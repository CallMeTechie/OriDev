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

    /**
     * Removes every transfer in a terminal state — `COMPLETED`, `FAILED`,
     * and `CANCELLED` — leaving in-flight rows (`QUEUED`, `ACTIVE`,
     * `PAUSED`) untouched.
     *
     * Bug N — the previous query only matched `COMPLETED`, which meant the
     * "Clear" button silently ignored failed and cancelled rows. Users with
     * a long history of retries-that-never-recovered (e.g. wrong host key
     * after server reinstall, expired credentials) had no way to prune the
     * queue without dropping the database, because the UI only exposes one
     * clear button.
     */
    @Query("DELETE FROM transfer_records WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinished(): Int

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
