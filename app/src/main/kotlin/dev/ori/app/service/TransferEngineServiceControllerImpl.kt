package dev.ori.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.dao.TransferRecordDao
import dev.ori.domain.repository.TransferEngineController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.5 — `:app`-side implementation of [TransferEngineController].
 *
 * Lives in `:app` rather than `:data` because it needs a direct class
 * reference to [TransferEngineService] to start / signal it via `Intent`,
 * and `:data` is not allowed to depend on the app module.
 *
 * All methods are cheap Intent builders; the heavy lifting (pausing
 * workers, updating rows) happens inside the service's coroutine scope.
 */
@Singleton
internal class TransferEngineServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TransferRecordDao,
    private val dispatcher: TransferDispatcher,
) : TransferEngineController {

    override fun ensureRunning() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, TransferEngineService::class.java),
        )
    }

    override suspend fun pauseAll() {
        context.startService(
            Intent(context, TransferEngineService::class.java).apply {
                action = TransferEngineService.ACTION_PAUSE_ALL
            },
        )
    }

    override suspend fun cancelAll() {
        context.startService(
            Intent(context, TransferEngineService::class.java).apply {
                action = TransferEngineService.ACTION_CANCEL_ALL
            },
        )
    }

    override suspend fun pauseTransfer(id: Long) {
        // The dispatcher is a @Singleton shared across service restarts, so
        // calling it directly is safe even if the service is not currently
        // running: cancelling a non-existent worker is a no-op.
        dispatcher.cancelWorker(id)
    }

    override suspend fun resumeTransfer(id: Long) {
        // Flip the row back to QUEUED (no-op if already QUEUED) and wake
        // the service so the dispatcher re-evaluates its ready set.
        dao.updateStatus(id, TransferStatus.QUEUED, null, null)
        ensureRunning()
    }

    override suspend fun cancelTransfer(id: Long) {
        dispatcher.cancelWorker(id)
        // Tier 2 T2b — dedicated CANCELLED terminal state replaces the
        // Phase 12 P12.5 FAILED+"Cancelled by user" workaround. Enum is
        // stored as TEXT via Converters so no Room schema change is
        // required.
        dao.updateStatus(
            id,
            TransferStatus.CANCELLED,
            null,
            System.currentTimeMillis(),
        )
    }
}
