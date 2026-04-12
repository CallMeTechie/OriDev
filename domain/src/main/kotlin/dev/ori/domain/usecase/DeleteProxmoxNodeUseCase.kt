package dev.ori.domain.usecase

import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.repository.ProxmoxRepository
import javax.inject.Inject

class DeleteProxmoxNodeUseCase @Inject constructor(
    private val repository: ProxmoxRepository,
) {
    suspend operator fun invoke(node: ProxmoxNode) {
        repository.deleteNode(node)
    }
}
