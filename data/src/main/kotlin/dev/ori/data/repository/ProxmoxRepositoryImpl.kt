package dev.ori.data.repository

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.core.common.result.getAppError
import dev.ori.core.network.proxmox.CertificateValidator
import dev.ori.core.network.proxmox.FingerprintProbeResult
import dev.ori.core.network.proxmox.ProxmoxApiService
import dev.ori.core.network.proxmox.ProxmoxTarget
import dev.ori.core.network.proxmox.model.ProxmoxCloneRequest
import dev.ori.data.dao.ProxmoxNodeDao
import dev.ori.data.entity.ProxmoxNodeEntity
import dev.ori.data.mapper.toDomain
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.repository.CredentialStore
import dev.ori.domain.repository.ProxmoxRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TOKEN_ALIAS_PREFIX = "proxmox_token_"
private const val TASK_POLL_INTERVAL_MS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L

/** Runtime state not persisted to Room (live status + metrics). */
internal data class NodeRuntimeState(
    val isOnline: Boolean = false,
    val nodeName: String? = null,
    val cpuUsage: Double? = null,
    val memUsed: Long? = null,
    val memTotal: Long? = null,
)

@Singleton
@Suppress("TooManyFunctions")
class ProxmoxRepositoryImpl @Inject constructor(
    private val nodeDao: ProxmoxNodeDao,
    private val apiService: ProxmoxApiService,
    private val credentialStore: CredentialStore,
) : ProxmoxRepository {

    private val runtimeState = MutableStateFlow<Map<Long, NodeRuntimeState>>(emptyMap())

    override fun getNodes(): Flow<List<ProxmoxNode>> =
        nodeDao.getAll().combine(runtimeState) { entities, runtime ->
            entities.map { entity ->
                val rt = runtime[entity.id] ?: NodeRuntimeState()
                entity.toDomain(
                    isOnline = rt.isOnline,
                    nodeName = rt.nodeName,
                    cpuUsage = rt.cpuUsage,
                    memUsed = rt.memUsed,
                    memTotal = rt.memTotal,
                )
            }
        }

    override suspend fun addNode(
        name: String,
        host: String,
        port: Int,
        tokenId: String,
        tokenSecret: String,
        certFingerprint: String,
    ): AppResult<Long> = runCatching {
        // Insert first to obtain a node id, then update the secret ref pointing at that id.
        val placeholderRef = "${TOKEN_ALIAS_PREFIX}pending"
        val provisional = ProxmoxNodeEntity(
            name = name,
            host = host,
            port = port,
            tokenId = tokenId,
            tokenSecretRef = placeholderRef,
            certFingerprint = certFingerprint,
            lastSyncAt = System.currentTimeMillis(),
        )
        val id = nodeDao.insert(provisional)
        val alias = "$TOKEN_ALIAS_PREFIX$id"
        credentialStore.storePassword(alias, tokenSecret.toCharArray())
        nodeDao.update(provisional.copy(id = id, tokenSecretRef = alias))
        id
    }.fold(
        onSuccess = { appSuccess(it) },
        onFailure = { appFailure(AppError.StorageError("Failed to add node: ${it.message}", it)) },
    )

    override suspend fun updateNode(node: ProxmoxNode) {
        val existing = nodeDao.getById(node.id) ?: return
        nodeDao.update(
            existing.copy(
                name = node.name,
                host = node.host,
                port = node.port,
                tokenId = node.tokenId,
                certFingerprint = node.certFingerprint,
                lastSyncAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteNode(node: ProxmoxNode) {
        credentialStore.deleteCredential(node.tokenSecretRef)
        nodeDao.delete(
            ProxmoxNodeEntity(
                id = node.id,
                name = node.name,
                host = node.host,
                port = node.port,
                tokenId = node.tokenId,
                tokenSecretRef = node.tokenSecretRef,
                certFingerprint = node.certFingerprint,
            ),
        )
        runtimeState.value = runtimeState.value - node.id
    }

    override suspend fun probeCertificate(host: String, port: Int): AppResult<String> =
        when (val result = CertificateValidator.probeFingerprint(host, port)) {
            is FingerprintProbeResult.Success -> appSuccess(result.fingerprint)
            is FingerprintProbeResult.Failure -> appFailure(AppError.NetworkError(result.reason))
        }

    override suspend fun refreshNodeStatus(nodeId: Long): AppResult<ProxmoxNode> {
        val entity = nodeDao.getById(nodeId)
            ?: return appFailure(AppError.StorageError("Node not found: $nodeId"))
        val target = buildTarget(entity) ?: return appFailure(
            AppError.AuthenticationError("Missing credentials for node $nodeId"),
        )
        val listResult = apiService.listNodes(target)
        val dtos = listResult.getOrElse { throwable ->
            val appErr = listResult.getAppError() ?: AppError.NetworkError(throwable.message ?: "unknown")
            runtimeState.value = runtimeState.value + (nodeId to NodeRuntimeState(isOnline = false))
            return appFailure(appErr)
        }
        val first = dtos.firstOrNull()
        val newRuntime = NodeRuntimeState(
            isOnline = true,
            nodeName = first?.node,
            cpuUsage = first?.cpu,
            memUsed = first?.mem,
            memTotal = first?.maxmem,
        )
        runtimeState.value = runtimeState.value + (nodeId to newRuntime)
        return appSuccess(
            entity.toDomain(
                isOnline = newRuntime.isOnline,
                nodeName = newRuntime.nodeName,
                cpuUsage = newRuntime.cpuUsage,
                memUsed = newRuntime.memUsed,
                memTotal = newRuntime.memTotal,
            ),
        )
    }

    override suspend fun getVms(nodeId: Long): AppResult<List<ProxmoxVm>> =
        listVmsInternal(nodeId) { vms -> vms.filter { !it.isTemplate } }

    override suspend fun getTemplates(nodeId: Long): AppResult<List<ProxmoxVm>> =
        listVmsInternal(nodeId) { vms -> vms.filter { it.isTemplate } }

    private suspend fun listVmsInternal(
        nodeId: Long,
        filter: (List<ProxmoxVm>) -> List<ProxmoxVm>,
    ): AppResult<List<ProxmoxVm>> {
        val entity = nodeDao.getById(nodeId)
            ?: return appFailure(AppError.StorageError("Node not found: $nodeId"))
        val target = buildTarget(entity) ?: return appFailure(
            AppError.AuthenticationError("Missing credentials for node $nodeId"),
        )
        val nodeNameResolved = runtimeState.value[nodeId]?.nodeName ?: resolveNodeName(target)
            ?: return appFailure(AppError.NetworkError("Unable to resolve Proxmox node name"))
        return apiService.listVms(target, nodeNameResolved).map { dtos ->
            filter(dtos.map { it.toDomain(nodeNameResolved) })
        }
    }

    private suspend fun resolveNodeName(target: ProxmoxTarget): String? =
        apiService.listNodes(target).getOrNull()?.firstOrNull()?.node

    override suspend fun startVm(nodeId: Long, vmid: Int): AppResult<String> =
        withNodeAndTarget(nodeId) { _, target, nodeName ->
            apiService.startVm(target, nodeName, vmid)
        }

    override suspend fun stopVm(nodeId: Long, vmid: Int): AppResult<String> =
        withNodeAndTarget(nodeId) { _, target, nodeName ->
            apiService.stopVm(target, nodeName, vmid)
        }

    override suspend fun restartVm(nodeId: Long, vmid: Int): AppResult<String> =
        withNodeAndTarget(nodeId) { _, target, nodeName ->
            apiService.rebootVm(target, nodeName, vmid)
        }

    override suspend fun deleteVm(nodeId: Long, vmid: Int): AppResult<String> =
        withNodeAndTarget(nodeId) { _, target, nodeName ->
            apiService.deleteVm(target, nodeName, vmid)
        }

    override suspend fun cloneVm(
        nodeId: Long,
        templateVmid: Int,
        newVmid: Int,
        newName: String,
        fullClone: Boolean,
    ): AppResult<String> = withNodeAndTarget(nodeId) { _, target, nodeName ->
        apiService.cloneVm(
            target,
            nodeName,
            templateVmid,
            ProxmoxCloneRequest(
                newid = newVmid,
                name = newName,
                full = if (fullClone) 1 else 0,
            ),
        )
    }

    override suspend fun waitForTask(
        nodeId: Long,
        upid: String,
        timeoutSeconds: Long,
    ): AppResult<Unit> {
        val entity = nodeDao.getById(nodeId)
            ?: return appFailure(AppError.StorageError("Node not found: $nodeId"))
        val target = buildTarget(entity) ?: return appFailure(
            AppError.AuthenticationError("Missing credentials for node $nodeId"),
        )
        val nodeName = runtimeState.value[nodeId]?.nodeName ?: resolveNodeName(target)
            ?: return appFailure(AppError.NetworkError("Unable to resolve Proxmox node name"))

        val timeoutMs = timeoutSeconds * MILLIS_PER_SECOND
        val result = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val statusResult = apiService.getTaskStatus(target, nodeName, upid)
                val status = statusResult.getOrNull()
                if (status != null && status.status != "running") {
                    return@withTimeoutOrNull status
                }
                if (status == null) {
                    // propagate the failure
                    return@withTimeoutOrNull null
                }
                delay(TASK_POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
        return when {
            result == null -> appFailure(AppError.NetworkError("Task $upid timed out after ${timeoutSeconds}s"))
            result.exitstatus != null && result.exitstatus != "OK" ->
                appFailure(AppError.ProxmoxApiError(statusCode = 0, message = "Task failed: ${result.exitstatus}"))
            else -> appSuccess(Unit)
        }
    }

    @Suppress("UnusedParameter")
    override suspend fun pollVmSshReady(
        nodeId: Long,
        vmid: Int,
        timeoutSeconds: Long,
    ): AppResult<String> =
        // pollVmSshReady not yet implemented -- static IP required.
        // Guest agent polling is deferred; the caller should use a static IP in the wizard.
        appFailure(AppError.NetworkError("Guest agent polling deferred; use static IP in wizard"))

    // --- helpers -------------------------------------------------------------

    private suspend fun withNodeAndTarget(
        nodeId: Long,
        block: suspend (ProxmoxNodeEntity, ProxmoxTarget, String) -> AppResult<String>,
    ): AppResult<String> {
        val entity = nodeDao.getById(nodeId)
            ?: return appFailure(AppError.StorageError("Node not found: $nodeId"))
        val target = buildTarget(entity) ?: return appFailure(
            AppError.AuthenticationError("Missing credentials for node $nodeId"),
        )
        val nodeName = runtimeState.value[nodeId]?.nodeName ?: resolveNodeName(target)
            ?: return appFailure(AppError.NetworkError("Unable to resolve Proxmox node name"))
        return block(entity, target, nodeName)
    }

    private suspend fun buildTarget(entity: ProxmoxNodeEntity): ProxmoxTarget? {
        val secret = credentialStore.getPassword(entity.tokenSecretRef) ?: return null
        val fingerprint = entity.certFingerprint ?: return null
        return try {
            ProxmoxTarget(
                host = entity.host,
                port = entity.port,
                tokenId = entity.tokenId,
                tokenSecret = String(secret),
                expectedFingerprint = fingerprint,
            )
        } finally {
            secret.fill('\u0000')
        }
    }
}
