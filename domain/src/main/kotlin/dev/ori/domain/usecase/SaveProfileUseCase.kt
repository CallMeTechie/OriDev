package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.extension.isValidHost
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import javax.inject.Inject

class SaveProfileUseCase @Inject constructor(
    private val repository: ConnectionRepository,
) {
    suspend operator fun invoke(profile: ServerProfile): AppResult<Long> {
        if (profile.name.isBlank()) {
            return appFailure(AppError.StorageError("Profile name cannot be blank"))
        }

        if (!profile.host.isValidHost()) {
            return appFailure(AppError.StorageError("Invalid host: ${profile.host}"))
        }

        if (profile.port !in 1..65535) {
            return appFailure(AppError.StorageError("Invalid port: ${profile.port}. Must be between 1 and 65535"))
        }

        return try {
            if (profile.id == 0L) {
                val id = repository.saveProfile(profile)
                appSuccess(id)
            } else {
                repository.updateProfile(profile)
                appSuccess(profile.id)
            }
        } catch (e: Exception) {
            appFailure(AppError.StorageError("Failed to save profile: ${e.message}", e))
        }
    }
}
