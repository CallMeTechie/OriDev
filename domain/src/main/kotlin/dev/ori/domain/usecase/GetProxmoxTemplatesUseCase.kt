package dev.ori.domain.usecase

import dev.ori.core.common.result.AppResult
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.repository.ProxmoxRepository
import javax.inject.Inject

class GetProxmoxTemplatesUseCase @Inject constructor(
    private val repository: ProxmoxRepository,
) {
    suspend operator fun invoke(nodeId: Long): AppResult<List<ProxmoxVm>> =
        repository.getTemplates(nodeId)
}
