package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.core.common.result.getAppError
import dev.ori.domain.repository.ClaudeRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ClaudeUseCaseTest {

    private class FakeClaudeRepository(
        var nextResult: AppResult<String> = appSuccess(""),
        var hasKey: Boolean = false,
    ) : ClaudeRepository {
        var lastMessage: String? = null
        var lastContext: String? = null
        var lastKey: String? = null

        override suspend fun hasApiKey(): Boolean = hasKey
        override suspend fun setApiKey(apiKey: String) { lastKey = apiKey }
        override suspend fun sendPrompt(userMessage: String, context: String?): AppResult<String> {
            lastMessage = userMessage
            lastContext = context
            return nextResult
        }
    }

    @Test
    fun `SendToClaudeUseCase returns success from repository`() = runTest {
        val repo = FakeClaudeRepository(nextResult = appSuccess("world"))
        val useCase = SendToClaudeUseCase(repo)

        val result = useCase("hello")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("world")
        assertThat(repo.lastMessage).isEqualTo("hello")
        assertThat(repo.lastContext).isNull()
    }

    @Test
    fun `SendToClaudeUseCase propagates failure from repository`() = runTest {
        val repo = FakeClaudeRepository(
            nextResult = appFailure(AppError.NetworkError("offline")),
        )
        val useCase = SendToClaudeUseCase(repo)

        val result = useCase("hi", "ctx")

        assertThat(result.isFailure).isTrue()
        assertThat(result.getAppError()).isInstanceOf(AppError.NetworkError::class.java)
        assertThat(repo.lastContext).isEqualTo("ctx")
    }

    @Test
    fun `SetClaudeApiKeyUseCase delegates to repository`() = runTest {
        val repo = FakeClaudeRepository()
        SetClaudeApiKeyUseCase(repo)("sk-ant-123")

        assertThat(repo.lastKey).isEqualTo("sk-ant-123")
    }

    @Test
    fun `repository hasApiKey is queryable`() = runTest {
        val repo = FakeClaudeRepository(hasKey = true)
        assertThat(repo.hasApiKey()).isTrue()
    }
}
