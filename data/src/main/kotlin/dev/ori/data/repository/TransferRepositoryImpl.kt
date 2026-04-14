package dev.ori.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.mapper.toDomain
import dev.ori.data.mapper.toEntity
import dev.ori.data.worker.TransferWorker
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val dao: TransferRecordDao,
    @ApplicationContext private val context: Context,
) : TransferRepository {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun getAllTransfers(): Flow<List<TransferRequest>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getActiveTransfers(): Flow<List<TransferRequest>> =
        dao.getActive().map { entities -> entities.map { it.toDomain() } }

    override suspend fun enqueue(transfer: TransferRequest): Long {
        val entity = transfer.copy(status = TransferStatus.QUEUED).toEntity()
        val id = dao.insert(entity)
        scheduleWork(id, transfer.transferredBytes)
        return id
    }

    override suspend fun pause(transferId: Long) {
        workManager.cancelUniqueWork(workName(transferId))
        val record = dao.getById(transferId) ?: return
        dao.update(record.copy(status = TransferStatus.PAUSED))
    }

    override suspend fun resume(transferId: Long) {
        val record = dao.getById(transferId) ?: return
        dao.update(record.copy(status = TransferStatus.QUEUED))
        scheduleWork(transferId, record.transferredBytes)
    }

    override suspend fun cancel(transferId: Long) {
        workManager.cancelUniqueWork(workName(transferId))
        val record = dao.getById(transferId) ?: return
        dao.update(
            record.copy(
                status = TransferStatus.FAILED,
                errorMessage = "Cancelled by user",
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearCompleted() {
        dao.clearCompleted()
    }

    // Phase 12 P12.2 — minimal DAO delegations used by the new engine workers.
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

    override suspend fun getTransferById(id: Long): TransferRequest? =
        dao.getById(id)?.toDomain()

    private fun scheduleWork(transferId: Long, offsetBytes: Long) {
        val inputData = Data.Builder()
            .putLong(TransferWorker.KEY_TRANSFER_ID, transferId)
            .putLong(TransferWorker.KEY_OFFSET_BYTES, offsetBytes)
            .build()

        val request = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            workName(transferId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun workName(transferId: Long): String = "transfer_$transferId"
}
