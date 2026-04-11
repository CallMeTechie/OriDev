package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
}
