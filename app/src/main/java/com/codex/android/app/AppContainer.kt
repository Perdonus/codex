package com.codex.android.app

import android.content.Context
import com.codex.android.app.data.github.GitHubAuthService
import com.codex.android.app.data.local.LocalStateRepository
import com.codex.android.app.data.remote.codex.CodexAppServerClient
import com.codex.android.app.data.remote.ssh.RemoteSshGateway
import com.codex.android.app.data.remote.ssh.SshKeyManager
import java.time.Duration
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val sharedOkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .pingInterval(Duration.ofSeconds(10))
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(30))
        .build()

    val localStateRepository by lazy { LocalStateRepository(appContext) }
    val sshKeyManager by lazy { SshKeyManager(appContext) }
    val remoteSshGateway by lazy { RemoteSshGateway(appContext, sshKeyManager) }
    val gitHubAuthService by lazy { GitHubAuthService(sharedOkHttpClient) }

    fun newCodexClient(): CodexAppServerClient = CodexAppServerClient(sharedOkHttpClient)
}
