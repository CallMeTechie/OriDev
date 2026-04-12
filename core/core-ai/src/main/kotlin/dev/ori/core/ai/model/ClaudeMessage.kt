package dev.ori.core.ai.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClaudeMessage(
    val role: String,
    val content: String,
)
