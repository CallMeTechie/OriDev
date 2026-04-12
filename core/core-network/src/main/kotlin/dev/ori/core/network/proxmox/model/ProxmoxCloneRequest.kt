package dev.ori.core.network.proxmox.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProxmoxCloneRequest(
    val newid: Int,
    val name: String,
    val full: Int = 1,
    val target: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProxmoxErrorResponse(
    val errors: Map<String, String>? = null,
    val message: String? = null,
)
