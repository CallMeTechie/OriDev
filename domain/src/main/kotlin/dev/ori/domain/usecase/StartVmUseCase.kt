package dev.ori.domain.usecase

import dev.ori.core.common.result.AppResult
import dev.ori.domain.repository.ProxmoxRepository
import javax.inject.Inject

class StartVmUseCase @Inject constructor(
    private val repository: ProxmoxRepository,
) {
    suspend operator fun invoke(nodeId: Long, vmid: Int): AppResult<String> =
        repository.startVm(nodeId, vmid)
}
