package com.codex.android.app.data.remote.ssh

import android.content.Context
import com.codex.android.app.core.model.CodexProfile
import com.hierynomus.sshj.key.KeyAlgorithm
import com.hierynomus.sshj.key.KeyAlgorithms
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.kex.Curve25519SHA256
import com.hierynomus.sshj.transport.kex.ExtInfoClientFactory
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

class RemoteSshGateway(
    private val context: Context,
    private val keyManager: SshKeyManager,
) {
    suspend fun bootstrapPasswordAuth(
        host: String,
        port: Int,
        username: String,
        password: String,
        publicOpenSsh: String,
    ): BootstrapResult = withContext(Dispatchers.IO) {
        val client = AndroidSshClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host, port)
        try {
            client.authPassword(username, password)
            val homeDir = executeCommand(client, "printf '%s' \"\$HOME\"").trim().ifBlank { null }
            val escapedKey = shellEscape(publicOpenSsh)
            executeCommand(
                client,
                "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && " +
                    "grep -F -- $escapedKey ~/.ssh/authorized_keys >/dev/null 2>&1 || printf '%s\\n' $escapedKey >> ~/.ssh/authorized_keys",
            )
            BootstrapResult(
                homeDirectory = homeDir,
                hostFingerprint = null,
            )
        } finally {
            client.disconnect()
        }
    }

    internal suspend fun openSession(
        host: String,
        port: Int,
        username: String,
        accountId: String,
        password: String? = null,
    ): ManagedRemoteSession = withContext(Dispatchers.IO) {
        val client = AndroidSshClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host, port)
        try {
            runCatching {
                val privateKey = keyManager.loadPrivateKey(accountId)
                    ?: error("Missing SSH key for account $accountId")
                val publicKey = keyManager.loadPublicKey(accountId)
                val keyProvider = client.loadKeys(privateKey, publicKey, null)
                client.authPublickey(username, keyProvider)
            }.getOrElse { authError ->
                if (password.isNullOrBlank()) throw authError
                client.authPassword(username, password)
            }
            client.setKeepAliveIntervalSeconds(15)
            ManagedRemoteSession(
                context = context,
                client = client,
                sftpClient = client.newSFTPClient(),
            )
        } catch (t: Throwable) {
            runCatching { client.disconnect() }
            throw t
        }
    }

    private fun executeCommand(client: SSHClient, command: String): String {
        client.startSession().use { session ->
            val cmd = session.exec(shellCommand(command))
            val stdout = cmd.inputStream.bufferedReader().readText()
            val stderr = cmd.errorStream.bufferedReader().readText()
            cmd.join()
            val exitCode = cmd.exitStatus ?: 0
            if (exitCode != 0) {
                throw IOException("Remote command failed ($exitCode): ${stderr.ifBlank { stdout }}")
            }
            return stdout
        }
    }

}

data class BootstrapResult(
    val homeDirectory: String?,
    val hostFingerprint: String?,
)

