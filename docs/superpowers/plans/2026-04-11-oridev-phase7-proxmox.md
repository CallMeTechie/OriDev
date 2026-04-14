# Ori:Dev Phase 7: Proxmox VM Manager (v2 -- post-review)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Proxmox VM Manager: REST API client with TLS certificate pinning (TOFU), per-node VM management with live status, VM lifecycle actions (start/stop/restart/delete), Create VM wizard (Template -> Config -> Network -> Review), auto-connect flow (clone + start + poll SSH + open terminal).

**Scope note**: Each Room `ProxmoxNodeEntity` represents a SINGLE node endpoint, not a cluster. The API client can call `listNodes()` to see cluster members reachable from that endpoint, but VM aggregation across multiple cluster members is deferred. For v1, users add each node individually.

**Architecture:** New `core-proxmox` module wraps OkHttp + Moshi for Proxmox REST API. `ProxmoxRepository` in domain, `ProxmoxRepositoryImpl` in data coordinates API + Room (ProxmoxNodeEntity). `feature-proxmox` UI observes state, triggers wizard. Certificate pinning uses `ProxmoxNodeEntity.certFingerprint` (TOFU like SSH host keys).

**Tech Stack:** OkHttp 4.12.0, Moshi 1.15.2 (existing), Hilt, Room, Compose. No new dependencies.

**Depends on:** Phase 1 (ProxmoxNodeEntity + DAO exist), Phase 2 (KeyStoreManager for token storage).

---

## File Structure

```
core/
├── core-network/src/main/kotlin/dev/ori/core/network/proxmox/
│   ├── ProxmoxApiService.kt        (interface)
│   ├── ProxmoxApiServiceImpl.kt
│   ├── CertificateValidator.kt     (TOFU SHA-256 pinning)
│   ├── model/
│   │   ├── ProxmoxNodeDto.kt
│   │   ├── ProxmoxVmDto.kt
│   │   ├── ProxmoxTaskDto.kt
│   │   ├── ProxmoxTemplateDto.kt
│   │   └── ProxmoxCloneRequest.kt
│   └── di/
│       └── ProxmoxNetworkModule.kt

domain/src/main/kotlin/dev/ori/domain/
├── model/
│   ├── ProxmoxNode.kt
│   ├── ProxmoxVm.kt
│   ├── ProxmoxTemplate.kt
│   └── ProxmoxVmStatus.kt          (enum)
├── repository/
│   └── ProxmoxRepository.kt
├── usecase/
│   ├── GetProxmoxNodesUseCase.kt
│   ├── GetProxmoxVmsUseCase.kt
│   ├── StartVmUseCase.kt
│   ├── StopVmUseCase.kt
│   ├── RestartVmUseCase.kt
│   ├── DeleteVmUseCase.kt
│   ├── GetProxmoxTemplatesUseCase.kt
│   ├── CloneVmUseCase.kt
│   └── PollVmSshUseCase.kt

data/src/main/kotlin/dev/ori/data/
├── repository/
│   └── ProxmoxRepositoryImpl.kt
├── mapper/
│   └── ProxmoxMapper.kt
├── di/
│   └── ProxmoxBindingsModule.kt

feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/
├── ui/
│   ├── ProxmoxDashboardScreen.kt
│   ├── ProxmoxDashboardViewModel.kt
│   ├── ProxmoxDashboardUiState.kt
│   ├── NodeCard.kt
│   ├── VmCard.kt
│   ├── AddNodeSheet.kt
│   ├── CertificateTrustDialog.kt
│   ├── CreateVmWizard.kt
│   └── CreateVmWizardViewModel.kt
├── navigation/
│   └── ProxmoxNavigation.kt
```

---

### Task 7.1: core-network -- Proxmox API Client

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/proxmox/ProxmoxApiService.kt` (interface)
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/proxmox/ProxmoxApiServiceImpl.kt`
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/proxmox/CertificateValidator.kt`
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/proxmox/model/*.kt` (5 files)
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/proxmox/di/ProxmoxNetworkModule.kt`
- Modify: `core/core-network/build.gradle.kts` (add okhttp, okhttp-tls, moshi, moshi-codegen)
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/proxmox/ProxmoxApiServiceImplTest.kt`

- [ ] **Step 1: Add dependencies to core-network**

Read current `core/core-network/build.gradle.kts`. Add:
```kotlin
implementation(libs.okhttp)
implementation(libs.okhttp.logging)
implementation(libs.moshi)
implementation(libs.moshi.kotlin)
ksp(libs.moshi.codegen)

testImplementation(libs.okhttp.mockwebserver)
```

Verify `ksp` plugin is already applied. If not, add `alias(libs.plugins.ksp)` to plugins block.

- [ ] **Step 2: Create Proxmox data models (Moshi)**

**ProxmoxNodeDto.kt** -- represents a node in the cluster:
```kotlin
package dev.ori.core.network.proxmox.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProxmoxNodeDto(
    val node: String,
    val status: String, // "online" | "offline"
    val cpu: Double? = null,
    val maxcpu: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val uptime: Long? = null,
)

@JsonClass(generateAdapter = true)
data class ProxmoxNodeListResponse(
    val data: List<ProxmoxNodeDto>,
)
```

**ProxmoxVmDto.kt:**
```kotlin
@JsonClass(generateAdapter = true)
data class ProxmoxVmDto(
    val vmid: Int,
    val name: String? = null,
    val status: String, // "running" | "stopped" | "paused"
    val cpu: Double? = null,
    val cpus: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val uptime: Long? = null,
    val template: Int? = null, // 1 if template, 0 or null if VM
)

@JsonClass(generateAdapter = true)
data class ProxmoxVmListResponse(val data: List<ProxmoxVmDto>)

@JsonClass(generateAdapter = true)
data class ProxmoxVmStatusResponse(val data: ProxmoxVmDto)
```

