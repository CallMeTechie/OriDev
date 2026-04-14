package dev.ori.domain.usecase

import dev.ori.domain.model.ConflictResolution
import dev.ori.domain.repository.TransferConflictRepository
import javax.inject.Inject

/**
 * Phase 12 P12.2 — forwards a user's decision from the conflict resolution
 * dialog back to the suspended worker coroutine that emitted the request.
 */
class ResolveConflictUseCase @Inject constructor(
    private val repo: TransferConflictRepository,
) {
    operator fun invoke(conflictId: String, resolution: ConflictResolution) {
        repo.resolve(conflictId, resolution)
    }
}
