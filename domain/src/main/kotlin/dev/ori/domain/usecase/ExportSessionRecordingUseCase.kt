package dev.ori.domain.usecase

import dev.ori.domain.repository.SessionRecordingRepository
import javax.inject.Inject

class ExportSessionRecordingUseCase @Inject constructor(
    private val repository: SessionRecordingRepository,
) {
    suspend operator fun invoke(recordingId: Long): String =
        repository.exportAsMarkdown(recordingId)
}