**ProxmoxTemplateDto.kt:**
```kotlin
@JsonClass(generateAdapter = true)
data class ProxmoxTemplateDto(
    val vmid: Int,
    val name: String,
    val node: String,
)
```

**ProxmoxTaskDto.kt** -- Proxmox returns task IDs for async operations:
```kotlin
@JsonClass(generateAdapter = true)
data class ProxmoxTaskResponse(
    val data: String, // task UPID like "UPID:pve1:00001234:..."
)

@JsonClass(generateAdapter = true)
data class ProxmoxTaskStatusDto(
    val status: String, // "running" | "stopped"
    val exitstatus: String? = null, // "OK" | error message
)

@JsonClass(generateAdapter = true)
data class ProxmoxTaskStatusResponse(val data: ProxmoxTaskStatusDto)
```

**ProxmoxCloneRequest.kt:**
```kotlin
@JsonClass(generateAdapter = true)
data class ProxmoxCloneRequest(
    val newid: Int,
    val name: String,
    val full: Int = 1, // 1 = full clone, 0 = linked clone
    val target: String? = null, // target node
)
```

**ProxmoxErrorResponse.kt** -- for error body parsing:
```kotlin
@JsonClass(generateAdapter = true)
data class ProxmoxErrorResponse(
    val errors: Map<String, String>? = null,
    val message: String? = null,
)
```

- [ ] **Step 3: Create CertificateValidator for TOFU pinning**

CRITICAL: Validation MUST happen at the TrustManager layer (during TLS handshake), NOT via a post-request Interceptor. Otherwise the auth token in the request header is already sent to a potential MITM before validation fires.

Proxmox uses self-signed certificates by default. We implement TOFU via a custom `X509TrustManager` that enforces SHA-256 fingerprint matching during `checkServerTrusted()`.

```kotlin
package dev.ori.core.network.proxmox

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class FingerprintProbeResult {
    data class Success(val fingerprint: String) : FingerprintProbeResult()
    data class Failure(val reason: String) : FingerprintProbeResult()
}

object CertificateValidator {

    /**
     * Compute SHA-256 fingerprint of a certificate.
     * Format: uppercase hex with colons (e.g., "A1:B2:C3:...").
     */
    fun computeFingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Builds an OkHttpClient that validates the server certificate against a pinned fingerprint
     * DURING the TLS handshake. Fails the handshake (before any request bytes are sent) if mismatch.
     *
     * Hostname verification is disabled because Proxmox hosts are typically accessed via IP and
     * use self-signed certs with wildcard or missing SANs. This is acceptable BECAUSE the certificate
     * fingerprint pinning provides stronger binding than hostname verification.
     */
    fun buildPinnedClient(pinnedFingerprint: String): OkHttpClient {
        val pinningTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                throw CertificateException("Client certificates not supported")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                if (chain.isEmpty()) {
                    throw CertificateException("Empty certificate chain")
                }
                val leaf = chain[0]
                val actual = computeFingerprint(leaf)
                if (actual != pinnedFingerprint) {
                    throw CertificateException(
                        "Certificate fingerprint mismatch.\nExpected: $pinnedFingerprint\nActual: $actual",
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(pinningTrustManager), java.security.SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, pinningTrustManager)
            .hostnameVerifier { _, _ -> true } // pinning supersedes hostname check for self-signed Proxmox
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Probes a Proxmox host via a one-shot SSLSocket to extract the leaf certificate fingerprint.
     * Used ONLY during the "Add Node" wizard, BEFORE any request with auth headers is made.
     *
     * The probe client accepts any cert (it is read-only, no auth). The extracted fingerprint is
     * presented to the user for explicit TOFU approval, then stored. Subsequent connections use
     * buildPinnedClient() with the stored fingerprint.
     */
    suspend fun probeFingerprint(host: String, port: Int): FingerprintProbeResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val trustAll = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<TrustManager>(trustAll), java.security.SecureRandom())
                }
                val socket = sslContext.socketFactory.createSocket(host, port) as SSLSocket
                socket.use { s ->
                    s.soTimeout = 10_000
                    s.startHandshake()
                    val leaf = s.session.peerCertificates.firstOrNull() as? X509Certificate
                        ?: return@withContext FingerprintProbeResult.Failure("No leaf certificate")
                    FingerprintProbeResult.Success(computeFingerprint(leaf))
                }
            } catch (e: java.net.UnknownHostException) {
                FingerprintProbeResult.Failure("Unknown host: $host")
            } catch (e: java.net.ConnectException) {
                FingerprintProbeResult.Failure("Connection refused to $host:$port")
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                FingerprintProbeResult.Failure("TLS handshake failed: ${e.message}")
            } catch (e: Exception) {
                FingerprintProbeResult.Failure("Probe failed: ${e.message}")
            }
        }
}
```

Key security properties:
- **Validation during handshake**: `checkServerTrusted()` throws `CertificateException` BEFORE any application data (including the auth header) is sent
- **Pinning supersedes hostname check**: self-signed Proxmox certs use IPs without matching SANs; the SHA-256 pin is a stronger binding
- **Probe uses a separate trust-all client** that is ONLY used for the initial read-only fingerprint extraction, never for authenticated requests
- **Probe returns typed errors** so the user can distinguish "host unreachable" from "handshake failed"

