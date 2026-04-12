package dev.ori.domain.model

data class SessionRecording(
    val id: Long = 0,
    val serverProfileId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val logFilePath: String,
)
