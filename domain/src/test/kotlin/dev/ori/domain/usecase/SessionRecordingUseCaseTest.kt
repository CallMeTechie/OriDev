package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.domain.model.SessionRecording
import dev.ori.domain.repository.SessionRecordingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SessionRecordingUseCaseTest {

    private val repository: SessionRecordingRepository = mockk(relaxed = true)

    private val recording = SessionRecording(
        id = 1L,
        serverProfileId = 10L,
        startedAt = 1_000L,
        logFilePath = "/tmp/rec.log",
    )

    @Test
    fun `StartSessionRecordingUseCase returns recording from repository`() = runTest {
        coEvery { repository.startRecording(10L) } returns recording

        val result = StartSessionRecordingUseCase(repository)(10L)

        assertThat(result).isEqualTo(recording)
    }

    @Test
    fun `StopSessionRecordingUseCase delegates to repository`() = runTest {
        StopSessionRecordingUseCase(repository)(1L)
        coVerify { repository.stopRecording(1L) }
    }

    @Test
    fun `ExportSessionRecordingUseCase returns markdown from repository`() = runTest {
        coEvery { repository.exportAsMarkdown(1L) } returns "# session"

        val result = ExportSessionRecordingUseCase(repository)(1L)

        assertThat(result).isEqualTo("# session")
    }

    @Test
    fun `repository appendOutput can be invoked`() = runTest {
        val bytes = "data".toByteArray()
        repository.appendOutput(1L, bytes)
        coVerify { repository.appendOutput(1L, bytes) }
    }

    @Test
    fun `StartSessionRecordingUseCase propagates repository exception`() = runTest {
        coEvery { repository.startRecording(any()) } throws IllegalStateException("disk full")

        assertThrows<IllegalStateException> {
            StartSessionRecordingUseCase(repository)(5L)
        }
    }

    @Test
    fun `ExportSessionRecordingUseCase propagates repository exception`() = runTest {
        coEvery { repository.exportAsMarkdown(any()) } throws IllegalArgumentException("missing")

        assertThrows<IllegalArgumentException> {
            ExportSessionRecordingUseCase(repository)(99L)
        }
    }
}
