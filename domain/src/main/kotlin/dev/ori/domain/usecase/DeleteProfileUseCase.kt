package dev.ori.domain.usecase

import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import javax.inject.Inject

class DeleteProfileUseCase @Inject constructor(
    private val repository: ConnectionRepository
) {
    suspend operator fun invoke(profile: ServerProfile) {
        try {
            repository.disconnect(profile.id)
        } catch (_: Exception) {
            // Ignore disconnect errors -- profile may not be connected
        }
        repository.deleteProfile(profile)
    }
}
