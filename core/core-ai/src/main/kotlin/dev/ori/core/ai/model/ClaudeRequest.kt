package dev.ori.core.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClaudeRequest(
    val model: String,
    @Json(name = "max_tokens") val maxTokens: Int = 4096,
    val messages: List<ClaudeMessage>,
    val system: String? = null,
    val stream: Boolean = false,
)
