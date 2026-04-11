package dev.ori.core.network.ssh

data class SshSession(
    val sessionId: String,
    val profileId: Long,
    val host: String,
    val port: Int,
    val connectedAt: Long
)
