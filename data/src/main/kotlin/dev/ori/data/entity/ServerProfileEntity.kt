package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.SshKeyType

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: Protocol,
    val username: String,
    val authMethod: AuthMethod,
    val credentialRef: String,
    val sshKeyType: SshKeyType?,
    val startupCommand: String?,
    val projectDirectory: String?,
    val claudeCodeModel: String?,
    val claudeMdPath: String?,
    val isFavorite: Boolean = false,
    val lastConnected: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val require2fa: Boolean = false,
)
