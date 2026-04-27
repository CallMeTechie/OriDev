package dev.ori.domain.usecase

import dev.ori.domain.repository.TransferRepository
import javax.inject.Inject

/**
 * Bug N — purges every terminal transfer (COMPLETED, FAILED, CANCELLED)
 * from persistent storage. The previous incarnation of this use case only
 * cleared `COMPLETED` rows, which meant a long tail of failed/cancelled
 * retries piled up with no UI affordance to remove them.
 */
class ClearFinishedTransfersUseCase @Inject constructor(
    private val repository: TransferRepository,
) {
    /**
     * @return number of finished transfers that were removed. The transfer
     *         queue UI uses this to show a confirmation Snackbar so the
     *         "Clear" action has visible feedback even when the list is
     *         already filtered to a non-terminal view.
     */
    suspend operator fun invoke(): Int = repository.clearFinished()
}
