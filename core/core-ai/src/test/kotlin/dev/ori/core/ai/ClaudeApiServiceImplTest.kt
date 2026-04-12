package dev.ori.core.ai

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.ori.core.ai.model.ClaudeMessage
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.getAppError
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ClaudeApiServiceImplTest {

    private lateinit var server: MockWebServer
    private lateinit var service: ClaudeApiServiceImpl

    private val successBody = """
        {
          "id": "msg_123",
          "type": "message",
          "role": "assistant",
          "content": [{"type": "text", "text": "Hello there"}],
          "model": "claude-opus-4-6",
          "stop_reason": "end_turn",
          "usage": {"input_tokens": 10, "output_tokens": 20}
        }
    """.trimIndent()

    private val errorBody = """
        {"type": "error", "error": {"type": "invalid_request_error", "message": "Bad request detail"}}
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        service = ClaudeApiServiceImpl(
            okHttpClient = client,
            moshi = moshi,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sendMessage returns success on 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody))

        val result = service.sendMessage(
            apiKey = "k",
            messages = listOf(ClaudeMessage("user", "Hi")),
        )

        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()!!
        assertThat(response.id).isEqualTo("msg_123")
        assertThat(response.content.first().text).isEqualTo("Hello there")
        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("x-api-key")).isEqualTo("k")
        assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01")
    }

    @Test
    fun `sendMessage maps 400 to FileOperationError with parsed message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody(errorBody))

        val result = service.sendMessage("k", listOf(ClaudeMessage("user", "Hi")))

        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.FileOperationError::class.java)
        assertThat(error!!.message).contains("Bad request detail")
    }

    @Test
    fun `sendMessage maps 401 to AuthenticationError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        val result = service.sendMessage("k", listOf(ClaudeMessage("user", "Hi")))

        assertThat(result.isFailure).isTrue()
        assertThat(result.getAppError()).isInstanceOf(AppError.AuthenticationError::class.java)
    }

    @Test
    fun `sendMessage retries on 429 then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("retry-after", "0").setBody(errorBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody))

        val result = service.sendMessage("k", listOf(ClaudeMessage("user", "Hi")))

        assertThat(result.isSuccess).isTrue()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `sendMessage retries 3 times on 529 then fails with NetworkError`() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(529).setHeader("retry-after", "0").setBody(errorBody))
        }

        val result = service.sendMessage("k", listOf(ClaudeMessage("user", "Hi")))

        assertThat(result.isFailure).isTrue()
        assertThat(result.getAppError()).isInstanceOf(AppError.NetworkError::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `sendMessage retries on 500 then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setHeader("retry-after", "0").setBody(errorBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody))

        val result = service.sendMessage("k", listOf(ClaudeMessage("user", "Hi")))

        assertThat(result.isSuccess).isTrue()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `sendMessage rejects oversized request without calling network`() = runTest {
        val bigContent = "a".repeat(ClaudeApiService.MAX_REQUEST_BYTES + 1)
        val result = service.sendMessage("k", listOf(ClaudeMessage("user", bigContent)))

        assertThat(result.isFailure).isTrue()
        assertThat(result.getAppError()).isInstanceOf(AppError.FileOperationError::class.java)
        assertThat(result.getAppError()!!.message).contains("Request too large")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `sendMessage sends system prompt when provided`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody))

        val result = service.sendMessage(
            apiKey = "k",
            messages = listOf(ClaudeMessage("user", "Hi")),
            system = "You are helpful",
        )

        assertThat(result.isSuccess).isTrue()
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertThat(body).contains("\"system\":\"You are helpful\"")
    }
}
