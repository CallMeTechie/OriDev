package dev.ori.domain.repository

/**
 * Phase 12 P12.2 — abstraction over the foreground `TransferEngineService`
 * so that the UI / ViewModel layer can drive start, pause, resume and cancel
 * operations without depending on `:app` or any Android APIs.
 *
 * Implemented in `:data` by a thin wrapper that sends `Intent`s to the
 * service (see `TransferEngineServiceControllerImpl` in P12.5).
 */
interface TransferEngineController {
    fun ensureRunning()
    suspend fun pauseAll()
    suspend fun cancelAll()
    suspend fun pauseTransfer(id: Long)
    suspend fun resumeTransfer(id: Long)
    suspend fun cancelTransfer(id: Long)
}
