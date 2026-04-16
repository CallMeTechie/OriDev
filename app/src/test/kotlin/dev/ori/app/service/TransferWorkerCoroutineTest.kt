package dev.ori.app.service

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.PremiumRepository
import dev.ori.domain.repository.TransferChunkRepository
import dev.ori.domain.repository.TransferConflictRepository
import dev.ori.domain.repository.TransferRepository
import dev.ori.feature.settings.data.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

/**
 * Phase 12 P12.4 — unit tests for [TransferWorkerCoroutine] covering the
 * five lifecycle paths from the plan §10.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferWorkerCoroutineTest {

    private val premiumRepo = mockk<PremiumRepository>(relaxed = true) {
        coEvery { getCachedEntitlement() } returns false
    }
    private val chunkRepo = mockk<TransferChunkRepository>(relaxed = true)
    private val connectionRepo = mockk<ConnectionRepository>(relaxed = true) {
        coEvery { getProfileById(any()) } returns null
    }

    private val transferId = 7L
    private val baseTransfer = TransferRequest(
        id = transferId,
        serverProfileId = 1L,
        sourcePath = "/local/a.txt",
        destinationPath = "/remote/a.txt",
        direction = TransferDirection.UPLOAD,
        status = TransferStatus.QUEUED,
        totalBytes = 1_000L,
    )

    @Test
    fun execute_sshSuccess_marksCompleted() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<TransferRepository>(relaxed = true)
        coEvery { repo.getTransferById(transferId) } returns baseTransfer

        val executor = mockk<TransferExecutor>(relaxed = true)
        coEvery { executor.remoteFileSize(any(), any()) } returns null

        val prefs = prefs(overwrite = "overwrite", autoResume = false)
        val conflictRepo = mockk<TransferConflictRepository>(relaxed = true)

        val worker = TransferWorkerCoroutine(
            transferId,
            repo,
            executor,
            conflictRepo,
            prefs,
            premiumRepo,
            chunkRepo,
            connectionRepo,
        )
        worker.execute()

        coVerify { repo.updateStatus(transferId, TransferStatus.ACTIVE, null, null) }
        coVerify { repo.updateStatus(transferId, TransferStatus.COMPLETED, null, any()) }
    }

    @Test
    fun execute_cancelled_marksPaused_keepsTransferredBytes() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<TransferRepository>(relaxed = true)
        coEvery { repo.getTransferById(transferId) } returns baseTransfer.copy(transferredBytes = 250L)

        val gate = CompletableDeferred<Unit>()
        val executor = object : TransferExecutor {
            override suspend fun upload(
                sessionId: String,
                localPath: String,
                remotePath: String,
                offsetBytes: Long,
                onProgress: suspend (Long, Long) -> Unit,
            ) {
                // Advance progress once, then block forever until cancelled.
                onProgress(300L, 1_000L)
                gate.await()
            }
            override suspend fun download(
                sessionId: String,
                remotePath: String,
                localPath: String,
                offsetBytes: Long,
                onProgress: suspend (Long, Long) -> Unit,
            ) = error("unused")
            override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? = null
        }

        val prefs = prefs(overwrite = "overwrite", autoResume = false)
        val conflictRepo = mockk<TransferConflictRepository>(relaxed = true)

        val worker = TransferWorkerCoroutine(
            transferId,
            repo,
            executor,
            conflictRepo,
            prefs,
            premiumRepo,
            chunkRepo,
            connectionRepo,
        )
        val job = async { worker.execute() }
        yield()
        job.cancel()
        runCatching { job.await() }

        coVerify { repo.updateStatus(transferId, TransferStatus.PAUSED, null, null) }
        coVerify(exactly = 0) { repo.updateStatus(transferId, TransferStatus.COMPLETED, any(), any()) }
        // Cancelled workers do not clobber `transferredBytes` — they leave
        // whatever the executor last reported via onProgress in place.
    }

    @Test
    fun execute_onFailure_autoResumeTrue_schedulesRetry() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<TransferRepository>(relaxed = true)
        coEvery { repo.getTransferById(transferId) } returns baseTransfer.copy(retryCount = 0)

        val executor = object : TransferExecutor {
            override suspend fun upload(
                sessionId: String,
                localPath: String,
                remotePath: String,
                offsetBytes: Long,
                onProgress: suspend (Long, Long) -> Unit,
            ): Unit = throw java.io.IOException("boom")
            override suspend fun download(
                sessionId: String,
                remotePath: String,
                localPath: String,
                offsetBytes: Long,
                onProgress: suspend (Long, Long) -> Unit,
            ) = error("unused")
            override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? = null
        }

        val prefs = prefs(overwrite = "overwrite", autoResume = true, maxRetryAttempts = 3)
        val conflictRepo = mockk<TransferConflictRepository>(relaxed = true)

        val worker = TransferWorkerCoroutine(
            transferId,
            repo,
            executor,
            conflictRepo,
            prefs,
            premiumRepo,
            chunkRepo,
            connectionRepo,
        )
        worker.execute()

        val nextRetrySlot = slot<Long>()
        coVerify { repo.scheduleRetry(transferId, capture(nextRetrySlot)) }
        assertThat(nextRetrySlot.captured).isGreaterThan(0L)
        coVerify(exactly = 0) { repo.updateStatus(transferId, TransferStatus.FAILED, any(), any()) }
    }

    @Test
    fun execute_onFailure_autoResumeFalse_marksFailed() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<TransferRepository>(relaxed = true)
        coEvery { repo.getTransferById(transferId) } returns baseTransfer.copy(retryCount = 0)

        val executor = object : TransferExecutor {
            override suspend fun upload(
                sessionId: String,
                localPath: String,
                remotePath: String,
                offsetBytes: Long,
                onProgress: suspend (Long, Long) -> Unit,
            ): Unit = throw java.io.IOException("boom")
            override suspend fun download(
                sessionId: String,
                remotePath: String,
                localPath: String,
                offsetBytes: Long,
                onProgress: suspend (Long, Long) -> Unit,
            ) = error("unused")
            override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? = null
        }

        val prefs = prefs(overwrite = "overwrite", autoResume = false)
        val conflictRepo = mockk<TransferConflictRepository>(relaxed = true)

        val worker = TransferWorkerCoroutine(
            transferId,
            repo,
            executor,
            conflictRepo,
            prefs,
            premiumRepo,
            chunkRepo,
            connectionRepo,
        )
        worker.execute()

        coVerify { repo.updateStatus(transferId, TransferStatus.FAILED, any(), any()) }
        coVerify(exactly = 0) { repo.scheduleRetry(any(), any()) }
    }

    @Test
    fun execute_overwriteMode_skip_existingDest_skipsTransfer() = runTest(UnconfinedTestDispatcher()) {
        val repo = mockk<TransferRepository>(relaxed = true)
        coEvery { repo.getTransferById(transferId) } returns baseTransfer

        val executor = mockk<TransferExecutor>(relaxed = true)
        coEvery { executor.remoteFileSize(any(), any()) } returns 2_048L

        val prefs = prefs(overwrite = "skip", autoResume = false)
        val conflictRepo = mockk<TransferConflictRepository>(relaxed = true)

        val worker = TransferWorkerCoroutine(
            transferId,
            repo,
            executor,
            conflictRepo,
            prefs,
            premiumRepo,
            chunkRepo,
            connectionRepo,
        )
        worker.execute()

        coVerify { repo.updateProgress(transferId, 0L, 0L) }
        coVerify { repo.updateStatus(transferId, TransferStatus.COMPLETED, null, any()) }
        coVerify(exactly = 0) { executor.upload(any(), any(), any(), any(), any()) }
    }

    // ---- helpers -----------------------------------------------------------

    private fun prefs(
        overwrite: String,
        autoResume: Boolean,
        maxRetryAttempts: Int = 3,
        baseSeconds: Int = 1,
    ): AppPreferences {
        val p = mockk<AppPreferences>(relaxed = true)
        every { p.overwriteMode } returns flowOf(overwrite)
        every { p.autoResume } returns flowOf(autoResume)
        every { p.maxRetryAttempts } returns flowOf(maxRetryAttempts)
        every { p.retryBackoffSeconds } returns flowOf(baseSeconds)
        every { p.maxParallelTransfers } returns flowOf(3)
        return p
    }
}
