package dev.ori.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.TransferRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TransferUseCaseTest {

    private lateinit var repository: TransferRepository
    private lateinit var enqueueUseCase: EnqueueTransferUseCase
    private lateinit var pauseUseCase: PauseTransferUseCase
    private lateinit var resumeUseCase: ResumeTransferUseCase
    private lateinit var cancelUseCase: CancelTransferUseCase
    private lateinit var getTransfersUseCase: GetTransfersUseCase

    private val sampleTransfer = TransferRequest(
        serverProfileId = 1L,
        sourcePath = "/local/file.txt",
        destinationPath = "/remote/file.txt",
        direction = TransferDirection.UPLOAD,
    )

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        enqueueUseCase = EnqueueTransferUseCase(repository)
        pauseUseCase = PauseTransferUseCase(repository)
        resumeUseCase = ResumeTransferUseCase(repository)
        cancelUseCase = CancelTransferUseCase(repository)
        getTransfersUseCase = GetTransfersUseCase(repository)
    }

    @Test
    fun `enqueue returns transfer id`() = runTest {
        coEvery { repository.enqueue(any()) } returns 42L

        val id = enqueueUseCase(sampleTransfer)

        assertThat(id).isEqualTo(42L)
        coVerify { repository.enqueue(sampleTransfer) }
    }

    @Test
    fun `pause calls repository pause`() = runTest {
        pauseUseCase(5L)

        coVerify { repository.pause(5L) }
    }

    @Test
    fun `resume calls repository resume`() = runTest {
        resumeUseCase(5L)

        coVerify { repository.resume(5L) }
    }

    @Test
    fun `cancel calls repository cancel`() = runTest {
        cancelUseCase(5L)

        coVerify { repository.cancel(5L) }
    }

    @Test
    fun `getTransfers returns flow from repository`() = runTest {
        val transfers = listOf(
            sampleTransfer.copy(id = 1L),
            sampleTransfer.copy(id = 2L, status = TransferStatus.COMPLETED),
        )
        every { repository.getAllTransfers() } returns flowOf(transfers)

        getTransfersUseCase().test {
            val result = awaitItem()
            assertThat(result).hasSize(2)
            assertThat(result[0].id).isEqualTo(1L)
            assertThat(result[1].status).isEqualTo(TransferStatus.COMPLETED)
            awaitComplete()
        }
    }
}
