package dev.ori.data.repository

import dev.ori.core.ai.ClaudeApiService
import dev.ori.core.ai.model.ClaudeMessage
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.domain.repository.ClaudeRepository
import dev.ori.domain.repository.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaudeRepositoryImpl @Inject constructor(
    private val claudeApiService: ClaudeApiService,
    private val credentialStore: CredentialStore,
) : ClaudeRepository {

    override suspend fun hasApiKey(): Boolean =
        credentialStore.hasCredential(CLAUDE_API_KEY_ALIAS)

    override suspend fun setApiKey(apiKey: String) {
        credentialStore.storePassword(CLAUDE_API_KEY_ALIAS, apiKey.toCharArray())
    }

    override suspend fun sendPrompt(userMessage: String, context: String?): AppResult<String> {
        val keyChars = credentialStore.getPassword(CLAUDE_API_KEY_ALIAS)
            ?: return appFailure(AppError.AuthenticationError("Claude API key not set"))

        val apiKey = String(keyChars)
        keyChars.fill('\u0000')

        val content = if (context != null) {
            "Context:\n```\n$context\n```\n\nQuestion: $userMessage"
        } else {
            userMessage
        }

        val messages = listOf(ClaudeMessage(role = "user", content = content))

        return claudeApiService.sendMessage(apiKey, messages).map { response ->
            response.content.firstOrNull { it.type == "text" }?.text.orEmpty()
        }
    }

    companion object {
        private const val CLAUDE_API_KEY_ALIAS = "claude_api_key"
    }
}
