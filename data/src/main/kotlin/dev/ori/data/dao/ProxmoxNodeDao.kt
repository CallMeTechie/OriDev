package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.ProxmoxNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxmoxNodeDao {
    @Query("SELECT * FROM proxmox_nodes ORDER BY name")
    fun getAll(): Flow<List<ProxmoxNodeEntity>>

    @Query("SELECT * FROM proxmox_nodes WHERE id = :id")
    suspend fun getById(id: Long): ProxmoxNodeEntity?

    @Insert
    suspend fun insert(node: ProxmoxNodeEntity): Long

    @Update
    suspend fun update(node: ProxmoxNodeEntity)

    @Delete
    suspend fun delete(node: ProxmoxNodeEntity)
}
