package dev.ori.feature.transfers.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.usecase.CancelTransferUseCase
import dev.ori.domain.usecase.ClearCompletedTransfersUseCase
import dev.ori.domain.usecase.EnqueueTransferUseCase
import dev.ori.domain.usecase.GetTransfersUseCase
import dev.ori.domain.usecase.PauseTransferUseCase
import dev.ori.domain.usecase.ResumeTransferUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
