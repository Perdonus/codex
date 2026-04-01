package com.codex.android.app

import android.app.Application
import android.os.Bundle
import com.codex.android.app.core.util.AppDiagnostics
import com.codex.android.app.core.util.SecurityProviderCompat

class CodexMobileApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.log("Application onCreate")
        SecurityProviderCompat.configureAndroidSshProviders()
        container = AppContainer(this)
        registerActivityLifecycleCallbacks(AppLifecycleLogger())
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppDiagnostics.log("Application onLowMemory")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppDiagnostics.log("Application onTrimMemory level=$level")
    }

    private class AppLifecycleLogger : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {
            AppDiagnostics.log("${activity.localClassName} created")
        }

        override fun onActivityStarted(activity: android.app.Activity) {
            AppDiagnostics.log("${activity.localClassName} started")
        }

        override fun onActivityResumed(activity: android.app.Activity) {
            AppDiagnostics.log("${activity.localClassName} resumed")
        }

        override fun onActivityPaused(activity: android.app.Activity) {
            AppDiagnostics.log("${activity.localClassName} paused")
        }

        override fun onActivityStopped(activity: android.app.Activity) {
            AppDiagnostics.log("${activity.localClassName} stopped")
        }

        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: android.app.Activity) {
            AppDiagnostics.log("${activity.localClassName} destroyed")
        }
    }
}
