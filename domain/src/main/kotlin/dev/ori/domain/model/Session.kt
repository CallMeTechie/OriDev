package dev.ori.domain.model

/**
 * Domain-layer handle on an open SSH session. Exposed by
 * [dev.ori.domain.repository.SessionRegistry] to every UI consumer
 * (Connections, Terminal, Files). The inner network-layer
 * `dev.ori.core.network.ssh.SshSession` stays in `:core:core-network`
 * as a SSHJ-specific concern; this type strips away everything the
 * UI should not care about (no SSHJ references) and adds
 * [profileName] so log breadcrumbs can carry a human-readable tag
 * per the spec's Section 3.2 observability requirement.
 */
data class Session(
    val id: String,
    val profileId: Long,
    val profileName: String,
    val host: String,
    val port: Int,
    val connectedAt: Long,
)
