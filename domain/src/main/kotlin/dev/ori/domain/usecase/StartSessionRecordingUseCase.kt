package dev.ori.domain.usecase

import dev.ori.domain.model.SessionRecording
import dev.ori.domain.repository.SessionRecordingRepository
import javax.inject.Inject

class StartSessionRecordingUseCase @Inject constructor(
    private val repository: SessionRecordingRepository,
) {
    suspend operator fun invoke(serverProfileId: Long): SessionRecording =
        repository.startRecording(serverProfileId)
}