- [ ] **Step 4: Create ProxmoxApiService interface**

```kotlin
package dev.ori.core.network.proxmox

import dev.ori.core.common.result.AppResult
import dev.ori.core.network.proxmox.model.*

interface ProxmoxApiService {
    /** Node operations */
    suspend fun listNodes(target: ProxmoxTarget): AppResult<List<ProxmoxNodeDto>>

    /** VM operations */
    suspend fun listVms(target: ProxmoxTarget, node: String): AppResult<List<ProxmoxVmDto>>
    suspend fun getVmStatus(target: ProxmoxTarget, node: String, vmid: Int): AppResult<ProxmoxVmDto>
    suspend fun startVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String>
    suspend fun stopVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String>
    suspend fun rebootVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String>
    suspend fun deleteVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String>

    /** Template operations -- templates filtered from listVms() in repository, not a separate endpoint */
    suspend fun cloneVm(
        target: ProxmoxTarget,
        node: String,
        templateVmid: Int,
        request: ProxmoxCloneRequest,
    ): AppResult<String>

    /** Task status (for async operations) */
    suspend fun getTaskStatus(target: ProxmoxTarget, node: String, upid: String): AppResult<ProxmoxTaskStatusDto>
}

/**
 * Connection target for a Proxmox API call. Includes the stored fingerprint so the
 * API client can build a per-request pinned OkHttpClient. NOT a credential DTO --
 * name is "Target" to avoid confusion with tokenSecret (which is a credential).
 */
data class ProxmoxTarget(
    val host: String,
    val port: Int = 8006,
    val tokenId: String,    // "user@realm!tokenname"
    val tokenSecret: String,
    val expectedFingerprint: String, // MUST be set (TOFU probe happens before first API call)
)
```

- [ ] **Step 5: Create ProxmoxApiServiceImpl**

Key implementation details:
- Base URL: `https://{host}:{port}/api2/json/`
- Auth header: `Authorization: PVEAPIToken={tokenId}={tokenSecret}`
- Content-Type: `application/x-www-form-urlencoded` for POST
- Endpoints:
  - GET `/nodes` -> list nodes
  - GET `/nodes/{node}/qemu` -> list VMs (and templates, filtered client-side by `template == 1`)
  - GET `/nodes/{node}/qemu/{vmid}/status/current` -> VM status
  - POST `/nodes/{node}/qemu/{vmid}/status/start` -> start VM
  - POST `/nodes/{node}/qemu/{vmid}/status/stop` -> stop VM (hard power-off)
  - POST `/nodes/{node}/qemu/{vmid}/status/shutdown` -> graceful shutdown (optional, future)
  - POST `/nodes/{node}/qemu/{vmid}/status/reboot` -> reboot
  - DELETE `/nodes/{node}/qemu/{vmid}` -> delete VM
  - POST `/nodes/{node}/qemu/{vmid}/clone` with form body -> clone
  - GET `/nodes/{node}/tasks/{upid}/status` -> task status
  - GET `/nodes/{node}/qemu/{vmid}/agent/network-get-interfaces` -> VM IPs (requires qemu-guest-agent)

**IMPORTANT: listTemplates is NOT a separate endpoint.** It calls the same `/nodes/{node}/qemu` and filters in the repository (not the API service). Remove `listTemplates` from the ProxmoxApiService interface. Templates are filtered from `listVms` results by `template == 1`.

Certificate handling (CORRECTED from review):
- For each call, build a per-request OkHttpClient via `CertificateValidator.buildPinnedClient(target.expectedFingerprint)`
- Pinning happens in the `X509TrustManager.checkServerTrusted()` during TLS handshake, BEFORE any auth headers are sent
- On fingerprint mismatch, the handshake throws `CertificateException` -> caught as `IOException` -> mapped to `AppError.ProxmoxApiError(0, "Certificate mismatch -- possible MITM")`
- NO trust-all client is used for authenticated requests. The trust-all probe is ONLY called during `CertificateValidator.probeFingerprint()` before the user has approved the cert.

Alternatively: cache the pinned client per host+fingerprint key to avoid rebuilding on every call. A `ConcurrentHashMap<String, OkHttpClient>` keyed on `"$host:$port:$fingerprint"` works.

Error mapping:
- 200 -> parse success
- 401 -> AppError.AuthenticationError("Invalid Proxmox token")
- 403 -> AppError.PermissionDenied("Insufficient Proxmox permissions")
- 404 -> AppError.ProxmoxApiError(404, "Resource not found")
- 500+ -> AppError.ProxmoxApiError(code, parsed error.message if available)
- IOException from cert mismatch -> AppError.ProxmoxApiError(0, "Certificate fingerprint mismatch")
- Other IOException -> AppError.NetworkError("Network: ${e.message}")

**Verify AppError variants exist**: before implementing, read `/root/OriDev/core/core-common/src/main/kotlin/dev/ori/core/common/error/AppError.kt` and confirm `AuthenticationError`, `NetworkError`, `PermissionDenied`, `ProxmoxApiError` all exist. If any are missing, add them first.

- [ ] **Step 6: Create ProxmoxNetworkModule (Hilt)**

