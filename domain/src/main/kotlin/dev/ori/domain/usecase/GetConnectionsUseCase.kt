package dev.ori.domain.usecase

import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConnectionsUseCase @Inject constructor(
    private val repository: ConnectionRepository,
) {
    operator fun invoke(): Flow<List<ServerProfile>> = repository.getAllProfiles()
}
