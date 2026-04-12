package dev.ori.domain.repository

import dev.ori.core.common.result.AppResult
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface ProxmoxRepository {
    // Node management
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

    // Task polling
    suspend fun waitForTask(nodeId: Long, upid: String, timeoutSeconds: Long = DEFAULT_TASK_TIMEOUT): AppResult<Unit>

    // SSH readiness polling
    suspend fun pollVmSshReady(
        nodeId: Long,
        vmid: Int,
        timeoutSeconds: Long = DEFAULT_SSH_POLL_TIMEOUT,
    ): AppResult<String>

    companion object {
        const val DEFAULT_TASK_TIMEOUT = 120L
        const val DEFAULT_SSH_POLL_TIMEOUT = 60L
    }
}
