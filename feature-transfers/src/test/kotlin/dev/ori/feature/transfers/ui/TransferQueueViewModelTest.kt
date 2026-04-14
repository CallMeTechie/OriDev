package dev.ori.feature.transfers.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.domain.model.ConflictRequest
import dev.ori.domain.model.ConflictResolution
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.TransferConflictRepository
import dev.ori.domain.usecase.CancelAllTransfersUseCase
import dev.ori.domain.usecase.CancelTransferUseCase
import dev.ori.domain.usecase.ClearCompletedTransfersUseCase
import dev.ori.domain.usecase.EnqueueTransferUseCase
import dev.ori.domain.usecase.GetTransfersUseCase
import dev.ori.domain.usecase.PauseAllTransfersUseCase
import dev.ori.domain.usecase.PauseTransferUseCase
import dev.ori.domain.usecase.ResolveConflictUseCase
import dev.ori.domain.usecase.ResumeTransferUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransferQueueViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getTransfersUseCase: GetTransfersUseCase
    private lateinit var pauseTransferUseCase: PauseTransferUseCase
    private lateinit var resumeTransferUseCase: ResumeTransferUseCase
    private lateinit var cancelTransferUseCase: CancelTransferUseCase
    private lateinit var enqueueTransferUseCase: EnqueueTransferUseCase
    private lateinit var clearCompletedTransfersUseCase: ClearCompletedTransfersUseCase
    private lateinit var conflictRepository: FakeTransferConflictRepository
    private lateinit var pauseAllTransfersUseCase: PauseAllTransfersUseCase
    private lateinit var cancelAllTransfersUseCase: CancelAllTransfersUseCase
    private lateinit var resolveConflictUseCase: ResolveConflictUseCase

    private val sampleTransfers = listOf(
        TransferRequest(
            id = 1L,
            serverProfileId = 1L,
            sourcePath = "/local/file1.txt",
            destinationPath = "/remote/file1.txt",
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.ACTIVE,
            totalBytes = 1000L,
            transferredBytes = 500L,
        ),
        TransferRequest(
            id = 2L,
            serverProfileId = 1L,
            sourcePath = "/remote/file2.txt",
            destinationPath = "/local/file2.txt",
            direction = TransferDirection.DOWNLOAD,
            status = TransferStatus.COMPLETED,
            totalBytes = 2000L,
            transferredBytes = 2000L,
        ),
        TransferRequest(
            id = 3L,
            serverProfileId = 1L,
            sourcePath = "/local/file3.txt",
            destinationPath = "/remote/file3.txt",
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.FAILED,
            errorMessage = "Connection lost",
        ),
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getTransfersUseCase = mockk()
        pauseTransferUseCase = mockk(relaxed = true)
        resumeTransferUseCase = mockk(relaxed = true)
        cancelTransferUseCase = mockk(relaxed = true)
        enqueueTransferUseCase = mockk(relaxed = true)
        clearCompletedTransfersUseCase = mockk(relaxed = true)
        conflictRepository = FakeTransferConflictRepository()
        pauseAllTransfersUseCase = mockk(relaxed = true)
        cancelAllTransfersUseCase = mockk(relaxed = true)
        resolveConflictUseCase = mockk(relaxed = true)

        every { getTransfersUseCase() } returns flowOf(sampleTransfers)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = TransferQueueViewModel(
        getTransfersUseCase = getTransfersUseCase,
        pauseTransferUseCase = pauseTransferUseCase,
        resumeTransferUseCase = resumeTransferUseCase,
        cancelTransferUseCase = cancelTransferUseCase,
        enqueueTransferUseCase = enqueueTransferUseCase,
        clearCompletedTransfersUseCase = clearCompletedTransfersUseCase,
        conflictRepository = conflictRepository,
        pauseAllTransfersUseCase = pauseAllTransfersUseCase,
        cancelAllTransfersUseCase = cancelAllTransfersUseCase,
        resolveConflictUseCase = resolveConflictUseCase,
    )

    @Test
    fun `init loads transfers from use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.transfers).hasSize(3)
            assertThat(state.isLoading).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilter to ACTIVE shows only active transfers`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.SetFilter(TransferFilter.ACTIVE))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.filter).isEqualTo(TransferFilter.ACTIVE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilter to COMPLETED shows only completed transfers`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.SetFilter(TransferFilter.COMPLETED))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.filter).isEqualTo(TransferFilter.COMPLETED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pause calls pause use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.PauseTransfer(1L))
        advanceUntilIdle()

        coVerify { pauseTransferUseCase(1L) }
    }

    @Test
    fun `resume calls resume use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.ResumeTransfer(1L))
        advanceUntilIdle()

        coVerify { resumeTransferUseCase(1L) }
    }

    @Test
    fun `cancel calls cancel use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.CancelTransfer(1L))
        advanceUntilIdle()

        coVerify { cancelTransferUseCase(1L) }
    }

    @Test
    fun `retry enqueues new transfer with reset state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val failedTransfer = sampleTransfers[2]
        viewModel.onEvent(TransferEvent.RetryTransfer(failedTransfer))
        advanceUntilIdle()

        coVerify {
            enqueueTransferUseCase(
                match {
                    it.id == 0L &&
                        it.status == TransferStatus.QUEUED &&
                        it.transferredBytes == 0L &&
                        it.errorMessage == null &&
                        it.retryCount == failedTransfer.retryCount + 1 &&
                        it.sourcePath == failedTransfer.sourcePath
                },
            )
        }
    }

    @Test
    fun `clearCompleted calls clear completed use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.ClearCompleted)
        advanceUntilIdle()

        coVerify { clearCompletedTransfersUseCase() }
    }

    @Test
    fun `error from pause is captured in ui state`() = runTest {
        coEvery { pauseTransferUseCase(any()) } throws RuntimeException("Pause failed")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.PauseTransfer(1L))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.error).isEqualTo("Pause failed")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pauseAll invokes use case and clears nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.PauseAll)
        advanceUntilIdle()

        coVerify(exactly = 1) { pauseAllTransfersUseCase() }
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelAll invokes use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(TransferEvent.CancelAll)
        advanceUntilIdle()

        coVerify(exactly = 1) { cancelAllTransfersUseCase() }
    }

    @Test
    fun `conflictRequest flows into ui state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val request = ConflictRequest(
            id = "conflict-1",
            transferId = 1L,
            conflictedPath = "/remote/existing.txt",
            existingSize = 4096L,
            existingLastModified = 0L,
        )

        viewModel.uiState.test {
            // Initial state — no pending conflict after init load.
            assertThat(awaitItem().pendingConflict).isNull()

            conflictRepository.emit(request)
            advanceUntilIdle()

            assertThat(awaitItem().pendingConflict).isEqualTo(request)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolveConflict calls use case and clears pending conflict`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val request = ConflictRequest(
            id = "conflict-7",
            transferId = 2L,
            conflictedPath = "/remote/already.bin",
            existingSize = 2_097_152L,
            existingLastModified = 0L,
        )
        conflictRepository.emit(request)
        advanceUntilIdle()

        viewModel.onEvent(
            TransferEvent.ResolveConflict(request.id, ConflictResolution.OVERWRITE),
        )
        advanceUntilIdle()

        verify(exactly = 1) { resolveConflictUseCase(request.id, ConflictResolution.OVERWRITE) }
        viewModel.uiState.test {
            assertThat(awaitItem().pendingConflict).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeTransferConflictRepository : TransferConflictRepository {
    private val _conflictRequests = MutableSharedFlow<ConflictRequest>(extraBufferCapacity = 8)
    override val conflictRequests = _conflictRequests.asSharedFlow()

    suspend fun emit(request: ConflictRequest) {
        _conflictRequests.emit(request)
    }

    override fun emitConflict(request: ConflictRequest) {
        _conflictRequests.tryEmit(request)
    }

    override suspend fun awaitResolution(conflictId: String): ConflictResolution =
        ConflictResolution.SKIP

    override fun resolve(conflictId: String, resolution: ConflictResolution) {
        // no-op for tests
    }
}