internal class ManagedRemoteSession(
    private val context: Context,
    private val client: AndroidSshClient,
    private val sftpClient: SFTPClient,
) : AutoCloseable {
    suspend fun ensureCodexAppServer(port: Int, forceRestart: Boolean = false): Int = withContext(Dispatchers.IO) {
        val command = """
            PORT=$port
            FORCE_RESTART=${if (forceRestart) 1 else 0}
            port_listening() {
              if command -v ss >/dev/null 2>&1; then
                ss -ltn 2>/dev/null | grep -q ":${'$'}PORT "
                return
              fi
              if command -v netstat >/dev/null 2>&1; then
                netstat -ltn 2>/dev/null | grep -q "[.:]${'$'}PORT[[:space:]]"
                return
              fi
              if command -v lsof >/dev/null 2>&1; then
                lsof -nP -iTCP:"${'$'}PORT" -sTCP:LISTEN >/dev/null 2>&1
                return
              fi
              return 1
            }
            if [ "${'$'}FORCE_RESTART" = "1" ]; then
              if command -v lsof >/dev/null 2>&1; then
                lsof -tiTCP:"${'$'}PORT" -sTCP:LISTEN 2>/dev/null | while read -r pid; do
                  kill "${'$'}pid" 2>/dev/null || true
                done
              fi
              pkill -f "codex --search --sandbox danger-full-access --dangerously-bypass-approvals-and-sandbox app-server --listen ws://127.0.0.1:${'$'}PORT" 2>/dev/null || true
              sleep 1
            fi
            if ! command -v codex >/dev/null 2>&1; then
              echo "__CODEX_MISSING__"
              exit 127
            fi
            if ! port_listening; then
              nohup bash -lc 'codex --search --sandbox danger-full-access --dangerously-bypass-approvals-and-sandbox app-server --listen ws://127.0.0.1:'"${'$'}PORT" > "${'$'}HOME/.codex-android-app-server-${'$'}PORT.log" 2>&1 &
              sleep 2
            fi
            if ! port_listening; then
              echo "__CODEX_APP_SERVER_FAILED__"
              [ -f "${'$'}HOME/.codex-android-app-server-${'$'}PORT.log" ] && tail -80 "${'$'}HOME/.codex-android-app-server-${'$'}PORT.log"
              exit 126
            fi
            printf '%s' "${'$'}PORT"
        """.trimIndent()
        when (val output = executeShell(command).trim()) {
            "__CODEX_MISSING__" -> throw IOException("codex is not installed on the remote server")
            "__CODEX_APP_SERVER_FAILED__" -> throw IOException("codex app-server did not start on the remote server")
            else -> output.toIntOrNull() ?: throw IOException("Unexpected app-server response: $output")
        }
    }

    suspend fun openLocalPortForward(remotePort: Int): PortForwardHandle = withContext(Dispatchers.IO) {
        val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val parameters = Parameters("127.0.0.1", serverSocket.localPort, "127.0.0.1", remotePort)
        val forwarder = client.newLocalPortForwarder(parameters, serverSocket)
        val thread = Thread(
            { runCatching { forwarder.listen() } },
            "codex-local-port-forward-$remotePort",
        ).apply {
            isDaemon = true
            start()
        }
        PortForwardHandle(
            localPort = serverSocket.localPort,
            closeAction = {
                runCatching { forwarder.close() }
                runCatching { serverSocket.close() }
                thread.interrupt()
            },
        )
    }

    suspend fun executeShell(command: String): String = withContext(Dispatchers.IO) {
        client.startSession().use { session ->
            val cmd = session.exec(shellCommand(command))
            val stdout = cmd.inputStream.bufferedReader().readText()
            val stderr = cmd.errorStream.bufferedReader().readText()
            cmd.join()
            val exitCode = cmd.exitStatus ?: 0
            if (exitCode != 0) {
                throw IOException("Remote shell failed ($exitCode): ${stderr.ifBlank { stdout }}")
            }
            stdout
        }
    }

    suspend fun listDirectory(path: String): List<RemoteFileEntry> = withContext(Dispatchers.IO) {
        sftpClient.ls(path)
            .filterNot { it.name == "." || it.name == ".." }
            .sortedWith(compareByDescending<net.schmizz.sshj.sftp.RemoteResourceInfo> { it.isDirectory() }.thenBy { it.name.lowercase() })
            .map {
                RemoteFileEntry(
                    path = it.path,
                    name = it.name,
                    isDirectory = it.isDirectory,
                    sizeBytes = it.attributes.size,
                    modifiedEpochSeconds = it.attributes.mtime.toLong(),
                )
            }
    }

    suspend fun readTextFile(path: String): String = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("codex-android-read-", ".tmp", context.cacheDir)
        try {
            sftpClient.get(path, temp.absolutePath)
            temp.readText()
        } finally {
            temp.delete()
        }
    }

    suspend fun writeTextFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("codex-android-write-", ".tmp", context.cacheDir)
        try {
            temp.writeText(content)
            sftpClient.put(temp.absolutePath, path)
        } finally {
            temp.delete()
        }
    }

    suspend fun downloadToLocal(path: String, targetFile: File): File = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        sftpClient.get(path, targetFile.absolutePath)
        targetFile
    }

    suspend fun uploadLocalFile(localFile: File, remotePath: String) = withContext(Dispatchers.IO) {
        sftpClient.put(localFile.absolutePath, remotePath)
    }

    suspend fun rename(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        sftpClient.rename(oldPath, newPath)
    }

    suspend fun delete(path: String, isDirectory: Boolean) = withContext(Dispatchers.IO) {
        if (isDirectory) sftpClient.rmdir(path) else sftpClient.rm(path)
    }

    suspend fun makeDirectory(path: String) = withContext(Dispatchers.IO) {
        sftpClient.mkdirs(path)
    }

    suspend fun detectHomeDirectory(): String = executeShell("printf '%s' \"\$HOME\"").trim()

    suspend fun listRemoteGitRepos(): List<DetectedGitRepo> = withContext(Dispatchers.IO) {
        val output = executeShell(
            """
            find "${'$'}HOME" -maxdepth 5 -type d -name .git 2>/dev/null | while read -r gitdir; do
              root="${'$'}{gitdir%/.git}"
              url="$(git -C "${'$'}root" config --get remote.origin.url 2>/dev/null || true)"
              if [ -n "${'$'}url" ]; then
                printf '%s\t%s\n' "${'$'}root" "${'$'}url"
              fi
            done
            """.trimIndent(),
        )
        output.lineSequence()
            .filter { it.isNotBlank() && it.contains('\t') }
            .map { line ->
                val parts = line.split('\t', limit = 2)
                val root = parts[0]
                val url = parts[1]
                DetectedGitRepo(
                    name = root.substringAfterLast('/'),
                    rootPath = root,
                    remoteUrl = normalizeGitRemoteUrl(url),
                )
            }
            .toList()
    }

    suspend fun listCodexProfiles(
        activeEmail: String? = null,
        activePlanType: String? = null,
    ): List<CodexProfile> = withContext(Dispatchers.IO) {
        val output = executeShell(
            """
            python3 - <<'PY'
            import base64, filecmp, json
            from pathlib import Path

            home = Path.home()
            profiles_dir = home / ".codex" / "profiles"
            profiles_dir.mkdir(parents=True, exist_ok=True)
            auth_path = home / ".codex" / "auth.json"
            active_seen = False

            def decode_json(text):
                try:
                    return json.loads(text)
                except Exception:
                    return {}

            def decode_jwt_payload(token):
                if not token:
                    return {}
                parts = token.split(".")
                if len(parts) < 2:
                    return {}
                payload = parts[1]
                payload += "=" * ((4 - len(payload) % 4) % 4)
                try:
                    return json.loads(base64.urlsafe_b64decode(payload.encode("utf-8")))
                except Exception:
                    return {}

            def walk(value):
                if isinstance(value, dict):
                    for item in value.values():
                        yield from walk(item)
                elif isinstance(value, list):
                    for item in value:
                        yield from walk(item)
                else:
                    yield value

            def guess_email(data, fallback):
                for value in walk(data):
                    if isinstance(value, str) and "@" in value and "." in value.split("@")[-1]:
                        return value
                return fallback if "@" in fallback else ""

            def guess_plan(data):
                for value in walk(data):
                    if isinstance(value, str):
                        lower = value.lower()
                        if any(token in lower for token in ("plus", "pro", "team", "enterprise", "free")):
                            return value
                return ""

            def profile_info(path, fallback_name):
                payload = decode_json(path.read_text()) if path.exists() else {}
                jwt_payload = decode_jwt_payload(payload.get("tokens", {}).get("id_token", ""))
                auth_meta = jwt_payload.get("https://api.openai.com/auth", {}) if isinstance(jwt_payload, dict) else {}
                email = ""
                if isinstance(jwt_payload, dict):
                    email = jwt_payload.get("email", "")
                if not email:
                    email = guess_email(payload, fallback_name)
                plan = ""
                if isinstance(auth_meta, dict):
                    plan = auth_meta.get("chatgpt_plan_type", "") or auth_meta.get("plan_type", "")
                if not plan:
                    plan = guess_plan(payload)
                return {
                    "email": email or "",
                    "plan": plan or "",
                    "sub": jwt_payload.get("sub", "") if isinstance(jwt_payload, dict) else "",
                    "account_id": auth_meta.get("chatgpt_account_id", "") if isinstance(auth_meta, dict) else "",
                }

            def same_identity(left, right):
                if not left or not right:
                    return False
                for key in ("account_id", "sub", "email"):
                    if left.get(key) and right.get(key) and left.get(key) == right.get(key):
                        return True
                return False

            auth_info = profile_info(auth_path, "current") if auth_path.exists() else None
            emitted_identity_keys = set()

            for profile in sorted(profiles_dir.glob("*.json")):
                name = profile.stem
                info = profile_info(profile, name)
                identity_key = info["account_id"] or info["sub"] or info["email"] or name
                if identity_key in emitted_identity_keys:
                    continue
                active = int(
                    auth_path.exists() and (
                        filecmp.cmp(profile, auth_path, shallow=False) or same_identity(info, auth_info)
                    )
                )
                active_seen = active_seen or bool(active)
                emitted_identity_keys.add(identity_key)
                print(f"{name}\t{active}\t{info['email']}\t{info['plan']}")

            if auth_path.exists() and not active_seen:
                info = auth_info or {"email": "", "plan": ""}
                print(f"current\t1\t{info['email']}\t{info['plan']}")
            PY
            """.trimIndent(),
        )
        output.lineSequence()
            .filter { it.isNotBlank() && it.contains('\t') }
            .map { line ->
                val parts = line.split('\t', limit = 4)
                val isActive = parts.getOrNull(1) == "1"
                CodexProfile(
                    name = parts[0],
                    isActive = isActive,
                    email = (if (isActive) activeEmail ?: parts.getOrNull(2) else parts.getOrNull(2)).takeUnless { it.isNullOrBlank() },
                    planType = (if (isActive) activePlanType ?: parts.getOrNull(3) else parts.getOrNull(3)).takeUnless { it.isNullOrBlank() },
                )
            }
            .sortedWith(compareByDescending<CodexProfile> { it.isActive }.thenBy { it.name.lowercase() })
            .toList()
    }

    suspend fun saveCurrentCodexProfile(preferredName: String): String = withContext(Dispatchers.IO) {
        val profileName = sanitizeProfileName(preferredName)
        val output = executeShell(
            """
            PREFERRED=${shellEscape(profileName)} python3 - <<'PY'
            import base64, filecmp, json, os, re, shutil
            from pathlib import Path

            home = Path.home()
            profiles_dir = home / ".codex" / "profiles"
            profiles_dir.mkdir(parents=True, exist_ok=True)
            auth_path = home / ".codex" / "auth.json"
            preferred = os.environ.get("PREFERRED", "profile")

            if not auth_path.exists():
                print("__NO_AUTH__", end="")
                raise SystemExit(0)

            def sanitize(raw):
                cleaned = re.sub(r"[^a-z0-9@._-]+", "-", raw.strip().lower()).strip("-")
                return cleaned or "profile"

            def decode_json(path):
                try:
                    return json.loads(path.read_text())
                except Exception:
                    return {}

            def decode_jwt_payload(token):
                if not token:
                    return {}
                parts = token.split(".")
                if len(parts) < 2:
                    return {}
                payload = parts[1]
                payload += "=" * ((4 - len(payload) % 4) % 4)
                try:
                    return json.loads(base64.urlsafe_b64decode(payload.encode("utf-8")))
                except Exception:
                    return {}

            def profile_info(path):
                payload = decode_json(path)
                jwt_payload = decode_jwt_payload(payload.get("tokens", {}).get("id_token", ""))
                auth_meta = jwt_payload.get("https://api.openai.com/auth", {}) if isinstance(jwt_payload, dict) else {}
                return {
                    "email": jwt_payload.get("email", "") if isinstance(jwt_payload, dict) else "",
                    "sub": jwt_payload.get("sub", "") if isinstance(jwt_payload, dict) else "",
                    "account_id": auth_meta.get("chatgpt_account_id", "") if isinstance(auth_meta, dict) else "",
                }

            def same_identity(left, right):
                for key in ("account_id", "sub", "email"):
                    if left.get(key) and right.get(key) and left.get(key) == right.get(key):
                        return True
                return False

            auth_info = profile_info(auth_path)

            for existing in sorted(profiles_dir.glob("*.json")):
                if filecmp.cmp(existing, auth_path, shallow=False) or same_identity(profile_info(existing), auth_info):
                    shutil.copy2(auth_path, existing)
                    print(existing.stem, end="")
                    raise SystemExit(0)

            base = sanitize(preferred)
            candidate = profiles_dir / f"{base}.json"
            index = 2
            while candidate.exists():
                candidate = profiles_dir / f"{base}-{index}.json"
                index += 1
            shutil.copy2(auth_path, candidate)
            print(candidate.stem, end="")
            PY
            """.trimIndent(),
        ).trim()
        if (output == "__NO_AUTH__") {
            throw IOException("codex auth.json is missing on the remote server")
        }
        output
    }

    suspend fun activateCodexProfile(profileName: String) = withContext(Dispatchers.IO) {
        val safeName = profileName.trim()
        val output = executeShell(
            """
            profile_name=${shellEscape("$safeName.json")}
            PROFILE="${'$'}HOME/.codex/profiles/${'$'}profile_name"
            AUTH="${'$'}HOME/.codex/auth.json"
            if [ ! -f "${'$'}PROFILE" ]; then
              printf '%s' "__PROFILE_MISSING__"
              exit 0
            fi
            cp "${'$'}PROFILE" "${'$'}AUTH"
            printf '%s' "__OK__"
            """.trimIndent(),
        ).trim()
        if (output == "__PROFILE_MISSING__") {
            throw IOException("Codex profile $safeName was not found on the remote server")
        }
    }

    suspend fun createConversationDirectory(basePath: String): String = withContext(Dispatchers.IO) {
        executeShell(
            """
            BASE=${shellEscape(basePath)}
            mkdir -p "${'$'}BASE"
            if command -v mktemp >/dev/null 2>&1; then
              DIR="$(mktemp -d "${'$'}BASE/codex-XXXXXX")"
            else
              DIR="${'$'}BASE/codex-$(date +%s)-${'$'}RANDOM"
              mkdir -p "${'$'}DIR"
            fi
            printf '%s' "${'$'}DIR"
            """.trimIndent(),
        ).trim()
    }

    fun appServerPortFor(username: String): Int {
        val hash = username.fold(0) { acc, char -> acc * 31 + char.code }
        return 47000 + kotlin.math.abs(hash % 1000)
    }

    override fun close() {
        runCatching { sftpClient.close() }
        runCatching { client.disconnect() }
    }

    private fun normalizeGitRemoteUrl(url: String): String {
        return when {
            url.startsWith("git@github.com:") -> "https://github.com/" + url.removePrefix("git@github.com:").removeSuffix(".git")
            else -> url.removeSuffix(".git")
        }
    }

    private fun sanitizeProfileName(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9@._-]+"), "-")
            .trim('-')
            .ifBlank { "profile" }
    }
}

