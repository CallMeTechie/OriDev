package dev.ori.core.ai

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dev.ori.core.ai.model.ClaudeErrorResponse
import dev.ori.core.ai.model.ClaudeMessage
import dev.ori.core.ai.model.ClaudeRequest
import dev.ori.core.ai.model.ClaudeResponse
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaudeApiServiceImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : ClaudeApiService {

    private val requestAdapter = moshi.adapter(ClaudeRequest::class.java)
    private val responseAdapter = moshi.adapter(ClaudeResponse::class.java)
    private val errorAdapter = moshi.adapter(ClaudeErrorResponse::class.java)

    @Suppress("ReturnCount", "LongMethod")
    override suspend fun sendMessage(
        apiKey: String,
        messages: List<ClaudeMessage>,
        system: String?,
        model: String,
    ): AppResult<ClaudeResponse> = withContext(Dispatchers.IO) {
        val requestBody = ClaudeRequest(
            model = model,
            messages = messages,
            system = system,
        )

        val json = try {
            requestAdapter.toJson(requestBody)
        } catch (e: JsonDataException) {
            return@withContext appFailure(
                AppError.FileOperationError("Failed to serialize Claude request: ${e.message}", e),
            )
        }

        val bodyBytes = json.toByteArray(Charsets.UTF_8)
        if (bodyBytes.size > ClaudeApiService.MAX_REQUEST_BYTES) {
            return@withContext appFailure(
                AppError.FileOperationError(
                    "Request too large: ${bodyBytes.size} bytes exceeds limit of " +
                        "${ClaudeApiService.MAX_REQUEST_BYTES} bytes",
                ),
            )
        }

        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ClaudeApiService.ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(bodyBytes.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        var attempt = 0
        var lastError: AppError? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val result = try {
                executeCall(httpRequest)
            } catch (e: IOException) {
                CallOutcome.Failure(AppError.NetworkError("Network error: ${e.message}", e), retryable = true)
            }

            when (result) {
                is CallOutcome.Success -> return@withContext appSuccess(result.response)
                is CallOutcome.Failure -> {
                    lastError = result.error
                    if (!result.retryable || attempt >= MAX_ATTEMPTS) {
                        return@withContext appFailure(result.error)
                    }
                    val backoffMs = result.retryAfterMs ?: (BASE_BACKOFF_MS shl (attempt - 1))
                    delay(backoffMs)
                }
            }
        }

        appFailure(lastError ?: AppError.NetworkError("Claude API request failed after retries"))
    }

    private fun executeCall(request: Request): CallOutcome {
        okHttpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                return parseSuccess(bodyString)
            }
            return mapErrorResponse(response, bodyString)
        }
    }

    private fun parseSuccess(body: String): CallOutcome {
        return try {
            val parsed = responseAdapter.fromJson(body)
            if (parsed == null) {
                CallOutcome.Failure(
                    AppError.NetworkError("Empty response body from Claude API"),
                    retryable = false,
                )
            } else {
                CallOutcome.Success(parsed)
            }
        } catch (e: JsonDataException) {
            CallOutcome.Failure(
                AppError.NetworkError("Failed to parse Claude response: ${e.message}", e),
                retryable = false,
            )
        } catch (e: IOException) {
            CallOutcome.Failure(
                AppError.NetworkError("Failed to read Claude response: ${e.message}", e),
                retryable = false,
            )
        }
    }

    private fun mapErrorResponse(response: Response, body: String): CallOutcome {
        val parsedMessage = parseErrorMessage(body)
        val code = response.code
        val baseMessage = parsedMessage ?: "HTTP $code"
        return when (code) {
            HTTP_BAD_REQUEST -> CallOutcome.Failure(
                AppError.FileOperationError("Claude API bad request: $baseMessage"),
                retryable = false,
            )
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> CallOutcome.Failure(
                AppError.AuthenticationError("Claude API auth failed: $baseMessage"),
                retryable = false,
            )
            HTTP_TOO_MANY_REQUESTS, HTTP_OVERLOADED -> CallOutcome.Failure(
                AppError.NetworkError("Claude API rate limited ($code): $baseMessage"),
                retryable = true,
                retryAfterMs = parseRetryAfter(response),
            )
            in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END -> CallOutcome.Failure(
                AppError.NetworkError("Claude API server error ($code): $baseMessage"),
                retryable = true,
                retryAfterMs = parseRetryAfter(response),
            )
            else -> CallOutcome.Failure(
                AppError.NetworkError("Claude API error ($code): $baseMessage"),
                retryable = false,
            )
        }
    }

    private fun parseErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            errorAdapter.fromJson(body)?.error?.message
        } catch (_: JsonDataException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun parseRetryAfter(response: Response): Long? {
        val header = response.header("retry-after") ?: return null
        val seconds = header.toLongOrNull() ?: return null
        return seconds * MILLIS_PER_SECOND
    }

    private sealed class CallOutcome {
        data class Success(val response: ClaudeResponse) : CallOutcome()
        data class Failure(
            val error: AppError,
            val retryable: Boolean,
            val retryAfterMs: Long? = null,
        ) : CallOutcome()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        private const val MAX_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_OVERLOADED = 529
        private const val HTTP_SERVER_ERROR_START = 500
        private const val HTTP_SERVER_ERROR_END = 599
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
