package dev.ori.core.network.proxmox.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProxmoxTaskResponse(
    val data: String,
)

@JsonClass(generateAdapter = true)
data class ProxmoxTaskStatusDto(
    val status: String,
    val exitstatus: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProxmoxTaskStatusResponse(val data: ProxmoxTaskStatusDto)
