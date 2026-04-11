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
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
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
                if (sessionCount > 0) "$sessionCount active session${if (sessionCount > 1) "s" else ""}"
                else "Connected",
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: replace with app icon
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
