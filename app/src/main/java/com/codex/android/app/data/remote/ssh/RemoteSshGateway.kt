package com.codex.android.app.data.remote.ssh

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.sftp.SFTPClient
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
    ): BootstrapResult = withContext(Dispatchers.IO) {
        val keyPair = keyManager.generateKeyPair(comment = "codex-android@$username")
        val client = AndroidSshClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host, port)
        try {
            client.authPassword(username, password)
            val homeDir = executeCommand(client, "printf '%s' \"\$HOME\"").trim().ifBlank { null }
            val escapedKey = shellEscape(keyPair.publicOpenSsh)
            executeCommand(
                client,
                "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && " +
                    "grep -F -- $escapedKey ~/.ssh/authorized_keys >/dev/null 2>&1 || printf '%s\\n' $escapedKey >> ~/.ssh/authorized_keys",
            )
            BootstrapResult(
                homeDirectory = homeDir,
                keyPair = keyPair,
                hostFingerprint = null,
            )
        } finally {
            client.disconnect()
        }
    }

    suspend fun openSession(
        host: String,
        port: Int,
        username: String,
        accountId: String,
    ): ManagedRemoteSession = withContext(Dispatchers.IO) {
        val privateKey = keyManager.loadPrivateKey(accountId)
            ?: error("Missing SSH key for account $accountId")
        val publicKey = keyManager.loadPublicKey(accountId)
        val client = AndroidSshClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host, port)
        try {
            val keyProvider = client.loadKeys(privateKey, publicKey, null)
            client.authPublickey(username, keyProvider)
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
            val cmd = session.exec("sh -lc ${shellEscape(command)}")
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

    private fun shellEscape(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}

data class BootstrapResult(
    val homeDirectory: String?,
    val keyPair: GeneratedSshKeyPair,
    val hostFingerprint: String?,
)

class ManagedRemoteSession(
    private val context: Context,
    private val client: AndroidSshClient,
    private val sftpClient: SFTPClient,
) : AutoCloseable {
    suspend fun ensureCodexAppServer(port: Int): Int = withContext(Dispatchers.IO) {
        val command = """
            PORT=$port
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
            if ! command -v codex >/dev/null 2>&1; then
              echo "__CODEX_MISSING__"
              exit 127
            fi
            if ! port_listening; then
              nohup sh -lc 'codex app-server --listen ws://127.0.0.1:'"${'$'}PORT"' > "'"${'$'}HOME"'/.codex-android-app-server-'"${'$'}PORT"'.log" 2>&1' >/dev/null 2>&1 &
              sleep 2
            fi
            if ! port_listening; then
              echo "__CODEX_APP_SERVER_FAILED__"
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
            val cmd = session.exec("sh -lc ${shellEscape(command)}")
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

    fun appServerPortFor(username: String): Int {
        val hash = username.fold(0) { acc, char -> acc * 31 + char.code }
        return 47000 + kotlin.math.abs(hash % 1000)
    }

    override fun close() {
        runCatching { sftpClient.close() }
        runCatching { client.disconnect() }
    }

    private fun shellEscape(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun normalizeGitRemoteUrl(url: String): String {
        return when {
            url.startsWith("git@github.com:") -> "https://github.com/" + url.removePrefix("git@github.com:").removeSuffix(".git")
            else -> url.removeSuffix(".git")
        }
    }
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

private class AndroidSshClient : SSHClient(DefaultConfig()) {
    fun setKeepAliveIntervalSeconds(seconds: Int) {
        conn.keepAlive.keepAliveInterval = seconds
    }
}
