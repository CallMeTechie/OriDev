package dev.ori.domain.usecase

import dev.ori.domain.repository.TransferEngineController
import javax.inject.Inject

/**
 * Phase 12 P12.2 — cancels every in-flight transfer on the engine service.
 * Used by the transfer queue toolbar "Cancel all" action and the persistent
 * notification's `ACTION_CANCEL_ALL`.
 */
class CancelAllTransfersUseCase @Inject constructor(
    private val controller: TransferEngineController,
) {
    suspend operator fun invoke() = controller.cancelAll()
}
