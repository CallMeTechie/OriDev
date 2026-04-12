package dev.ori.domain.usecase

import dev.ori.core.common.result.AppResult
import dev.ori.domain.repository.ProxmoxRepository
import javax.inject.Inject

class PollVmSshUseCase @Inject constructor(
    private val repository: ProxmoxRepository,
) {
    suspend operator fun invoke(
        nodeId: Long,
        vmid: Int,
        timeoutSeconds: Long = DEFAULT_TIMEOUT,
    ): AppResult<String> =
        repository.pollVmSshReady(nodeId, vmid, timeoutSeconds)

    companion object {
        const val DEFAULT_TIMEOUT = 60L
    }
}
