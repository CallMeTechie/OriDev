package dev.ori.domain.usecase

import dev.ori.domain.repository.TransferEngineController
import javax.inject.Inject

/**
 * Phase 12 P12.2 — pauses every in-flight transfer on the engine service.
 * Used by the transfer queue toolbar "Pause all" action and the persistent
 * notification's `ACTION_PAUSE_ALL`.
 */
class PauseAllTransfersUseCase @Inject constructor(
    private val controller: TransferEngineController,
) {
    suspend operator fun invoke() = controller.pauseAll()
}