Provides:
- Moshi instance (qualified `@ProxmoxMoshi` to avoid clash with core-ai's Moshi if present). Use:
  ```kotlin
  @Qualifier @Retention(AnnotationRetention.BINARY) annotation class ProxmoxMoshi
  ```
- @Binds ProxmoxApiServiceImpl -> ProxmoxApiService as Singleton

DO NOT create a `@ProxmoxHttpClient` qualifier. ProxmoxApiServiceImpl owns its internal cache of pinned OkHttpClients (keyed by `host:port:fingerprint`). There is NO shared OkHttpClient to inject from the module.

Note: core-ai's OkHttpClient is already scoped by `@ClaudeHttpClient` qualifier, so there is no unqualified `OkHttpClient` singleton in the app. No conflict.

- [ ] **Step 7: Write ProxmoxApiServiceImplTest with MockWebServer**

10 tests:
- `listNodes_success_returnsNodes`
- `listNodes_401_returnsAuthError`
- `listVms_success_filtersTemplates` (templates have template=1)
- `startVm_success_returnsUpid`
- `stopVm_success_returnsUpid`
- `cloneVm_withRequest_sendsFormBody`
- `getTaskStatus_running_returnsRunning`
- `getTaskStatus_failed_returnsError`
- `deleteVm_success_returnsUpid`
- `authHeader_includesPVEAPIToken` (verify the header format)

Note: MockWebServer uses plain HTTP, so cert pinning code paths aren't exercised here. Certificate validation tests happen in a separate unit test for CertificateValidator.

- [ ] **Step 8: Write CertificateValidatorTest**

3 tests:
- `computeFingerprint_deterministic` -- same cert produces same fingerprint
- `computeFingerprint_formatIsHexWithColons`
- `probeFingerprint_invalidHost_returnsNull`

- [ ] **Step 9: Run tests and commit**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew :core:core-network:test`
Message: `feat(core-network): add Proxmox REST API client with TOFU certificate pinning`

---

### Task 7.2: domain -- Proxmox Models, Repository Interface, Use Cases

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/model/ProxmoxNode.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/model/ProxmoxVm.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/model/ProxmoxVmStatus.kt` (enum)
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/ProxmoxRepository.kt`
- Create: 9 use case files in `domain/src/main/kotlin/dev/ori/domain/usecase/`
- Test: `domain/src/test/kotlin/dev/ori/domain/usecase/ProxmoxUseCaseTest.kt`

- [ ] **Step 1: Create domain models**

```kotlin
// ProxmoxNode.kt
package dev.ori.domain.model

data class ProxmoxNode(
    val id: Long,            // Room PK
    val name: String,        // friendly name
    val host: String,
    val port: Int = 8006,
    val tokenId: String,
    val tokenSecretRef: String,  // KeyStore alias
    val certFingerprint: String?, // null until user trusts
    val isOnline: Boolean = false,
    val nodeName: String? = null, // Proxmox cluster node name (e.g., "pve1")
    val cpuUsage: Double? = null,
    val memUsedBytes: Long? = null,
    val memTotalBytes: Long? = null,
)
```

```kotlin
// ProxmoxVm.kt
package dev.ori.domain.model

data class ProxmoxVm(
    val vmid: Int,
    val name: String,
    val nodeName: String,
    val status: ProxmoxVmStatus,
    val cpuUsage: Double? = null,
    val memUsedBytes: Long? = null,
    val memTotalBytes: Long? = null,
    val uptimeSeconds: Long? = null,
    val isTemplate: Boolean = false,
)
```

```kotlin
// ProxmoxVmStatus.kt
package dev.ori.domain.model

enum class ProxmoxVmStatus {
    RUNNING,
    STOPPED,
    PAUSED,
    UNKNOWN;

    companion object {
        fun fromString(raw: String): ProxmoxVmStatus = when (raw.lowercase()) {
            "running" -> RUNNING
            "stopped" -> STOPPED
            "paused" -> PAUSED
            else -> UNKNOWN
        }
    }
}
```

- [ ] **Step 2: Create ProxmoxRepository interface**

```kotlin
package dev.ori.domain.repository

import dev.ori.core.common.result.AppResult
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm
import kotlinx.coroutines.flow.Flow

interface ProxmoxRepository {
    // Node management (CRUD + connection)
    fun getNodes(): Flow<List<ProxmoxNode>>
    suspend fun addNode(
        name: String,
        host: String,
        port: Int,
        tokenId: String,
        tokenSecret: String,
        certFingerprint: String,
    ): AppResult<Long>
    suspend fun updateNode(node: ProxmoxNode)
    suspend fun deleteNode(node: ProxmoxNode)
    suspend fun probeCertificate(host: String, port: Int): AppResult<String>
    suspend fun refreshNodeStatus(nodeId: Long): AppResult<ProxmoxNode>

    // VM operations
    suspend fun getVms(nodeId: Long): AppResult<List<ProxmoxVm>>
    suspend fun getTemplates(nodeId: Long): AppResult<List<ProxmoxVm>>
    suspend fun startVm(nodeId: Long, vmid: Int): AppResult<String>
    suspend fun stopVm(nodeId: Long, vmid: Int): AppResult<String>
    suspend fun restartVm(nodeId: Long, vmid: Int): AppResult<String>
    suspend fun deleteVm(nodeId: Long, vmid: Int): AppResult<String>
    suspend fun cloneVm(
        nodeId: Long,
        templateVmid: Int,
        newVmid: Int,
        newName: String,
        fullClone: Boolean,
    ): AppResult<String>

    // Task polling (async operations return UPID)
    suspend fun waitForTask(nodeId: Long, upid: String, timeoutSeconds: Long = 120): AppResult<Unit>

    // SSH readiness polling (after clone + start)
    suspend fun pollVmSshReady(nodeId: Long, vmid: Int, timeoutSeconds: Long = 60): AppResult<String> // returns IP
}
```

- [ ] **Step 3: Create 9 use cases**

All follow the standard pattern: `@Inject constructor(private val repository: ProxmoxRepository)`, `suspend operator fun invoke(...)`.

- `GetProxmoxNodesUseCase` -> `Flow<List<ProxmoxNode>>`
- `GetProxmoxVmsUseCase` -> `suspend (nodeId) -> AppResult<List<ProxmoxVm>>`
- `StartVmUseCase`, `StopVmUseCase`, `RestartVmUseCase`, `DeleteVmUseCase` -> `suspend (nodeId, vmid) -> AppResult<String>`
- `GetProxmoxTemplatesUseCase` -> `suspend (nodeId) -> AppResult<List<ProxmoxVm>>`
- `CloneVmUseCase` -> `suspend (nodeId, templateVmid, newVmid, newName, fullClone) -> AppResult<String>`
- `PollVmSshUseCase` -> `suspend (nodeId, vmid, timeoutSeconds) -> AppResult<String>`

- [ ] **Step 4: Write tests**

`ProxmoxUseCaseTest` with 6+ tests covering use case delegation with fake repository.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :domain:test`
Message: `feat(domain): add Proxmox models, repository interface, and use cases`

---

### Task 7.3: data -- ProxmoxRepositoryImpl + Mapper

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/mapper/ProxmoxMapper.kt`
- Create: `data/src/main/kotlin/dev/ori/data/repository/ProxmoxRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/di/ProxmoxBindingsModule.kt`
- Modify: `data/build.gradle.kts` (verify core-network already a dep, no changes needed)
- Test: `data/src/test/kotlin/dev/ori/data/repository/ProxmoxRepositoryImplTest.kt`

- [ ] **Step 1: Create ProxmoxMapper**

Bidirectional conversions. Key detail: `ProxmoxVmDto.name` is nullable (the Proxmox API may omit name), but `ProxmoxVm.name` is non-null -- coerce with a default.

```kotlin
package dev.ori.data.mapper

import dev.ori.core.network.proxmox.model.ProxmoxNodeDto
import dev.ori.core.network.proxmox.model.ProxmoxVmDto
import dev.ori.data.entity.ProxmoxNodeEntity
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.model.ProxmoxVmStatus

fun ProxmoxNodeEntity.toDomain(
    isOnline: Boolean = false,
    nodeName: String? = null,
    cpuUsage: Double? = null,
    memUsed: Long? = null,
    memTotal: Long? = null,
) = ProxmoxNode(
    id = id,
    name = name,
    host = host,
    port = port,
    tokenId = tokenId,
    tokenSecretRef = tokenSecretRef,
    certFingerprint = certFingerprint,
    isOnline = isOnline,
    nodeName = nodeName,
    cpuUsage = cpuUsage,
    memUsedBytes = memUsed,
    memTotalBytes = memTotal,
)

fun ProxmoxNode.toEntity() = ProxmoxNodeEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    tokenId = tokenId,
    tokenSecretRef = tokenSecretRef,
    certFingerprint = certFingerprint,
    lastSyncAt = System.currentTimeMillis(),
)

