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
