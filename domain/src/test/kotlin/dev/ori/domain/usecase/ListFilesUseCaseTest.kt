package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.result.getAppError
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ListFilesUseCaseTest {

    private val repository = mockk<FileSystemRepository>()
    private val useCase = ListFilesUseCase()

    @Test
    fun invoke_success_returnsSortedDirectoriesFirst() = runTest {
        coEvery { repository.listFiles("/") } returns listOf(
            FileItem("README.md", "/README.md", isDirectory = false, size = 100),
            FileItem("src", "/src", isDirectory = true),
            FileItem("build", "/build", isDirectory = true),
            FileItem("app.kt", "/app.kt", isDirectory = false, size = 200),
        )

        val result = useCase(repository, "/")

        assertThat(result.isSuccess).isTrue()
        val sorted = result.getOrNull()!!
        assertThat(sorted[0].name).isEqualTo("build")
        assertThat(sorted[1].name).isEqualTo("src")
        assertThat(sorted[2].name).isEqualTo("app.kt")
        assertThat(sorted[3].name).isEqualTo("README.md")
    }

    @Test
    fun invoke_failure_returnsFileOperationError() = runTest {
        coEvery { repository.listFiles("/") } throws RuntimeException("Permission denied")

        val result = useCase(repository, "/")

        assertThat(result.isFailure).isTrue()
        assertThat(result.getAppError()!!.message).contains("Permission denied")
    }

    @Test
    fun invoke_emptyDirectory_returnsEmptyList() = runTest {
        coEvery { repository.listFiles("/empty") } returns emptyList()

        val result = useCase(repository, "/empty")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEmpty()
    }
}
