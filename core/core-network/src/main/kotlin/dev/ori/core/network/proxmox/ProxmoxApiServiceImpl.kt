package dev.ori.core.network.proxmox

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.core.network.proxmox.di.ProxmoxMoshi
import dev.ori.core.network.proxmox.model.ProxmoxCloneRequest
import dev.ori.core.network.proxmox.model.ProxmoxErrorResponse
import dev.ori.core.network.proxmox.model.ProxmoxNodeDto
import dev.ori.core.network.proxmox.model.ProxmoxNodeListResponse
import dev.ori.core.network.proxmox.model.ProxmoxTaskResponse
import dev.ori.core.network.proxmox.model.ProxmoxTaskStatusDto
import dev.ori.core.network.proxmox.model.ProxmoxTaskStatusResponse
import dev.ori.core.network.proxmox.model.ProxmoxVmDto
import dev.ori.core.network.proxmox.model.ProxmoxVmListResponse
import dev.ori.core.network.proxmox.model.ProxmoxVmStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.security.cert.CertificateException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
open class ProxmoxApiServiceImpl @Inject constructor(
    @ProxmoxMoshi private val moshi: Moshi,
) : ProxmoxApiService {

    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    private val nodeListAdapter = moshi.adapter(ProxmoxNodeListResponse::class.java)
    private val vmListAdapter = moshi.adapter(ProxmoxVmListResponse::class.java)
    private val vmStatusAdapter = moshi.adapter(ProxmoxVmStatusResponse::class.java)
    private val taskAdapter = moshi.adapter(ProxmoxTaskResponse::class.java)
    private val taskStatusAdapter = moshi.adapter(ProxmoxTaskStatusResponse::class.java)
    private val errorAdapter = moshi.adapter(ProxmoxErrorResponse::class.java)

    /** Override for tests that use plain HTTP (MockWebServer). */
    protected open fun clientFor(target: ProxmoxTarget): OkHttpClient {
        val key = "${target.host}:${target.port}:${target.expectedFingerprint}"
        return clientCache.getOrPut(key) {
            CertificateValidator.buildPinnedClient(target.expectedFingerprint)
        }
    }

    /** Override for tests -- builds base URL (https by default). */
    protected open fun baseUrl(target: ProxmoxTarget): HttpUrl =
        "https://${target.host}:${target.port}${ProxmoxApiService.PROXMOX_API_PATH}/".toHttpUrl()

    private fun buildUrl(target: ProxmoxTarget, vararg segments: String): HttpUrl {
        val builder = baseUrl(target).newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun authHeader(target: ProxmoxTarget): String =
        "PVEAPIToken=${target.tokenId}=${target.tokenSecret}"

    override suspend fun listNodes(target: ProxmoxTarget): AppResult<List<ProxmoxNodeDto>> =
        executeGet(target, buildUrl(target, "nodes")) { body ->
            nodeListAdapter.fromJson(body)?.data
                ?: error("Empty response parsing listNodes")
        }

    override suspend fun listVms(
        target: ProxmoxTarget,
        node: String,
    ): AppResult<List<ProxmoxVmDto>> =
        executeGet(target, buildUrl(target, "nodes", node, "qemu")) { body ->
            vmListAdapter.fromJson(body)?.data
                ?: error("Empty response parsing listVms")
        }

    override suspend fun getVmStatus(
        target: ProxmoxTarget,
        node: String,
        vmid: Int,
    ): AppResult<ProxmoxVmDto> =
        executeGet(
            target,
            buildUrl(target, "nodes", node, "qemu", vmid.toString(), "status", "current"),
        ) { body ->
            vmStatusAdapter.fromJson(body)?.data
                ?: error("Empty response parsing getVmStatus")
        }

    override suspend fun startVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String> =
        executePostForm(
            target,
            buildUrl(target, "nodes", node, "qemu", vmid.toString(), "status", "start"),
            FormBody.Builder().build(),
        )

    override suspend fun stopVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String> =
        executePostForm(
            target,
            buildUrl(target, "nodes", node, "qemu", vmid.toString(), "status", "stop"),
            FormBody.Builder().build(),
        )

    override suspend fun rebootVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String> =
        executePostForm(
            target,
            buildUrl(target, "nodes", node, "qemu", vmid.toString(), "status", "reboot"),
            FormBody.Builder().build(),
        )

    override suspend fun deleteVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String> =
        executeDelete(target, buildUrl(target, "nodes", node, "qemu", vmid.toString()))

    override suspend fun cloneVm(
        target: ProxmoxTarget,
        node: String,
        templateVmid: Int,
        request: ProxmoxCloneRequest,
    ): AppResult<String> {
        val formBuilder = FormBody.Builder()
            .add("newid", request.newid.toString())
            .add("name", request.name)
            .add("full", request.full.toString())
        request.target?.let { formBuilder.add("target", it) }
        return executePostForm(
            target,
            buildUrl(target, "nodes", node, "qemu", templateVmid.toString(), "clone"),
            formBuilder.build(),
        )
    }

    override suspend fun getTaskStatus(
        target: ProxmoxTarget,
        node: String,
        upid: String,
    ): AppResult<ProxmoxTaskStatusDto> =
        executeGet(
            target,
            buildUrl(target, "nodes", node, "tasks", upid, "status"),
        ) { body ->
            taskStatusAdapter.fromJson(body)?.data
                ?: error("Empty response parsing getTaskStatus")
        }

    private suspend fun <T> executeGet(
        target: ProxmoxTarget,
        url: HttpUrl,
        parse: (String) -> T,
    ): AppResult<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(target))
            .get()
            .build()
        executeAndParse(target, request, parse)
    }

    private suspend fun executePostForm(
        target: ProxmoxTarget,
        url: HttpUrl,
        body: RequestBody,
    ): AppResult<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(target))
            .post(body)
            .build()
        executeAndParse(target, request) { parseTaskUpid(it) }
    }

    private suspend fun executeDelete(
        target: ProxmoxTarget,
        url: HttpUrl,
    ): AppResult<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(target))
            .delete()
            .build()
        executeAndParse(target, request) { parseTaskUpid(it) }
    }

    private fun parseTaskUpid(body: String): String =
        taskAdapter.fromJson(body)?.data ?: error("Empty response parsing task UPID")

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun <T> executeAndParse(
        target: ProxmoxTarget,
        request: Request,
        parse: (String) -> T,
    ): AppResult<T> {
        val client = try {
            clientFor(target)
        } catch (e: Exception) {
            return appFailure(AppError.ProxmoxApiError(0, "Failed to build client: ${e.message}"))
        }
        return try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    parseSuccess(bodyString, parse)
                } else {
                    mapErrorResponse(response, bodyString)
                }
            }
        } catch (e: IOException) {
            mapIoException(e)
        }
    }

    private fun <T> parseSuccess(body: String, parse: (String) -> T): AppResult<T> =
        try {
            appSuccess(parse(body))
        } catch (e: JsonDataException) {
            appFailure(AppError.ProxmoxApiError(0, "Failed to parse response: ${e.message}"))
        } catch (e: IllegalStateException) {
            appFailure(AppError.ProxmoxApiError(0, e.message ?: "Invalid response"))
        } catch (e: IOException) {
            appFailure(AppError.ProxmoxApiError(0, "Failed to read response: ${e.message}"))
        }

    private fun <T> mapErrorResponse(response: Response, body: String): AppResult<T> {
        val parsedMessage = parseErrorMessage(body)
        val code = response.code
        val baseMessage = parsedMessage ?: "HTTP $code"
        return when (code) {
            HTTP_UNAUTHORIZED -> appFailure(
                AppError.AuthenticationError("Invalid Proxmox token: $baseMessage"),
            )
            HTTP_FORBIDDEN -> appFailure(
                AppError.PermissionDenied("Insufficient Proxmox permissions: $baseMessage"),
            )
            HTTP_NOT_FOUND -> appFailure(AppError.ProxmoxApiError(code, "Resource not found"))
            in HTTP_BAD_REQUEST..HTTP_BAD_REQUEST_END,
            in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END,
            -> appFailure(AppError.ProxmoxApiError(code, baseMessage))
            else -> appFailure(AppError.ProxmoxApiError(code, baseMessage))
        }
    }

    private fun <T> mapIoException(e: IOException): AppResult<T> {
        val cause = e.cause
        val isCertError = e is javax.net.ssl.SSLHandshakeException ||
            e is javax.net.ssl.SSLPeerUnverifiedException ||
            cause is CertificateException ||
            (e.message?.contains("fingerprint", ignoreCase = true) == true)
        return if (isCertError) {
            appFailure(AppError.ProxmoxApiError(0, "Certificate fingerprint mismatch -- possible MITM"))
        } else {
            appFailure(AppError.NetworkError("Network: ${e.message}", e))
        }
    }

    private fun parseErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            errorAdapter.fromJson(body)?.message
        } catch (_: JsonDataException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_BAD_REQUEST_END = 499
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_SERVER_ERROR_START = 500
        private const val HTTP_SERVER_ERROR_END = 599
    }
}
