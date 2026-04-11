package dev.ori.domain.usecase

import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.TransferRepository
import javax.inject.Inject

class EnqueueTransferUseCase @Inject constructor(
    private val repository: TransferRepository,
) {
    suspend operator fun invoke(transfer: TransferRequest): Long {
        return repository.enqueue(transfer)
    }
}
