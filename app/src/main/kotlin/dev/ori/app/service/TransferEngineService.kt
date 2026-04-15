package dev.ori.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.dao.TransferRecordDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 12 P12.5 — the real transfer engine, a foreground service that
 * owns the lifecycle of the P12.4 [TransferDispatcher] and the P12.3
 * resumable SSH/FTP clients.
 *
 * Responsibilities:
 *  - start the dispatcher on `onCreate` and promote itself to foreground
 *    with the `DATA_SYNC` type and the aggregate progress notification;
 *  - collect [TransferRecordDao.observeNonTerminalCount] to re-post the
 *    aggregate notification and `stopSelf()` when the queue drains;
 *  - handle explicit intents for `PAUSE_ALL`, `CANCEL_ALL`, and `RETRY`.
 *
 * Cancellation semantics:
 *  - `PAUSE_ALL` cancels every active worker; the worker's
 *    `CancellationException` handler flips each affected row to PAUSED.
 *  - `CANCEL_ALL` cancels every active worker and terminally marks every
 *    non-terminal row as [TransferStatus.CANCELLED] (Tier 2 T2b). The
 *    enum is stored as TEXT via Converters so no Room schema migration
 *    was required when the value was added.
 *  - `RETRY` bumps `nextRetryAt = now` on the given transfer id and calls
 *    [TransferDispatcher.tryDispatch] immediately.
 */
@AndroidEntryPoint
internal class TransferEngineService : Service() {

    @Inject lateinit var dispatcher: TransferDispatcher

    @Inject lateinit var dao: TransferRecordDao

    @Inject lateinit var transferNotificationManager: TransferNotificationManager

    @Inject lateinit var scope: CoroutineScope

    private var watcherJob: Job? = null
    private var dispatcherStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            TransferNotificationManager.NOTIFICATION_ID_SERVICE,
            transferNotificationManager.buildAggregateNotification(
                activeCount = 0,
                transferredBytes = 0L,
                totalBytes = 0L,
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (!dispatcherStarted) {
            dispatcher.start()
            dispatcherStarted = true
        }
        watcherJob?.cancel()
        watcherJob = scope.launch {
            dao.observeNonTerminalCount().distinctUntilChanged().collect { count ->
                if (count == 0) {
                    transferNotificationManager.cancelAggregate()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    refreshAggregateNotification(count)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> scope.launch { pauseAll() }
            ACTION_CANCEL_ALL -> scope.launch { cancelAll() }
            ACTION_RETRY -> {
                val id = intent.getLongExtra(EXTRA_TRANSFER_ID, -1L)
                if (id > 0) {
                    scope.launch { retryTransfer(id) }
                }
            }
            else -> Unit
        }
        return START_STICKY
    }

    override fun onDestroy() {
        watcherJob?.cancel()
        watcherJob = null
        super.onDestroy()
    }

    // ---- action handlers ----------------------------------------------------

    private suspend fun pauseAll() {
        val active = dao.getByStatuses(listOf(TransferStatus.ACTIVE, TransferStatus.QUEUED))
        for (entity in active) {
            dispatcher.cancelWorker(entity.id)
            if (entity.status == TransferStatus.QUEUED) {
                dao.updateStatus(entity.id, TransferStatus.PAUSED, null, null)
            }
        }
    }

    private suspend fun cancelAll() {
        val active = dao.getByStatuses(
            listOf(TransferStatus.ACTIVE, TransferStatus.QUEUED, TransferStatus.PAUSED),
        )
        val now = System.currentTimeMillis()
        for (entity in active) {
            dispatcher.cancelWorker(entity.id)
            dao.updateStatus(
                entity.id,
                TransferStatus.CANCELLED,
                null,
                now,
            )
        }
    }

    private suspend fun retryTransfer(id: Long) {
        dao.setNextRetryAt(id, System.currentTimeMillis())
        dao.updateStatus(id, TransferStatus.QUEUED, null, null)
        dispatcher.tryDispatch()
    }

    private suspend fun refreshAggregateNotification(count: Int) {
        val activeRows = dao.getByStatus(TransferStatus.ACTIVE)
        val transferred = activeRows.sumOf { it.transferredBytes }
        val total = activeRows.sumOf { it.totalBytes }
        val notification = transferNotificationManager.buildAggregateNotification(
            activeCount = count,
            transferredBytes = transferred,
            totalBytes = total,
        )
        transferNotificationManager.postAggregate(notification)
    }

    companion object {
        const val ACTION_PAUSE_ALL = "dev.ori.action.TRANSFER_PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "dev.ori.action.TRANSFER_CANCEL_ALL"
        const val ACTION_RETRY = "dev.ori.action.TRANSFER_RETRY"
        const val EXTRA_TRANSFER_ID = "transferId"
    }
}
