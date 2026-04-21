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
        } catch (e: Exception) {
            // SSHJ wraps our verifier exception inside its own
            // TransportException → SSHException chain, so `catch
            // (AppErrorException)` alone never fired and the
            // HostKey branches were dead code. Walk the cause chain
            // looking for our marker exception before falling
            // through to the generic auth/network mapping.
            val appError = findAppError(e)
            if (appError != null) {
                appFailure(appError)
            } else {
                val message = e.message ?: "Connection failed"
                if (isAuthError(e)) {
                    appFailure(AppError.AuthenticationError(message, e))
                } else {
                    appFailure(AppError.NetworkError(message, e))
                }
            }
        }
    }

    private fun findAppError(e: Throwable): AppError? {
        var current: Throwable? = e
        while (current != null) {
            if (current is AppErrorException) return current.error
            current = current.cause
        }
        return null
    }

    private fun isAuthError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: return false
        return "auth" in message || "password" in message || "credential" in message
    }
}
