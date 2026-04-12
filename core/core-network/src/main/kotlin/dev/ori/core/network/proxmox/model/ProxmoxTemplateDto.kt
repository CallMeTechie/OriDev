package dev.ori.core.network.proxmox.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProxmoxTemplateDto(
    val vmid: Int,
    val name: String,
    val node: String,
)
