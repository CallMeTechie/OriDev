package dev.ori.domain.model

import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.SshKeyType

data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: Protocol,
    val username: String,
    val authMethod: AuthMethod,
    val credentialRef: String,
    val sshKeyType: SshKeyType? = null,
    val startupCommand: String? = null,
    val projectDirectory: String? = null,
    val claudeCodeModel: String? = null,
    val claudeMdPath: String? = null,
    val isFavorite: Boolean = false,
    val lastConnected: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val require2fa: Boolean = false,
    val maxBandwidthKbps: Int? = null,
)
