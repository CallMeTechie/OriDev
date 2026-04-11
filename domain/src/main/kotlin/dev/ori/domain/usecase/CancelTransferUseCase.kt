package dev.ori.domain.usecase

import dev.ori.domain.repository.TransferRepository
import javax.inject.Inject

class CancelTransferUseCase @Inject constructor(
    private val repository: TransferRepository,
) {
    suspend operator fun invoke(transferId: Long) {
        repository.cancel(transferId)
    }
}
