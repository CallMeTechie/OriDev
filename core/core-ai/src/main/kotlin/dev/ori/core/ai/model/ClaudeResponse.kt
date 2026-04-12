package dev.ori.core.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @Json(name = "stop_reason") val stopReason: String?,
    val usage: Usage,
) {
    @JsonClass(generateAdapter = true)
    data class ContentBlock(val type: String, val text: String? = null)

    @JsonClass(generateAdapter = true)
    data class Usage(
        @Json(name = "input_tokens") val inputTokens: Int,
        @Json(name = "output_tokens") val outputTokens: Int,
    )
}

@JsonClass(generateAdapter = true)
data class ClaudeErrorResponse(
    val type: String,
    val error: ClaudeErrorDetail,
)

@JsonClass(generateAdapter = true)
data class ClaudeErrorDetail(
    val type: String,
    val message: String,
)
