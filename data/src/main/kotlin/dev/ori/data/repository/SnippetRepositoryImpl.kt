package dev.ori.data.repository

import dev.ori.data.dao.CommandSnippetDao
import dev.ori.data.entity.CommandSnippetEntity
import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.repository.SnippetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepositoryImpl @Inject constructor(
    private val dao: CommandSnippetDao,
) : SnippetRepository {

    override fun getSnippetsForServer(serverId: Long?): Flow<List<CommandSnippet>> =
        dao.getForServer(serverId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun addSnippet(snippet: CommandSnippet): Long =
        dao.insert(snippet.toEntity())

    override suspend fun updateSnippet(snippet: CommandSnippet) =
        dao.update(snippet.toEntity())

    override suspend fun deleteSnippet(snippet: CommandSnippet) =
        dao.delete(snippet.toEntity())

    private fun CommandSnippetEntity.toDomain() = CommandSnippet(
        id = id,
        serverProfileId = serverProfileId,
        name = name,
        command = command,
        category = category,
        isWatchQuickCommand = isWatchQuickCommand,
        sortOrder = sortOrder,
    )

    private fun CommandSnippet.toEntity() = CommandSnippetEntity(
        id = id,
        serverProfileId = serverProfileId,
        name = name,
        command = command,
        category = category,
        isWatchQuickCommand = isWatchQuickCommand,
        sortOrder = sortOrder,
    )
}
