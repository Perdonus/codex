package com.codex.android.app

import android.app.Application
import com.codex.android.app.core.util.SecurityProviderCompat

class CodexMobileApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        SecurityProviderCompat.configureAndroidSshProviders()
        container = AppContainer(this)
    }
}
