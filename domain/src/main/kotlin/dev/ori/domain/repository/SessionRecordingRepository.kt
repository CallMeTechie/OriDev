package dev.ori.domain.repository

import dev.ori.domain.model.SessionRecording
import kotlinx.coroutines.flow.Flow

interface SessionRecordingRepository {
    suspend fun startRecording(serverProfileId: Long): SessionRecording
    suspend fun appendOutput(recordingId: Long, data: ByteArray)
    suspend fun stopRecording(recordingId: Long)
    suspend fun exportAsMarkdown(recordingId: Long): String
    fun getRecordingsForServer(serverProfileId: Long): Flow<List<SessionRecording>>
}
