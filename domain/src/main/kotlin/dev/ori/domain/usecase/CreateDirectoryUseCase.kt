package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class CreateDirectoryUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, path: String): AppResult<Unit> =
        try {
            repository.createDirectory(path)
            appSuccess(Unit)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to create directory: ${e.message}", e))
        }
}
