package com.codex.android.app.data.github

import com.codex.android.app.BuildConfig
import com.codex.android.app.core.model.GitHubRepo
import com.codex.android.app.core.model.GitHubSession
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubAuthService(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun beginDeviceFlow(): DeviceFlowSession = withContext(Dispatchers.IO) {
        val clientId = requireClientId()
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .post(
                FormBody.Builder()
                    .add("client_id", clientId)
                    .add("scope", "repo read:user")
                    .build(),
            )
            .header("Accept", "application/json")
            .header("User-Agent", "codex-android-client")
            .build()
        val payload = executeJson(request)
        DeviceFlowSession(
            deviceCode = payload["device_code"]?.jsonPrimitive?.content.orEmpty(),
            userCode = payload["user_code"]?.jsonPrimitive?.content.orEmpty(),
            verificationUri = payload["verification_uri"]?.jsonPrimitive?.content.orEmpty(),
            expiresInSeconds = payload["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 900L,
            intervalSeconds = payload["interval"]?.jsonPrimitive?.content?.toLongOrNull() ?: 5L,
        )
    }

    suspend fun awaitAccessToken(deviceCode: String, intervalSeconds: Long): String = withContext(Dispatchers.IO) {
        val clientId = requireClientId()
        while (true) {
            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .post(
                    FormBody.Builder()
                        .add("client_id", clientId)
                        .add("device_code", deviceCode)
                        .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                        .build(),
                )
                .header("Accept", "application/json")
                .header("User-Agent", "codex-android-client")
                .build()
            val payload = executeJson(request)
            val accessToken = payload["access_token"]?.jsonPrimitive?.contentOrNull
            if (accessToken != null) return@withContext accessToken
            val error = payload["error"]?.jsonPrimitive?.contentOrNull
            when (error) {
                "authorization_pending" -> delay(intervalSeconds * 1000)
                "slow_down" -> delay((intervalSeconds + 5) * 1000)
                else -> throw IOException("GitHub device flow failed: ${error ?: "unknown_error"}")
            }
        }
        error("Unreachable")
    }

    suspend fun fetchSession(accessToken: String): GitHubSession = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", "codex-android-client")
            .build()
        val payload = executeJson(request)
        GitHubSession(
            accessToken = accessToken,
            userLogin = payload["login"]?.jsonPrimitive?.content.orEmpty(),
            userName = payload["name"]?.jsonPrimitive?.contentOrNull,
            avatarUrl = payload["avatar_url"]?.jsonPrimitive?.contentOrNull,
        )
    }

    suspend fun fetchRepositories(accessToken: String): List<GitHubRepo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/user/repos?per_page=100&sort=updated")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", "codex-android-client")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub repo request failed with ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val array = json.parseToJsonElement(body).jsonArray
            array.map { element ->
                val obj = element.jsonObject
                GitHubRepo(
                    id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                    fullName = obj["full_name"]?.jsonPrimitive?.content.orEmpty(),
                    htmlUrl = obj["html_url"]?.jsonPrimitive?.content.orEmpty(),
                    sshUrl = obj["ssh_url"]?.jsonPrimitive?.content.orEmpty(),
                    defaultBranch = obj["default_branch"]?.jsonPrimitive?.contentOrNull,
                    privateRepo = obj["private"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }
        }
    }

    private fun executeJson(request: Request) = httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("GitHub request failed with ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        json.parseToJsonElement(body).jsonObject
    }

    private fun requireClientId(): String {
        return BuildConfig.GITHUB_DEVICE_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing GITHUB_DEVICE_CLIENT_ID build config")
    }
}

data class DeviceFlowSession(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)
