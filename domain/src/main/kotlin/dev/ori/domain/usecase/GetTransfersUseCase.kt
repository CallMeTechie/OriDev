package dev.ori.domain.usecase

import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTransfersUseCase @Inject constructor(
    private val repository: TransferRepository,
) {
    operator fun invoke(): Flow<List<TransferRequest>> {
        return repository.getAllTransfers()
    }

    fun active(): Flow<List<TransferRequest>> {
        return repository.getActiveTransfers()
    }
}
