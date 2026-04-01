package com.codex.android.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codex.android.app.core.util.AppDiagnostics

class SessionKeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        acquireLocks()
        AppDiagnostics.log("KeepAlive service создан")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Codex подключён" }
        val message = intent?.getStringExtra(EXTRA_MESSAGE).orEmpty().ifBlank { "Держу SSH и Codex-сессию активной" }
        startForeground(NOTIFICATION_ID, buildNotification(title, message))
        AppDiagnostics.log("KeepAlive service обновлён: $message")
        return START_STICKY
    }

    override fun onDestroy() {
        AppDiagnostics.log("KeepAlive service остановлен")
        releaseLocks()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AppDiagnostics.log("KeepAlive service task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(title: String, message: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(message)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Codex Keep Alive",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Удерживает SSH и Codex-сессию живой в фоне"
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:codex-keepalive").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$packageName:codex-keepalive")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        AppDiagnostics.log("KeepAlive locks захвачены")
    }

    private fun releaseLocks() {
        runCatching { wifiLock?.release() }
        runCatching { wakeLock?.release() }
        wifiLock = null
        wakeLock = null
        AppDiagnostics.log("KeepAlive locks освобождены")
    }

    companion object {
        private const val CHANNEL_ID = "codex_keep_alive"
        private const val NOTIFICATION_ID = 42013
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"

        fun sync(context: Context, active: Boolean, title: String, message: String) {
            val intent = Intent(context, SessionKeepAliveService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
            if (active) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
