package dev.ori.domain.model

data class TerminalTab(
    val id: String,
    val profileId: Long,
    val serverName: String,
    val shellId: String? = null,
    val isConnected: Boolean = false,
)
