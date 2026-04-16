package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.ori.data.entity.TransferChunkEntity

@Dao
interface TransferChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chunk: TransferChunkEntity): Long

    @Query("SELECT * FROM transfer_chunks WHERE transferId = :transferId ORDER BY chunkIndex ASC")
    suspend fun getByTransferId(transferId: Long): List<TransferChunkEntity>

    @Query("UPDATE transfer_chunks SET status = :status, lastError = :error, attempts = attempts + 1 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String?)

    @Query("DELETE FROM transfer_chunks WHERE transferId = :transferId")
    suspend fun deleteByTransferId(transferId: Long)

    @Query("SELECT COUNT(*) FROM transfer_chunks WHERE transferId = :transferId AND status != 'COMPLETED'")
    suspend fun countIncomplete(transferId: Long): Int
}