fun ProxmoxVmDto.toDomain(nodeName: String) = ProxmoxVm(
    vmid = vmid,
    name = name ?: "vm-$vmid",  // Proxmox API may omit name; fallback to "vm-{vmid}"
    nodeName = nodeName,
    status = ProxmoxVmStatus.fromString(status),
    cpuUsage = cpu,
    memUsedBytes = mem,
    memTotalBytes = maxmem,
    uptimeSeconds = uptime,
    isTemplate = template == 1,
)
```

Write `ProxmoxMapperTest` with 4 tests:
- `vmDto_withNullName_coercesToVmPrefix`
- `vmDto_withStatus_mapsToEnum` (all status values)
- `node_roundTrip_preservesFields`
- `vmDto_templateFlag_mapsToIsTemplate`

- [ ] **Step 2: Create ProxmoxRepositoryImpl**

@Singleton. Injects:
- `ProxmoxNodeDao` (Room)
- `ProxmoxApiService` (from core-network)
- `CredentialStore` (from domain, backed by KeyStoreManager)

Implementation notes:
- `getNodes()`: combines Room Flow with in-memory runtime state (online status, CPU/mem); use `combine()` + `MutableStateFlow<Map<Long, NodeRuntimeState>>`
- `addNode()`: store token secret via `credentialStore.storePassword(tokenSecretRef, tokenSecret.toCharArray())`, insert entity
- `probeCertificate()`: delegates to `CertificateValidator.probeFingerprint()`
- `startVm/stopVm/etc`: load credentials, call API, return task UPID
- `waitForTask()`: poll `getTaskStatus` every 1 second, timeout after 120s
- `pollVmSshReady()`: after a clone+start, poll the VM's SSH port (22) until TCP connection succeeds; get IP from VM agent API (`nodes/{node}/qemu/{vmid}/agent/network-get-interfaces`)

Token alias pattern: `"proxmox_token_<nodeId>"`

- [ ] **Step 3: Create ProxmoxBindingsModule**

Hilt @Binds ProxmoxRepositoryImpl -> ProxmoxRepository as Singleton.

- [ ] **Step 4: Write ProxmoxRepositoryImplTest**

5+ tests with fake DAO and fake API service:
- `addNode_storesTokenAndInsertsEntity`
- `startVm_loadsCredentialsAndCallsApi`
- `getVms_filtersOutTemplates`
- `probeCertificate_delegatesToValidator`
- `waitForTask_pollsUntilComplete`

- [ ] **Step 5: Commit**

Run: `./gradlew :data:test`
Message: `feat(data): add Proxmox repository implementation with credential storage`

---

### Task 7.4: feature-proxmox -- UiState, ViewModel, Dashboard

**Files:**
- Modify: `feature-proxmox/build.gradle.kts` (verify deps: core-network, compose-material-icons-extended, useJUnitPlatform)
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/ProxmoxDashboardUiState.kt`
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/ProxmoxDashboardViewModel.kt`
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/NodeCard.kt`
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/VmCard.kt`
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/ProxmoxDashboardScreen.kt`

