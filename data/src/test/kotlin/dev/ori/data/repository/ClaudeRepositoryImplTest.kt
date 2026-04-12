package dev.ori.data.repository

import com.google.common.truth.Truth.assertThat
import dev.ori.core.ai.ClaudeApiService
import dev.ori.core.ai.model.ClaudeMessage
import dev.ori.core.ai.model.ClaudeResponse
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppErrorException
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.CredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Note: MockK cannot stub suspend methods that return `Result` (inline value class)
 * without ClassCastException on unbox. We use a hand-rolled fake for ClaudeApiService.
 */
class ClaudeRepositoryImplTest {

    private class FakeClaudeApiService : ClaudeApiService {
        var result: AppResult<ClaudeResponse> = appSuccess(
            ClaudeResponse(
                id = "x",
                type = "message",
                role = "assistant",
                content = emptyList(),
                model = "claude-opus-4-6",
                stopReason = null,
                usage = ClaudeResponse.Usage(0, 0),
            ),
        )
        var lastApiKey: String? = null
        var lastMessages: List<ClaudeMessage>? = null

        override suspend fun sendMessage(
            apiKey: String,
            messages: List<ClaudeMessage>,
            system: String?,
            model: String,
        ): AppResult<ClaudeResponse> {
            lastApiKey = apiKey
            lastMessages = messages
            return result
        }
    }

    private val apiService = FakeClaudeApiService()
    private val credentialStore = mockk<CredentialStore>(relaxed = true)
    private val repository = ClaudeRepositoryImpl(apiService, credentialStore)

    private fun response(text: String): ClaudeResponse = ClaudeResponse(
        id = "msg_1",
        type = "message",
        role = "assistant",
        content = listOf(ClaudeResponse.ContentBlock(type = "text", text = text)),
        model = "claude-opus-4-6",
        stopReason = "end_turn",
        usage = ClaudeResponse.Usage(inputTokens = 10, outputTokens = 20),
    )

    @Test
    fun `sendPrompt returns success when api returns text`() = runTest {
        coEvery { credentialStore.getPassword("claude_api_key") } returns "sk-test".toCharArray()
        apiService.result = appSuccess(response("Hello!"))

        val result = repository.sendPrompt("Hi", context = null)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("Hello!")
        assertThat(apiService.lastApiKey).isEqualTo("sk-test")
    }

    @Test
    fun `sendPrompt includes context when provided`() = runTest {
        coEvery { credentialStore.getPassword("claude_api_key") } returns "sk-test".toCharArray()
        apiService.result = appSuccess(response("ok"))

        repository.sendPrompt("What is this?", context = "val x = 1")

        val sent = apiService.lastMessages?.first()
        assertThat(sent?.content).contains("val x = 1")
        assertThat(sent?.content).contains("What is this?")
    }

    @Test
    fun `sendPrompt returns auth failure when no key`() = runTest {
        coEvery { credentialStore.getPassword("claude_api_key") } returns null

        val result = repository.sendPrompt("hi", null)

        assertThat(result.isFailure).isTrue()
        val error = (result.exceptionOrNull() as? AppErrorException)?.error
        assertThat(error).isInstanceOf(AppError.AuthenticationError::class.java)
    }

    @Test
    fun `sendPrompt propagates api failure`() = runTest {
        coEvery { credentialStore.getPassword("claude_api_key") } returns "sk-test".toCharArray()
        apiService.result = appFailure(AppError.NetworkError("boom"))

        val result = repository.sendPrompt("hi", null)

        assertThat(result.isFailure).isTrue()
        val error = (result.exceptionOrNull() as? AppErrorException)?.error
        assertThat(error).isInstanceOf(AppError.NetworkError::class.java)
    }

    @Test
    fun `setApiKey stores credential under claude alias`() = runTest {
        repository.setApiKey("sk-abc")

        coVerify { credentialStore.storePassword("claude_api_key", any()) }
    }

    @Test
    fun `hasApiKey delegates to credential store`() = runTest {
        coEvery { credentialStore.hasCredential("claude_api_key") } returns true

        val result = repository.hasApiKey()

        assertThat(result).isTrue()
        coVerify { credentialStore.hasCredential("claude_api_key") }
    }
}
