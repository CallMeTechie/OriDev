package dev.ori.domain.usecase

import dev.ori.domain.repository.SessionRecordingRepository
import javax.inject.Inject

class StopSessionRecordingUseCase @Inject constructor(
    private val repository: SessionRecordingRepository,
) {
    suspend operator fun invoke(recordingId: Long) {
        repository.stopRecording(recordingId)
    }
}
