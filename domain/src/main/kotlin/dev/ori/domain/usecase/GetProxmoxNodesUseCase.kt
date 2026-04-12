package dev.ori.domain.usecase

import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.repository.ProxmoxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetProxmoxNodesUseCase @Inject constructor(
    private val repository: ProxmoxRepository,
) {
    operator fun invoke(): Flow<List<ProxmoxNode>> = repository.getNodes()
}
