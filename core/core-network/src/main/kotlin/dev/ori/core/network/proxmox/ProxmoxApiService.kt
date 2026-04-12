package dev.ori.core.network.proxmox

import dev.ori.core.common.result.AppResult
import dev.ori.core.network.proxmox.model.ProxmoxCloneRequest
import dev.ori.core.network.proxmox.model.ProxmoxNodeDto
import dev.ori.core.network.proxmox.model.ProxmoxTaskStatusDto
import dev.ori.core.network.proxmox.model.ProxmoxVmDto

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

    /** Template operations -- templates filtered from listVms() in repository. */
    suspend fun cloneVm(
        target: ProxmoxTarget,
        node: String,
        templateVmid: Int,
        request: ProxmoxCloneRequest,
    ): AppResult<String>

    /** Task status (for async operations) */
    suspend fun getTaskStatus(
        target: ProxmoxTarget,
        node: String,
        upid: String,
    ): AppResult<ProxmoxTaskStatusDto>

    companion object {
        const val PROXMOX_API_PATH = "/api2/json"
    }
}

/**
 * Connection target for a Proxmox API call. Includes the stored fingerprint so the
 * API client can build a per-request pinned OkHttpClient.
 */
data class ProxmoxTarget(
    val host: String,
    val port: Int = DEFAULT_PORT,
    val tokenId: String,
    val tokenSecret: String,
    val expectedFingerprint: String,
) {
    companion object {
        const val DEFAULT_PORT = 8006
    }
}
