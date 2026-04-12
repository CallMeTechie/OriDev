package dev.ori.core.network.proxmox.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProxmoxNodeDto(
    val node: String,
    val status: String,
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
