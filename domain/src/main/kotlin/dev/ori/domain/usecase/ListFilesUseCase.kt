package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class ListFilesUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, path: String): AppResult<List<FileItem>> =
        try {
            val files = repository.listFiles(path)
            val sorted = files.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() },
            )
            appSuccess(sorted)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to list files: ${e.message}", e))
        }
}
