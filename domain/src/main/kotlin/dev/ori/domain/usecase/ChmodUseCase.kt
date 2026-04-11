package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class ChmodUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, path: String, permissions: String): AppResult<Unit> =
        try {
            repository.chmod(path, permissions)
            appSuccess(Unit)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to chmod: ${e.message}", e))
        }
}
