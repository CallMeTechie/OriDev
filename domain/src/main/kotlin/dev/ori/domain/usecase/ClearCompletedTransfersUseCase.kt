package dev.ori.domain.usecase

import dev.ori.domain.repository.TransferRepository
import javax.inject.Inject

class ClearCompletedTransfersUseCase @Inject constructor(
    private val repository: TransferRepository,
) {
    /**
     * @return number of completed transfers that were removed. The transfer
     *         queue UI uses this to show a confirmation Snackbar so the
     *         "Clear" action has visible feedback even when the list is
     *         already filtered to a non-COMPLETED view.
     */
    suspend operator fun invoke(): Int = repository.clearCompleted()
}
