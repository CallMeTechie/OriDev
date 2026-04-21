package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.KnownHostRepository
import javax.inject.Inject

/**
 * Persist a newly accepted (or updated) SSH host key after the user has
 * confirmed the fingerprint in the TOFU dialog. Writing the entry makes
 * the next [ConnectUseCase.invoke] pass verifier checks for that host/port
 * without re-prompting.
 */
class TrustHostUseCase @Inject constructor(
    private val knownHostRepository: KnownHostRepository,
) {
    suspend operator fun invoke(
        host: String,
        port: Int,
        keyType: String,
        fingerprint: String,
    ): AppResult<Unit> = try {
        knownHostRepository.trustHost(host, port, keyType, fingerprint)
        appSuccess(Unit)
    } catch (e: Exception) {
        appFailure(AppError.StorageError(e.message ?: "Failed to trust host", e))
    }
}
