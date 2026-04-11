package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.result.getAppError
import dev.ori.domain.repository.FileSystemRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FileOperationUseCaseTest {

    private val repository = mockk<FileSystemRepository>()

    @Test
    fun deleteFile_success() = runTest {
        coEvery { repository.deleteFile("/file.txt") } just runs
        val result = DeleteFileUseCase()(repository, "/file.txt")
        assertThat(result.isSuccess).isTrue()
        coVerify { repository.deleteFile("/file.txt") }
    }

    @Test
    fun deleteFile_failure_returnsError() = runTest {
        coEvery { repository.deleteFile(any()) } throws RuntimeException("busy")
        val result = DeleteFileUseCase()(repository, "/file.txt")
        assertThat(result.isFailure).isTrue()
        assertThat(result.getAppError()!!.message).contains("busy")
    }

    @Test
    fun renameFile_success() = runTest {
        coEvery { repository.renameFile("/old.txt", "/new.txt") } just runs
        val result = RenameFileUseCase()(repository, "/old.txt", "/new.txt")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun createDirectory_success() = runTest {
        coEvery { repository.createDirectory("/newdir") } just runs
        val result = CreateDirectoryUseCase()(repository, "/newdir")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun chmod_success() = runTest {
        coEvery { repository.chmod("/file.sh", "755") } just runs
        val result = ChmodUseCase()(repository, "/file.sh", "755")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun chmod_failure_returnsError() = runTest {
        coEvery { repository.chmod(any(), any()) } throws RuntimeException("not supported")
        val result = ChmodUseCase()(repository, "/file.sh", "755")
        assertThat(result.isFailure).isTrue()
    }
}
