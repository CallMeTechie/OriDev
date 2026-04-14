package dev.ori.data.conflict

import dev.ori.domain.model.ConflictRequest
import dev.ori.domain.model.ConflictResolution
import dev.ori.domain.repository.TransferConflictRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.4 — in-process implementation of
 * [TransferConflictRepository]. A [MutableSharedFlow] fans conflict
 * requests out to whichever ViewModel / UseCase is observing, and a
 * [ConcurrentHashMap] of [CompletableDeferred] provides the
 * request-id -> resolution rendezvous the worker awaits.
 *
 * Buffer strategy:
 *  - `replay = 0` so rejoining collectors don't see stale prompts
 *  - `extraBufferCapacity = 16` so bursts of N queued transfers hitting
 *    the "ask" policy at once don't lose emissions via `tryEmit`
 */
@Singleton
class TransferConflictRepositoryImpl @Inject constructor() : TransferConflictRepository {

    private val _conflictRequests = MutableSharedFlow<ConflictRequest>(
        replay = 0,
        extraBufferCapacity = EXTRA_BUFFER_CAPACITY,
    )

    override val conflictRequests: SharedFlow<ConflictRequest> = _conflictRequests.asSharedFlow()

    private val pending = ConcurrentHashMap<String, CompletableDeferred<ConflictResolution>>()

    override fun emitConflict(request: ConflictRequest) {
        pending[request.id] = CompletableDeferred()
        _conflictRequests.tryEmit(request)
    }

    override suspend fun awaitResolution(conflictId: String): ConflictResolution {
        // If no deferred is pending (e.g. conflict never emitted, or already
        // resolved and cleaned up), treat that as SKIP — the worker falls
        // back to the safe path.
        val deferred = pending[conflictId] ?: return ConflictResolution.SKIP
        return try {
            deferred.await()
        } finally {
            pending.remove(conflictId)
        }
    }

    override fun resolve(conflictId: String, resolution: ConflictResolution) {
        pending[conflictId]?.complete(resolution)
    }

    private companion object {
        const val EXTRA_BUFFER_CAPACITY = 16
    }
}
