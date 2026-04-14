package dev.ori.app.service

import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.domain.model.ConflictRequest
import dev.ori.domain.model.ConflictResolution
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.TransferConflictRepository
import dev.ori.domain.repository.TransferRepository
import dev.ori.feature.settings.data.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Phase 12 P12.4 — per-transfer coroutine that owns one row's lifecycle
 * end-to-end: overwrite policy, resumed I/O, progress throttling, retry
 * scheduling and structured cancellation into PAUSED.
 *
 * Not a Hilt-injectable class itself — instances are built by
 * [TransferWorkerCoroutineFactory] with a specific `transferId` so the
 * [TransferDispatcher] can dispatch many rows off the same graph.
 */
internal class TransferWorkerCoroutine(
    private val transferId: Long,
    private val repository: TransferRepository,
    private val executor: TransferExecutor,
    private val conflictRepo: TransferConflictRepository,
    private val prefs: AppPreferences,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val progressThrottleMs: Long = PROGRESS_THROTTLE_MS,
) {

    /**
     * Executes the transfer in the caller's coroutine context. Never throws
     * across the boundary: all failure paths terminate with a DAO status
     * update (COMPLETED / FAILED / PAUSED / QUEUED-for-retry).
     */
    suspend fun execute() {
        val transfer = repository.getTransferById(transferId) ?: return

        val overwriteMode = prefs.overwriteMode.first()
        val resolved = resolveOverwrite(transfer, overwriteMode) ?: return

        repository.updateStatus(
            id = transferId,
            status = TransferStatus.ACTIVE,
            error = null,
            completedAt = null,
        )

        try {
            runTransfer(resolved)
            repository.updateStatus(
                id = transferId,
                status = TransferStatus.COMPLETED,
                error = null,
                completedAt = nowProvider(),
            )
        } catch (ce: CancellationException) {
            // Structured cancellation path — flip the row to PAUSED in a
            // NonCancellable block so the DAO write survives the coroutine
            // teardown, then rethrow to propagate the cancellation.
            withContext(NonCancellable) {
                repository.updateStatus(
                    id = transferId,
                    status = TransferStatus.PAUSED,
                    error = null,
                    completedAt = null,
                )
            }
            @Suppress("RethrowCaughtException")
            throw ce
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            handleFailure(transfer, t)
        }
    }

    // ---- overwrite policy --------------------------------------------------

    /**
     * Returns a (possibly rewritten) [TransferRequest] with the effective
     * destination path, or `null` if the transfer should be treated as a
     * no-op (SKIP / CANCEL).
     */
    private suspend fun resolveOverwrite(
        transfer: TransferRequest,
        overwriteMode: String,
    ): TransferRequest? {
        val existingSize = destinationSize(transfer) ?: return transfer
        return when (overwriteMode) {
            "overwrite" -> transfer.copy(transferredBytes = 0)
            "skip" -> {
                completeAsSkipped()
                null
            }
            "rename" -> transfer.copy(
                destinationPath = renameWithSuffix(transfer.destinationPath),
                transferredBytes = 0,
            )
            else -> handleAsk(transfer, existingSize)
        }
    }

    private suspend fun handleAsk(
        transfer: TransferRequest,
        existingSize: Long,
    ): TransferRequest? {
        val conflictId = UUID.randomUUID().toString()
        conflictRepo.emitConflict(
            ConflictRequest(
                id = conflictId,
                transferId = transferId,
                conflictedPath = transfer.destinationPath,
                existingSize = existingSize,
                existingLastModified = nowProvider(),
            ),
        )
        val resolution = try {
            withTimeout(CONFLICT_TIMEOUT_MS) {
                conflictRepo.awaitResolution(conflictId)
            }
        } catch (_: TimeoutCancellationException) {
            // Q4 decision: default to SKIP when UI is not listening.
            ConflictResolution.SKIP
        }
        return when (resolution) {
            ConflictResolution.OVERWRITE -> transfer.copy(transferredBytes = 0)
            ConflictResolution.RENAME -> transfer.copy(
                destinationPath = renameWithSuffix(transfer.destinationPath),
                transferredBytes = 0,
            )
            ConflictResolution.SKIP -> {
                completeAsSkipped()
                null
            }
            ConflictResolution.CANCEL -> {
                repository.updateStatus(
                    id = transferId,
                    status = TransferStatus.FAILED,
                    error = "Cancelled by user at conflict prompt",
                    completedAt = nowProvider(),
                )
                null
            }
        }
    }

    private suspend fun completeAsSkipped() {
        repository.updateProgress(transferId, 0L, 0L)
        repository.updateStatus(
            id = transferId,
            status = TransferStatus.COMPLETED,
            error = null,
            completedAt = nowProvider(),
        )
    }

    /**
     * For uploads, queries the remote executor. For downloads, checks the
     * local filesystem. Returns `null` when the destination does not yet
     * exist (no conflict).
     */
    private suspend fun destinationSize(transfer: TransferRequest): Long? =
        when (transfer.direction) {
            TransferDirection.UPLOAD ->
                executor.remoteFileSize(
                    sessionId = transfer.serverProfileId.toString(),
                    remotePath = transfer.destinationPath,
                )
            TransferDirection.DOWNLOAD -> {
                val localFile = File(transfer.destinationPath)
                if (localFile.exists()) localFile.length() else null
            }
        }

    private fun renameWithSuffix(path: String): String {
        val dotIdx = path.lastIndexOf('.')
        val slashIdx = path.lastIndexOf('/')
        val suffix = "-${nowProvider()}"
        return if (dotIdx > slashIdx && dotIdx >= 0) {
            path.substring(0, dotIdx) + suffix + path.substring(dotIdx)
        } else {
            path + suffix
        }
    }

    // ---- transfer body -----------------------------------------------------

    private suspend fun runTransfer(transfer: TransferRequest) {
        val sessionId = transfer.serverProfileId.toString()
        val offset = transfer.transferredBytes
        var lastWriteAtMs = 0L
        val onProgress: suspend (Long, Long) -> Unit = { sent, total ->
            val now = nowProvider()
            if (now - lastWriteAtMs >= progressThrottleMs) {
                lastWriteAtMs = now
                repository.updateProgress(transferId, sent, total)
            }
        }
        when (transfer.direction) {
            TransferDirection.UPLOAD -> executor.upload(
                sessionId = sessionId,
                localPath = transfer.sourcePath,
                remotePath = transfer.destinationPath,
                offsetBytes = offset,
                onProgress = onProgress,
            )
            TransferDirection.DOWNLOAD -> executor.download(
                sessionId = sessionId,
                remotePath = transfer.sourcePath,
                localPath = transfer.destinationPath,
                offsetBytes = offset,
                onProgress = onProgress,
            )
        }
        // Final flush — make sure totals in Room match what the executor
        // reported on its last progress tick (the 500 ms throttle may have
        // swallowed the last one).
        repository.updateProgress(transferId, transfer.totalBytes, transfer.totalBytes)
    }

    // ---- retry / failure ---------------------------------------------------

    private suspend fun handleFailure(
        transfer: TransferRequest,
        error: Throwable,
    ) {
        val autoResume = prefs.autoResume.first()
        val maxAttempts = prefs.maxRetryAttempts.first()
        val baseSeconds = prefs.retryBackoffSeconds.first()
        if (autoResume && transfer.retryCount < maxAttempts) {
            val now = nowProvider()
            val nextRetryAt = RetryScheduler.computeNextRetryAt(
                retryCount = transfer.retryCount,
                baseSeconds = baseSeconds,
                nowMillis = now,
            )
            // scheduleRetry bumps retryCount and flips status back to QUEUED
            // atomically, so the dispatcher will pick it up again once
            // nextRetryAt elapses.
            repository.scheduleRetry(transferId, nextRetryAt)
        } else {
            repository.updateStatus(
                id = transferId,
                status = TransferStatus.FAILED,
                error = error.message ?: error.javaClass.simpleName,
                completedAt = nowProvider(),
            )
        }
    }

    companion object {
        /** Q4 — how long the worker will wait for UI to resolve a conflict. */
        internal const val CONFLICT_TIMEOUT_MS: Long = 60_000L

        /** Minimum interval between `repository.updateProgress` writes. */
        internal const val PROGRESS_THROTTLE_MS: Long = 500L
    }
}

/**
 * Hilt-injectable factory that closes over the fixed-graph dependencies and
 * stamps each [TransferWorkerCoroutine] with a specific `transferId`.
 */
internal class TransferWorkerCoroutineFactory @Inject constructor(
    private val repository: TransferRepository,
    private val executor: TransferExecutor,
    private val conflictRepo: TransferConflictRepository,
    private val prefs: AppPreferences,
) {
    fun create(transferId: Long): TransferWorkerCoroutine =
        TransferWorkerCoroutine(
            transferId = transferId,
            repository = repository,
            executor = executor,
            conflictRepo = conflictRepo,
            prefs = prefs,
        )
}
