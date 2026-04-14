package dev.ori.data.repository

import dev.ori.core.common.model.TransferStatus
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.mapper.toDomain
import dev.ori.data.mapper.toEntity
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.TransferEngineController
import dev.ori.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Phase 12 P12.5 — Room-backed implementation of [TransferRepository].
 *
 * Previously this class enqueued `OneTimeWorkRequest<TransferWorker>`
 * instances through WorkManager. The Phase 12 engine replaces that
 * machinery with a foreground service driven by [TransferEngineController]
 * (whose `:app`-side impl dispatches Intents to `TransferEngineService`).
 * Work lifetime is no longer WorkManager's concern: inserts simply create
 * a `QUEUED` row and call `ensureRunning()`; pause/resume/cancel delegate
 * to the controller and mutate the row directly.
 */
@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val dao: TransferRecordDao,
    // Lazy `Provider<T>` breaks the Hilt dependency cycle: the
    // `TransferEngineController` impl's transitive graph also needs a
    // `TransferRepository`, and the two would otherwise reference each
    // other synchronously. Deferring the controller lookup until the
    // first `enqueue`/`pause`/… call keeps the graph acyclic while
    // preserving singleton semantics.
    private val engineControllerProvider: Provider<TransferEngineController>,
) : TransferRepository {

    private val engineController: TransferEngineController
        get() = engineControllerProvider.get()

    override fun getAllTransfers(): Flow<List<TransferRequest>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getActiveTransfers(): Flow<List<TransferRequest>> =
        dao.getActive().map { entities -> entities.map { it.toDomain() } }

    override suspend fun enqueue(transfer: TransferRequest): Long {
        val entity = transfer.copy(status = TransferStatus.QUEUED).toEntity()
        val id = dao.insert(entity)
        engineController.ensureRunning()
        return id
    }

    override suspend fun pause(transferId: Long) {
        engineController.pauseTransfer(transferId)
        val record = dao.getById(transferId) ?: return
        // Only flip to PAUSED if the worker's own CancellationException
        // handler hasn't already done so (idempotent).
        if (record.status != TransferStatus.PAUSED) {
            dao.updateStatus(transferId, TransferStatus.PAUSED, null, null)
        }
    }

    override suspend fun resume(transferId: Long) {
        engineController.resumeTransfer(transferId)
    }

    override suspend fun cancel(transferId: Long) {
        engineController.cancelTransfer(transferId)
    }

    override suspend fun clearCompleted() {
        dao.clearCompleted()
    }

    override suspend fun updateProgress(id: Long, transferred: Long, total: Long) {
        dao.updateProgress(id, transferred, total)
    }

    override suspend fun updateStatus(
        id: Long,
        status: TransferStatus,
        error: String?,
        completedAt: Long?,
    ) {
        dao.updateStatus(id, status, error, completedAt)
    }

    override suspend fun setNextRetryAt(id: Long, nextRetryAt: Long) {
        dao.setNextRetryAt(id, nextRetryAt)
    }

    override suspend fun scheduleRetry(id: Long, nextRetryAt: Long) {
        dao.scheduleRetry(id, nextRetryAt)
    }

    override suspend fun getTransferById(id: Long): TransferRequest? =
        dao.getById(id)?.toDomain()
}
