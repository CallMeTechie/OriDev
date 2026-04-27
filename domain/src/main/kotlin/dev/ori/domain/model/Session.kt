package dev.ori.domain.model

import dev.ori.core.common.model.Protocol

/**
 * Domain-layer handle on an open SSH session. Exposed by
 * [dev.ori.domain.repository.SessionRegistry] to every UI consumer
 * (Connections, Terminal, Files). The inner network-layer
 * `dev.ori.core.network.ssh.SshSession` stays in `:core:core-network`
 * as a SSHJ-specific concern; this type strips away everything the
 * UI should not care about (no SSHJ references) and adds
 * [profileName] so log breadcrumbs can carry a human-readable tag
 * per the spec's Section 3.2 observability requirement.
 *
 * [protocol] carries the connection protocol (SSH/SFTP/SCP) so that
 * protocol-keyed dispatch (e.g. [RemoteFileSystemRepository]) can
 * resolve the correct [dev.ori.core.network.ssh.SshClient] without
 * a separate registry lookup.
 */
data class Session(
    val id: String,
    val profileId: Long,
    val profileName: String,
    val host: String,
    val port: Int,
    val connectedAt: Long,
    val protocol: Protocol,
)
