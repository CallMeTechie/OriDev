package dev.ori.app.service

import dev.ori.data.dao.TransferRecordDao
import dev.ori.feature.settings.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.4 — reactive dispatcher that owns the Semaphore-gated
 * parallelism budget for the transfer engine. P12.5 wires this into the
 * `TransferEngineService` lifecycle.
 *
 * Observes two reactive sources:
 *  1. [AppPreferences.maxParallelTransfers] — rebuilds [semaphore] on
 *     change and calls [tryDispatch] to fill freshly unlocked slots.
 *  2. [TransferRecordDao.observeNonTerminalCount] — fires [tryDispatch]
 *     whenever new rows arrive or existing rows flip status, so the
 *     dispatcher reacts to both enqueue and retry wake-ups without a
 *     separate polling loop.
 *
 * Cancellation is tracked in [activeJobs]: [cancelWorker] cancels only
 * the targeted job, and each worker removes itself from the map in a
 * `finally` block so slots are always released.
 */
@Singleton
internal class TransferDispatcher @Inject constructor(
    private val dao: TransferRecordDao,
    private val prefs: AppPreferences,
    // TODO(P12.5): replace with `@ApplicationScope CoroutineScope` once the
    // Hilt qualifier + SupervisorJob binding lands with TransferEngineService.
    private val scope: CoroutineScope,
    private val workerFactory: TransferWorkerCoroutineFactory,
) {
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    @Volatile
    private var currentCap: Int = DEFAULT_CAP

    @Volatile
    private var semaphore: Semaphore = Semaphore(DEFAULT_CAP)

    /**
     * Starts the two watcher coroutines. Idempotent: calling more than
     * once just re-launches the collectors (P12.5 will guard this from
     * the service side).
     */
    fun start() {
        scope.launch {
            prefs.maxParallelTransfers.distinctUntilChanged().collect { cap ->
                val newCap = cap.coerceAtLeast(1)
                currentCap = newCap
                semaphore = Semaphore(newCap)
                tryDispatch()
            }
        }
        scope.launch {
            dao.observeNonTerminalCount().distinctUntilChanged().collect {
                tryDispatch()
            }
        }
    }

    /**
     * Pulls up to `availablePermits` ready rows from the DAO and launches a
     * [TransferWorkerCoroutine] for each. Rows already in [activeJobs] are
     * skipped (defensive against duplicate dispatch).
     */
    suspend fun tryDispatch() {
        val snapshotSemaphore = semaphore
        // Effective slots = cap minus already-running workers. Using the raw
        // `availablePermits` is not enough: when the cap shrinks we build a
        // fresh Semaphore that still has `newCap` permits even though the
        // previous-generation workers are still running. Guarding on the
        // ConcurrentHashMap size prevents over-dispatching across rebuilds.
        val slots = (currentCap - activeJobs.size).coerceAtLeast(0)
        if (slots <= 0) return
        val candidates = dao.getReadyQueued(System.currentTimeMillis(), slots)
        for (record in candidates) {
            if (activeJobs.containsKey(record.id)) continue
            if ((currentCap - activeJobs.size) <= 0) return
            if (!snapshotSemaphore.tryAcquire()) return
            val job = scope.launch {
                try {
                    workerFactory.create(record.id).execute()
                } finally {
                    activeJobs.remove(record.id)
                    // Release to whichever Semaphore was current at launch.
                    // If `semaphore` was rebuilt since then, releasing to the
                    // old one is a no-op from the new one's perspective —
                    // `currentCap - activeJobs.size` remains the authoritative
                    // slot-count check.
                    snapshotSemaphore.release()
                    tryDispatch()
                }
            }
            activeJobs[record.id] = job
        }
    }

    /**
     * Cancels a specific in-flight worker, leaving all other jobs running.
     * The worker's `CancellationException` handler flips the row to PAUSED.
     */
    fun cancelWorker(id: Long) {
        activeJobs[id]?.cancel()
    }

    /** Visible for testing — the set of transfer ids currently running. */
    internal fun activeIds(): Set<Long> = activeJobs.keys.toSet()

    companion object {
        const val DEFAULT_CAP: Int = 3
    }
}
