package dev.ori.domain.repository

import dev.ori.domain.model.ConflictRequest
import dev.ori.domain.model.ConflictResolution
import kotlinx.coroutines.flow.SharedFlow

/**
 * Phase 12 P12.2 — rendezvous between a transfer worker coroutine that has
 * detected an overwrite conflict and the UI that must ask the user what to
 * do. The worker calls [emitConflict] and then suspends on [awaitResolution]
 * until the ViewModel forwards the user's choice via [resolve].
 */
interface TransferConflictRepository {
    val conflictRequests: SharedFlow<ConflictRequest>

    fun emitConflict(request: ConflictRequest)

    suspend fun awaitResolution(conflictId: String): ConflictResolution

    fun resolve(conflictId: String, resolution: ConflictResolution)
}
