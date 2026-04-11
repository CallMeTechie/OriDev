package dev.ori.domain.repository

import dev.ori.domain.model.CommandSnippet
import kotlinx.coroutines.flow.Flow

interface SnippetRepository {
    fun getSnippetsForServer(serverId: Long?): Flow<List<CommandSnippet>>
    suspend fun addSnippet(snippet: CommandSnippet): Long
    suspend fun updateSnippet(snippet: CommandSnippet)
    suspend fun deleteSnippet(snippet: CommandSnippet)
}
