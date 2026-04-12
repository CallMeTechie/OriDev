package dev.ori.domain.model

data class ProxmoxNode(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PROXMOX_PORT,
    val tokenId: String,
    val tokenSecretRef: String,
    val certFingerprint: String?,
    val isOnline: Boolean = false,
    val nodeName: String? = null,
    val cpuUsage: Double? = null,
    val memUsedBytes: Long? = null,
    val memTotalBytes: Long? = null,
) {
    companion object {
        const val DEFAULT_PROXMOX_PORT = 8006
    }
}
