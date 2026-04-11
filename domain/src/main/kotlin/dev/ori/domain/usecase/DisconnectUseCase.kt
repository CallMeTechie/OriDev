package dev.ori.domain.usecase

import dev.ori.domain.repository.ConnectionRepository
import javax.inject.Inject

class DisconnectUseCase @Inject constructor(
    private val repository: ConnectionRepository,
) {
    suspend operator fun invoke(profileId: Long) {
        repository.disconnect(profileId)
    }
}