- [ ] **Step 1: Update feature-proxmox/build.gradle.kts**

Read current file first. REMOVE any existing direct dependencies on:
- `libs.okhttp`, `libs.okhttp.logging`
- `libs.moshi`, `libs.moshi.kotlin`, `libs.moshi.codegen`
- Retrofit (if present from scaffold)

These are dead deps from the scaffold -- feature-proxmox talks to the API via domain use cases only, not directly.

Ensure dependencies include ONLY:
- `project(":core:core-common")`, `project(":core:core-ui")`, `project(":domain")`
- `libs.compose.material.icons.extended`
- Standard Compose + Hilt + ViewModel deps (already present)

Do NOT add `project(":core:core-network")` -- ViewModel uses only domain interfaces.

Add `tasks.withType<Test> { useJUnitPlatform() }` if missing.

Remove .gitkeep.

- [ ] **Step 2: Create UiState**

```kotlin
data class ProxmoxDashboardUiState(
    val nodes: List<ProxmoxNode> = emptyList(),
    val selectedNodeId: Long? = null,
    val vms: List<ProxmoxVm> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddNodeSheet: Boolean = false,
    val showCertificateDialog: CertificateTrustRequest? = null,
    val vmActionInProgress: Int? = null, // vmid being acted on
)

data class CertificateTrustRequest(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val pendingAddData: AddNodePending, // for resuming add-node flow after user trusts
)

data class AddNodePending(
    val name: String,
    val host: String,
    val port: Int,
    val tokenId: String,
    val tokenSecret: String,
)

sealed class ProxmoxEvent {
    data class SelectNode(val nodeId: Long) : ProxmoxEvent()
    data object ShowAddNodeSheet : ProxmoxEvent()
    data object HideAddNodeSheet : ProxmoxEvent()
    data class ProbeAndAddNode(val pending: AddNodePending) : ProxmoxEvent()
    data class ConfirmTrustCertificate(val request: CertificateTrustRequest) : ProxmoxEvent()
    data class RejectCertificate(val request: CertificateTrustRequest) : ProxmoxEvent()
    data class DeleteNode(val node: ProxmoxNode) : ProxmoxEvent()
    data class StartVm(val nodeId: Long, val vmid: Int) : ProxmoxEvent()
    data class StopVm(val nodeId: Long, val vmid: Int) : ProxmoxEvent()
    data class RestartVm(val nodeId: Long, val vmid: Int) : ProxmoxEvent()
    data class DeleteVm(val nodeId: Long, val vmid: Int) : ProxmoxEvent()
    data object RefreshVms : ProxmoxEvent()
    data object ClearError : ProxmoxEvent()
}
```

- [ ] **Step 3: Create ProxmoxDashboardViewModel**

@HiltViewModel. Injects use cases.

On init: collect `GetProxmoxNodesUseCase()` flow into state. Select first node by default.

Handle events:
- `SelectNode`: set selectedNodeId, load VMs for that node
- `ShowAddNodeSheet`: show sheet
- `ProbeAndAddNode`: call `probeCertificate`, on success set `showCertificateDialog` with fingerprint
- `ConfirmTrustCertificate`: call `addNode()` with the fingerprint, dismiss dialogs
- `StartVm/StopVm/RestartVm/DeleteVm`: set vmActionInProgress, call use case, refresh on success
- `RefreshVms`: reload VMs for selected node

- [ ] **Step 4: Create NodeCard composable**

Horizontal card showing node name, host:port, online status (StatusDot), CPU bar, RAM bar. Selected state has indigo border. Click -> SelectNode event.

- [ ] **Step 5: Create VmCard composable**

Card showing: vmid, VM name, status badge (color-coded), CPU/RAM usage, action buttons (Start/Stop/Restart/Delete based on status).

- [ ] **Step 6: Create ProxmoxDashboardScreen**

Scaffold with OriDevTopBar("Proxmox Manager"). FAB for add node.
Content: horizontal scroll of NodeCards at top, LazyColumn of VmCards below.
AddNodeSheet (modal bottom sheet) for adding new node.
CertificateTrustDialog for TOFU approval.
Snackbar for errors.

- [ ] **Step 7: Commit**

Message: `feat(proxmox): add ProxmoxDashboardScreen with node/VM cards and lifecycle actions`

---

### Task 7.5: feature-proxmox -- Add Node Sheet + Certificate Trust Dialog + Create VM Wizard

**Files:**
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/AddNodeSheet.kt`
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/CertificateTrustDialog.kt`
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/CreateVmWizard.kt`
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/ui/CreateVmWizardViewModel.kt`

- [ ] **Step 1: Create AddNodeSheet**

ModalBottomSheet form:
- Name (TextField)
- Host (TextField, validates via isValidHost())
- Port (TextField, default 8006)
- Token ID (TextField, placeholder "user@pam!tokenname")
- Token Secret (TextField, password visibility toggle)
- "Probe and Add" button -> triggers ProbeAndAddNode event

- [ ] **Step 2: Create CertificateTrustDialog**

