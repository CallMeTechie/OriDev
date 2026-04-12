package dev.ori.domain.usecase

import dev.ori.core.common.result.AppResult
import dev.ori.domain.repository.ProxmoxRepository
import javax.inject.Inject

class CloneVmUseCase @Inject constructor(
    private val repository: ProxmoxRepository,
) {
    suspend operator fun invoke(
        nodeId: Long,
        templateVmid: Int,
        newVmid: Int,
        newName: String,
        fullClone: Boolean,
    ): AppResult<String> =
        repository.cloneVm(nodeId, templateVmid, newVmid, newName, fullClone)
}
