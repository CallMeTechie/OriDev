package dev.ori.domain.usecase

import dev.ori.core.common.result.AppResult
import dev.ori.domain.repository.ProxmoxRepository
import javax.inject.Inject

class AddProxmoxNodeUseCase @Inject constructor(
    private val repository: ProxmoxRepository,
) {
    suspend operator fun invoke(
        name: String,
        host: String,
        port: Int,
        tokenId: String,
        tokenSecret: String,
        certFingerprint: String,
    ): AppResult<Long> = repository.addNode(
        name = name,
        host = host,
        port = port,
        tokenId = tokenId,
        tokenSecret = tokenSecret,
        certFingerprint = certFingerprint,
    )
}