private fun shellEscape(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

private fun shellCommand(command: String): String {
    val escaped = shellEscape(command)
    return "if command -v bash >/dev/null 2>&1; then bash -lc $escaped; else sh -lc $escaped; fi"
}

data class PortForwardHandle(
    val localPort: Int,
    val closeAction: () -> Unit,
) : AutoCloseable {
    override fun close() = closeAction()
}

data class DetectedGitRepo(
    val name: String,
    val remoteUrl: String,
    val rootPath: String,
)

data class RemoteFileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedEpochSeconds: Long?,
)

internal class AndroidSshClient : SSHClient(androidCompatibleConfig()) {
    fun setKeepAliveIntervalSeconds(seconds: Int) {
        conn.keepAlive.keepAliveInterval = seconds
    }
}

private fun androidCompatibleConfig(): DefaultConfig {
    return object : DefaultConfig() {
        override fun initKeyExchangeFactories() {
            // Android devices often route generic EC generation into AndroidKeyStore.
            // Restricting sshj to curve25519 avoids that path and still matches this server.
            setKeyExchangeFactories(
                Curve25519SHA256.Factory(),
                Curve25519SHA256.FactoryLibSsh(),
                ExtInfoClientFactory(),
            )
        }

        override fun initKeyAlgorithms() {
            // Prefer RSA host keys to avoid EC/Ed25519 provider edge cases on Android.
            setKeyAlgorithms(
                listOf<Factory.Named<KeyAlgorithm>>(
                    KeyAlgorithms.RSASHA512(),
                    KeyAlgorithms.RSASHA256(),
                    KeyAlgorithms.SSHRSA(),
                ),
            )
        }
    }
}