AlertDialog:
- Title: "Untrusted Certificate"
- Content: host, port, fingerprint formatted with line breaks (e.g., groups of 4 hex chars)
- Warning text: "This is a self-signed certificate. Only trust if you recognize the fingerprint."
- Buttons: "Trust and Add" (indigo) / "Cancel"

- [ ] **Step 3: Create CreateVmWizardViewModel**

@HiltViewModel. Injects GetProxmoxTemplatesUseCase, CloneVmUseCase, PollVmSshUseCase, ConnectionRepository (to auto-add SSH profile).

State:
```kotlin
data class CreateVmWizardState(
    val nodeId: Long,
    val step: WizardStep = WizardStep.SELECT_TEMPLATE,
    val templates: List<ProxmoxVm> = emptyList(),
    val selectedTemplate: ProxmoxVm? = null,
    val newVmid: Int = 100,
    val newName: String = "",
    val fullClone: Boolean = true,
    // Network configuration (Step 3)
    val useStaticIp: Boolean = false,
    val staticIp: String = "",
    val gateway: String = "",
    val bridge: String = "vmbr0",
    // SSH credentials for auto-created ServerProfile (needed for auto-connect)
    val sshUsername: String = "root",
    val sshPassword: String = "",
    // Progress states
    val cloneInProgress: Boolean = false,
    val autoConnectInProgress: Boolean = false,
    val resultSshProfileId: Long? = null,
    val error: String? = null,
)

enum class WizardStep {
    SELECT_TEMPLATE,   // Step 1: pick a template VM
    CONFIGURE,         // Step 2: VM ID, name, clone mode
    NETWORK,           // Step 3: bridge, IP mode (DHCP/static), SSH credentials
    REVIEW,            // Step 4: summary + Clone & Start button
    CLONING,           // Progress state: waiting for clone task
    CONNECTING,        // Progress state: polling SSH readiness
    DONE,              // Success: profile id + Open Terminal button
}
```

NOTE: CreateVmWizardViewModel MUST inject `SavedStateHandle` to read `nodeId` argument from navigation:
```kotlin
@HiltViewModel
class CreateVmWizardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    // ... use cases
) : ViewModel() {
    private val nodeId: Long = savedStateHandle["nodeId"]
        ?: error("nodeId required")
    // ...
}
```

On Clone & Start:
1. Set step = CLONING
2. Call CloneVmUseCase -> returns UPID
3. Poll task status until done
4. Set step = CONNECTING
5. Determine VM IP:
   - If `useStaticIp` was set in Step 3 -> use `staticIp` directly (works without guest agent)
   - Else -> call `PollVmSshUseCase` which queries the guest agent `/agent/network-get-interfaces` (requires qemu-guest-agent in template)
6. Poll SSH port 22 until TCP connection succeeds (max 60s timeout)
7. Create a new ServerProfile via `ConnectionRepository.saveProfile()`:
   ```kotlin
   val credentialAlias = "proxmox_vm_${newVmid}"
   credentialStore.storePassword(credentialAlias, state.sshPassword.toCharArray())
   ConnectionRepository.saveProfile(
       ServerProfile(
           name = state.newName,
           host = vmIp,
           port = 22,
           protocol = Protocol.SSH,
           username = state.sshUsername,
           authMethod = AuthMethod.PASSWORD,
           credentialRef = credentialAlias,
           sshKeyType = null,
           startupCommand = null,
           projectDirectory = null,
           claudeCodeModel = null,
           claudeMdPath = null,
       )
   )
   ```
8. Set step = DONE with the returned profileId
9. User taps "Open Terminal" -> navigate to terminal with that profile id

**Rollback handling**: if cloning succeeds but SSH polling times out, the VM is left running. The DONE step should show:
- Success message (if SSH connected)
- OR warning message "VM cloned but SSH not reachable. You can manually connect later via the Connection Manager." with a "View VM" link. Do NOT automatically delete the VM -- user may want to debug it.

- [ ] **Step 4: Create CreateVmWizard composable**

Multi-step UI with step indicator at top (4 user-facing steps + 3 progress states):
- **Step 1 (SELECT_TEMPLATE)**: Grid of template cards, Next enabled when template selected
- **Step 2 (CONFIGURE)**: Form with VM ID (numeric), name, clone mode (full/linked radio)
- **Step 3 (NETWORK)**: Bridge selector (default vmbr0), IP mode toggle (DHCP/Static). If Static: IP TextField, Gateway TextField. SSH credentials (username, password). Next enabled when credentials filled.
- **Step 4 (REVIEW)**: Summary table (template, VM ID, name, clone mode, bridge, IP, username) + "Clone & Start" button
- **Progress (CLONING)**: Loading with status text "Cloning template..."
- **Progress (CONNECTING)**: "Waiting for SSH..." with spinner
- **Progress (DONE)**: Success OR warning message + "Open Terminal" button

- [ ] **Step 5: Commit**

Message: `feat(proxmox): add node wizard, certificate trust dialog, and create VM wizard`

---

### Task 7.6: Navigation + Integration + Tests + CI Green

**Files:**
- Create: `feature-proxmox/src/main/kotlin/dev/ori/feature/proxmox/navigation/ProxmoxNavigation.kt`
- Modify: `app/src/main/kotlin/dev/ori/app/navigation/OriDevNavHost.kt`
- Modify: `app/src/main/kotlin/dev/ori/app/ui/OriDevApp.kt` (add Proxmox to bottom nav if appropriate, OR keep as sub-nav)
- Test: `feature-proxmox/src/test/kotlin/dev/ori/feature/proxmox/ui/ProxmoxDashboardViewModelTest.kt`

