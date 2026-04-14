package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferRecordDao {
    @Query("SELECT * FROM transfer_records ORDER BY startedAt DESC")
    fun getAll(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_records WHERE status IN ('QUEUED', 'ACTIVE', 'PAUSED')")
    fun getActive(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_records WHERE id = :id")
    suspend fun getById(id: Long): TransferRecordEntity?

    @Insert
    suspend fun insert(record: TransferRecordEntity): Long

    @Update
    suspend fun update(record: TransferRecordEntity)

    @Query("DELETE FROM transfer_records WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query(
        """
        SELECT * FROM transfer_records
        WHERE status = 'QUEUED'
        AND (nextRetryAt IS NULL OR nextRetryAt <= :now)
        ORDER BY queuedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getReadyQueued(now: Long, limit: Int): List<TransferRecordEntity>

    @Query(
        "UPDATE transfer_records SET transferredBytes = :transferred, totalBytes = :total WHERE id = :id",
    )
    suspend fun updateProgress(id: Long, transferred: Long, total: Long)

    @Query(
        "UPDATE transfer_records SET status = :status, errorMessage = :error, " +
            "completedAt = :completedAt WHERE id = :id",
    )
    suspend fun updateStatus(id: Long, status: TransferStatus, error: String?, completedAt: Long?)

    @Query(
        "UPDATE transfer_records SET nextRetryAt = :nextRetryAt, status = 'QUEUED', " +
            "retryCount = retryCount + 1 WHERE id = :id",
    )
    suspend fun scheduleRetry(id: Long, nextRetryAt: Long)

    @Query("UPDATE transfer_records SET nextRetryAt = :nextRetryAt WHERE id = :id")
    suspend fun setNextRetryAt(id: Long, nextRetryAt: Long)

    @Query("SELECT COUNT(*) FROM transfer_records WHERE status IN ('QUEUED','ACTIVE','PAUSED')")
    fun observeNonTerminalCount(): Flow<Int>

    @Query("SELECT * FROM transfer_records WHERE status = :status")
    suspend fun getByStatus(status: TransferStatus): List<TransferRecordEntity>

    @Query("SELECT * FROM transfer_records WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<TransferStatus>): List<TransferRecordEntity>
}
