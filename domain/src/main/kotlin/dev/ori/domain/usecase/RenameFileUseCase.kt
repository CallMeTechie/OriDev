package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class RenameFileUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, oldPath: String, newPath: String): AppResult<Unit> =
        try {
            repository.renameFile(oldPath, newPath)
            appSuccess(Unit)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to rename: ${e.message}", e))
        }
}
