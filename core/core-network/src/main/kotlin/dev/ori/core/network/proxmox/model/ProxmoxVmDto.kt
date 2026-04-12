package dev.ori.core.network.proxmox.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProxmoxVmDto(
    val vmid: Int,
    val name: String? = null,
    val status: String,
    val cpu: Double? = null,
    val cpus: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val uptime: Long? = null,
    val template: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ProxmoxVmListResponse(val data: List<ProxmoxVmDto>)

@JsonClass(generateAdapter = true)
data class ProxmoxVmStatusResponse(val data: ProxmoxVmDto)
