package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.CommandSnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandSnippetDao {
    @Query(
        "SELECT * FROM command_snippets WHERE serverProfileId = :serverId " +
            "OR serverProfileId IS NULL ORDER BY sortOrder",
    )
    fun getForServer(serverId: Long?): Flow<List<CommandSnippetEntity>>

    @Query("SELECT * FROM command_snippets WHERE isWatchQuickCommand = 1 ORDER BY sortOrder")
    fun getWatchCommands(): Flow<List<CommandSnippetEntity>>

    @Insert
    suspend fun insert(snippet: CommandSnippetEntity): Long

    @Update
    suspend fun update(snippet: CommandSnippetEntity)

    @Delete
    suspend fun delete(snippet: CommandSnippetEntity)
}
