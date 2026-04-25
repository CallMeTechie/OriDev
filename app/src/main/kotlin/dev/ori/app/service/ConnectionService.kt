package dev.ori.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.ori.app.R
import dev.ori.core.network.ssh.SshShellManager
import javax.inject.Inject

@AndroidEntryPoint
class ConnectionService : Service() {

    @Inject lateinit var shellManager: SshShellManager

    private val binder = LocalBinder()
    private var sessionCount = 0

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
        fun getShellManager(): SshShellManager = shellManager
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must stay a subset of the manifest's `android:foregroundServiceType`
        // (`specialUse`, declared with PROPERTY_SPECIAL_USE_FGS_SUBTYPE).
        // We migrated off `dataSync` because Android 14+ enforces a 6 h /
        // 24 h budget on it and crashes the next startForeground() call
        // once exhausted with `ForegroundServiceStartNotAllowedException:
        // Time limit already exhausted for foreground service type
        // dataSync` — reproduced on Pixel Fold (oridev-error-terminal-
        // open-shell-2026-04-25-* embedded 04-23 / 04-24 stacks). SSH
        // sessions are interactive and intentionally long-lived, so
        // dataSync is semantically wrong; specialUse is the documented
        // escape hatch for use cases not covered by the typed FGS list.
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        return START_STICKY
    }

    fun updateSessionCount(count: Int) {
        sessionCount = count
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
        if (count == 0) {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Connections",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows active SSH connections"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ori:Dev")
            .setContentText(
                if (sessionCount > 0) {
                    "$sessionCount active session${if (sessionCount > 1) "s" else ""}"
                } else {
                    "Connected"
                },
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        shellManager.closeAllShells()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "oridev_connections"
        const val NOTIFICATION_ID = 1001
    }
}
