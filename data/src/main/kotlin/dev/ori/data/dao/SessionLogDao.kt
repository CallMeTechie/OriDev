package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.SessionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs WHERE serverProfileId = :serverId ORDER BY startedAt DESC")
    fun getForServer(serverId: Long): Flow<List<SessionLogEntity>>

    @Insert
    suspend fun insert(log: SessionLogEntity): Long

    @Update
    suspend fun update(log: SessionLogEntity)
}
