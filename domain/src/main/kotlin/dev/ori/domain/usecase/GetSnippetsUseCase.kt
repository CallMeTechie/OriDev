package dev.ori.domain.usecase

import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.repository.SnippetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSnippetsUseCase @Inject constructor(
    private val repository: SnippetRepository,
) {
    operator fun invoke(serverId: Long?): Flow<List<CommandSnippet>> =
        repository.getSnippetsForServer(serverId)
}
