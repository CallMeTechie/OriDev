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
    suspend fun clearCompleted()

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
