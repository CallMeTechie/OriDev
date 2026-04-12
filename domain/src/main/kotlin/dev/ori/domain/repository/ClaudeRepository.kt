package dev.ori.domain.repository

import dev.ori.core.common.result.AppResult

interface ClaudeRepository {
    suspend fun hasApiKey(): Boolean
    suspend fun setApiKey(apiKey: String)
    suspend fun sendPrompt(userMessage: String, context: String? = null): AppResult<String>
}
