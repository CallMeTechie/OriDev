package dev.ori.domain.repository

import dev.ori.core.common.model.TransferStatus
import dev.ori.domain.model.TransferRequest
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    fun getAllTransfers(): Flow<List<TransferRequest>>
    fun getActiveTransfers(): Flow<List<TransferRequest>>
    suspend fun enqueue(transfer: TransferRequest): Long
    suspend fun pause(transferId: Long)
    suspend fun resume(transferId: Long)
    suspend fun cancel(transferId: Long)

    /**
     * Removes every transfer in a terminal state — `COMPLETED`, `FAILED`,
     * `CANCELLED` — from persistent storage. In-flight rows (`QUEUED`,
     * `ACTIVE`, `PAUSED`) are untouched.
     *
     * Bug N — historically this method only purged `COMPLETED` rows, so the
     * single "Clear" button in the queue toolbar ignored failed and cancelled
     * transfers and left the list cluttered. Widening the predicate is the
     * least disruptive fix because the UI never exposed separate clear
     * actions for failed/cancelled rows in the first place.
     *
     * @return number of rows that were deleted. Surfaced to the UI so the
     *         queue toolbar can confirm the action via a Snackbar (e.g.
     *         "3 finished transfers cleared").
     */
    suspend fun clearFinished(): Int

    // Phase 12 P12.2 — additions consumed by the TransferEngineService workers.
    suspend fun updateProgress(id: Long, transferred: Long, total: Long)

    suspend fun updateStatus(
        id: Long,
        status: TransferStatus,
        error: String? = null,
        completedAt: Long? = null,
    )

    suspend fun setNextRetryAt(id: Long, nextRetryAt: Long)

    /**
     * Phase 12 P12.4 — atomic retry scheduling. Increments `retryCount`,
     * flips the row back to `QUEUED`, and stamps `nextRetryAt`. Used by
     * the per-transfer worker coroutine after a recoverable failure.
     */
    suspend fun scheduleRetry(id: Long, nextRetryAt: Long)

    suspend fun getTransferById(id: Long): TransferRequest?
}
