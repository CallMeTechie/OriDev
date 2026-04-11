package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.ServerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY sortOrder, name")
    fun getAll(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE isFavorite = 1 ORDER BY name")
    fun getFavorites(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE id = :id")
    suspend fun getById(id: Long): ServerProfileEntity?

    @Query("SELECT COUNT(*) FROM server_profiles")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(profile: ServerProfileEntity): Long

    @Update
    suspend fun update(profile: ServerProfileEntity)

    @Delete
    suspend fun delete(profile: ServerProfileEntity)

    @Query("UPDATE server_profiles SET lastConnected = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long = System.currentTimeMillis())
}
