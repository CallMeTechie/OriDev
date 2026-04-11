package dev.ori.domain.usecase

import dev.ori.domain.repository.TransferRepository
import javax.inject.Inject

class ClearCompletedTransfersUseCase @Inject constructor(
    private val repository: TransferRepository,
) {
    suspend operator fun invoke() {
        repository.clearCompleted()
    }
}
