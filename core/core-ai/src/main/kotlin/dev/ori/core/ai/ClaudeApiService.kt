package dev.ori.core.ai

import dev.ori.core.ai.model.ClaudeMessage
import dev.ori.core.ai.model.ClaudeResponse
import dev.ori.core.common.result.AppResult

interface ClaudeApiService {
    suspend fun sendMessage(
        apiKey: String,
        messages: List<ClaudeMessage>,
        system: String? = null,
        model: String = DEFAULT_MODEL,
    ): AppResult<ClaudeResponse>

    companion object {
        const val DEFAULT_MODEL = "claude-opus-4-6"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MAX_REQUEST_BYTES = 200_000
    }
}
