package dev.ori.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.app.R
import dev.ori.feature.settings.data.AppPreferences
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.5 — chrome for the transfer engine's notifications.
 *
 * Owns:
 *  - the `oridev_transfers` notification channel (idempotent init);
 *  - the aggregate ongoing notification displayed while
 *    [TransferEngineService] is in the foreground state
 *    (id = [NOTIFICATION_ID_SERVICE]);
 *  - per-file completion notifications (base id [NOTIFICATION_ID_DONE_BASE])
 *    gated on the user's `notifyTransferDone` preference;
 *  - per-file failure notifications (base id [NOTIFICATION_ID_FAIL_BASE])
 *    which are not gated on user prefs — failures are always surfaced.
 *
 * The aggregate notification carries `PAUSE_ALL` and `CANCEL_ALL` action
 * buttons that dispatch explicit intents back to [TransferEngineService];
 * the failure notification carries a `RETRY` action for the specific
 * transfer that failed.
 */
@Singleton
internal class TransferNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) {
    val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing file transfers and completion notifications"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ---- aggregate ongoing notification ------------------------------------

    /**
     * Builds the ongoing notification shown while the service is in the
     * foreground. When [totalBytes] is positive a determinate progress bar
     * is shown, otherwise an indeterminate one.
     */
    fun buildAggregateNotification(
        activeCount: Int,
        transferredBytes: Long,
        totalBytes: Long,
    ): Notification {
        val contentText = TransferNotificationText.aggregateTitle(activeCount)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Ori:Dev transfers")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_transfer_active)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                0,
                "Pause all",
                servicePendingIntent(TransferEngineService.ACTION_PAUSE_ALL),
            )
            .addAction(
                0,
                "Cancel all",
                servicePendingIntent(TransferEngineService.ACTION_CANCEL_ALL),
            )

        if (totalBytes > 0L) {
            val percent = TransferNotificationText.progressPercent(transferredBytes, totalBytes)
            builder.setProgress(PROGRESS_MAX, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    fun postAggregate(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID_SERVICE, notification)
    }

    fun cancelAggregate() {
        notificationManager.cancel(NOTIFICATION_ID_SERVICE)
    }

    // ---- per-file notifications --------------------------------------------

    /**
     * Posts a per-file completion notification if the user has opted in via
     * the `notifyTransferDone` preference. Auto-cancels on tap.
     */
    suspend fun postCompletionNotification(
        transferId: Long,
        fileName: String,
        sizeBytes: Long,
        isUpload: Boolean,
    ) {
        if (!prefs.notifyTransferDone.first()) return
        val icon = if (isUpload) {
            R.drawable.ic_transfer_upload
        } else {
            R.drawable.ic_transfer_download
        }
        val verb = if (isUpload) "Uploaded" else "Downloaded"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("$verb $fileName")
            .setContentText(TransferNotificationText.humanReadableBytes(sizeBytes))
            .setSmallIcon(icon)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(
            NOTIFICATION_ID_DONE_BASE + transferId.toInt(),
            notification,
        )
    }

    /**
     * Posts a sticky failure notification with a Retry action that sends
     * [TransferEngineService.ACTION_RETRY] with the failed transfer's id.
     */
    fun postFailureNotification(
        transferId: Long,
        fileName: String,
        error: String,
    ) {
        val retryIntent = Intent(context, TransferEngineService::class.java).apply {
            action = TransferEngineService.ACTION_RETRY
            putExtra(TransferEngineService.EXTRA_TRANSFER_ID, transferId)
        }
        val retryPending = PendingIntent.getService(
            context,
            (RETRY_REQUEST_CODE_BASE + transferId).toInt(),
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Transfer failed: $fileName")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setSmallIcon(R.drawable.ic_transfer_active)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Retry", retryPending)
            .build()
        notificationManager.notify(
            NOTIFICATION_ID_FAIL_BASE + transferId.toInt(),
            notification,
        )
    }

    // ---- helpers ------------------------------------------------------------

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(context, TransferEngineService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "oridev_transfers"
        const val NOTIFICATION_ID_SERVICE = 2001
        const val NOTIFICATION_ID_DONE_BASE = 3000
        const val NOTIFICATION_ID_FAIL_BASE = 4000
        private const val PROGRESS_MAX = 100
        private const val RETRY_REQUEST_CODE_BASE = 10_000L
    }
}

/**
 * Pure-Kotlin text formatting helpers extracted from [TransferNotificationManager]
 * so they can be unit tested on the JVM without Robolectric.
 *
 * - [humanReadableBytes] — binary units ("512 B", "2.0 KiB", "5.0 MiB", "2.0 GiB").
 * - [aggregateTitle] — content text for the foreground notification, e.g.
 *   "Preparing transfers", "1 active transfer", "3 active transfers".
 * - [aggregateBody] — "500 KiB / 1.0 MiB · 50%" style line; falls back to an
 *   em-dash placeholder when the total is unknown (<= 0).
 * - [progressPercent] — `(transferred / total * 100).toInt()` clamped to 0..100,
 *   returning `0` for a zero/negative total.
 */
internal object TransferNotificationText {

    private const val ONE_KIB = 1024L
    private const val ONE_MIB = 1024L * 1024L
    private const val ONE_GIB = 1024L * 1024L * 1024L
    private const val PROGRESS_MAX = 100
    private const val INDETERMINATE_BODY = "—"

    fun humanReadableBytes(bytes: Long): String = when {
        bytes >= ONE_GIB -> String.format(Locale.US, "%.1f GiB", bytes / ONE_GIB.toDouble())
        bytes >= ONE_MIB -> String.format(Locale.US, "%.1f MiB", bytes / ONE_MIB.toDouble())
        bytes >= ONE_KIB -> String.format(Locale.US, "%.1f KiB", bytes / ONE_KIB.toDouble())
        else -> "$bytes B"
    }

    fun aggregateTitle(activeCount: Int): String = when {
        activeCount <= 0 -> "Preparing transfers"
        activeCount == 1 -> "1 active transfer"
        else -> "$activeCount active transfers"
    }

    fun aggregateBody(transferred: Long, total: Long): String {
        if (total <= 0L) return INDETERMINATE_BODY
        val percent = progressPercent(transferred, total)
        return "${humanReadableBytes(transferred)} / ${humanReadableBytes(total)} · $percent%"
    }

    fun progressPercent(transferred: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((transferred * PROGRESS_MAX) / total)
            .toInt()
            .coerceIn(0, PROGRESS_MAX)
    }
}
