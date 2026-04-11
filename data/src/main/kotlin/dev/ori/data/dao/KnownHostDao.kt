package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.ori.data.entity.KnownHostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun find(host: String, port: Int): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(knownHost: KnownHostEntity)

    @Delete
    suspend fun delete(knownHost: KnownHostEntity)

    @Query("SELECT * FROM known_hosts ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<KnownHostEntity>>
}
