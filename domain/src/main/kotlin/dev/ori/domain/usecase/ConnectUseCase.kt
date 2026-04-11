package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppErrorException
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.model.Connection
import dev.ori.domain.repository.ConnectionRepository
import javax.inject.Inject

class ConnectUseCase @Inject constructor(
    private val repository: ConnectionRepository,
) {
    suspend operator fun invoke(profileId: Long): AppResult<Connection> {
        return try {
            val connection = repository.connect(profileId)
            appSuccess(connection)
        } catch (e: AppErrorException) {
            when (e.error) {
                is AppError.HostKeyUnknown,
                is AppError.HostKeyMismatch,
                -> throw e

                is AppError.AuthenticationError -> appFailure(e.error)

                else -> appFailure(e.error)
            }
        } catch (e: Exception) {
            val message = e.message ?: "Connection failed"
            when {
                isAuthError(e) -> appFailure(AppError.AuthenticationError(message, e))
                else -> appFailure(AppError.NetworkError(message, e))
            }
        }
    }

    private fun isAuthError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: return false
        return "auth" in message || "password" in message || "credential" in message
    }
}