- [ ] **Step 1: Create ProxmoxNavigation**

```kotlin
const val PROXMOX_ROUTE = "proxmox"
const val PROXMOX_CREATE_VM_ROUTE = "proxmox/create-vm/{nodeId}"

fun NavGraphBuilder.proxmoxDashboardScreen(
    onNavigateToTerminal: (profileId: Long) -> Unit,
    onNavigateToCreateVm: (nodeId: Long) -> Unit,
) {
    composable(PROXMOX_ROUTE) {
        ProxmoxDashboardScreen(
            onNavigateToTerminal = onNavigateToTerminal,
            onNavigateToCreateVm = onNavigateToCreateVm,
        )
    }
    composable(
        route = PROXMOX_CREATE_VM_ROUTE,
        arguments = listOf(navArgument("nodeId") { type = NavType.LongType }),
    ) {
        CreateVmWizard(onNavigateToTerminal = onNavigateToTerminal)
    }
}

fun NavController.navigateToProxmox() {
    navigate(PROXMOX_ROUTE) { launchSingleTop = true }
}
```

- [ ] **Step 2: Update OriDevNavHost**

Register `proxmoxDashboardScreen()` with callbacks. The terminal navigation callback should wire through the existing `navigateToTerminal(profileId)` function.

- [ ] **Step 3: Decide on bottom nav integration**

Proxmox can either be:
- **Added to bottom nav** (6 items total -- tight but feasible)
- **Sub-screen from Connections** (accessed via overflow menu or banner)

SIMPLER: add as 5th nav item. Rearrange: Connections, Files, Terminal, Transfers, Proxmox. Remove Settings from bottom nav (it moves to a top-bar icon or drawer).

Alternative (LESS INVASIVE): Keep bottom nav as-is, add a "Proxmox" entry point from the Connection Manager screen (add a "+" action showing "Add Proxmox Node").

Go with the ALTERNATIVE (less invasive): Add an IconButton to ConnectionListScreen top bar that navigates to Proxmox route. Document in CLAUDE.md that Proxmox is accessed via Connection Manager.

- [ ] **Step 4: Write ProxmoxDashboardViewModelTest**

8 tests:
- init_loadsNodesFromFlow
- selectNode_loadsVms
- probeAndAddNode_success_showsCertDialog
- confirmTrustCertificate_callsAddNode
- startVm_callsUseCaseAndRefreshes
- stopVm_failure_setsError
- refreshVms_reloadsList
- deleteNode_callsUseCase

- [ ] **Step 4b: Write CreateVmWizardViewModelTest**

The wizard has the most complex state machine in Phase 7 -- it needs dedicated tests.

8 tests:
- `init_withNodeId_loadsTemplates`
- `selectTemplate_advancesToConfigure`
- `configure_toNetwork_preservesFields`
- `network_toReview_preservesCredentials`
- `cloneAndStart_success_advancesThroughStates` (CLONING -> CONNECTING -> DONE with profileId)
- `cloneAndStart_taskTimeout_setsError`
- `cloneAndStart_sshPollTimeout_showsWarningMessage_butStillReachesDone`
- `cloneAndStart_withStaticIp_skipsGuestAgentQuery`

Use fake repository + fake ConnectionRepository. Use SavedStateHandle with "nodeId" = 1L for the VM init.

Use MockK for use cases.

- [ ] **Step 5: Run all checks**

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew detekt
./gradlew test
./gradlew assembleDebug
```

Fix any violations.

- [ ] **Step 6: Commit and push**

Message: `feat(proxmox): add navigation, bottom nav integration, and ViewModel tests`
Then: `git push origin master`

- [ ] **Step 7: Monitor CI until green**

`gh run list --branch master --limit 5` until Build & Test = success.
If failures, `gh run view <id> --log-failed`, fix, commit, push, repeat.

**DO NOT report DONE until CI is GREEN.**

---

## Phase 7 Completion Checklist

- [ ] `core-network`: ProxmoxApiService + Impl with OkHttp, CertificateValidator (TOFU), data models, Hilt module, 10+ tests
- [ ] `domain`: ProxmoxNode/Vm/VmStatus models, ProxmoxRepository interface, 9 use cases, tests
- [ ] `data`: ProxmoxRepositoryImpl with credential storage, mapper, Hilt binding, tests
- [ ] `feature-proxmox`: ProxmoxDashboardScreen, NodeCard, VmCard, AddNodeSheet, CertificateTrustDialog, CreateVmWizard, ViewModel, navigation, tests
- [ ] `app`: ConnectionListScreen has Proxmox entry point, OriDevNavHost registers routes
- [ ] All tests pass, detekt clean, build succeeds, CI GREEN

## Known Limitations (Documented)

1. **Certificate pinning is TOFU only**: No CA validation. User approves the cert on first connect. Fingerprint mismatch blocks subsequent connects with a clear error.
2. **Task polling is fixed 1s interval**: No exponential backoff. Long-running tasks (>120s default timeout) require manual retry.
3. **SSH auto-connect requires guest agent**: Getting the VM IP via `/agent/network-get-interfaces` requires qemu-guest-agent to be installed in the VM. Without it, auto-connect times out and user must add the SSH profile manually.
4. **No VM console access**: Proxmox VNC/Spice console is deferred to a follow-up (would require WebSocket + VNC client).
5. **No multi-cluster support**: Each `ProxmoxNodeEntity` represents a single node (can be the cluster master). Real cluster-wide operations (failover, migration) deferred.
