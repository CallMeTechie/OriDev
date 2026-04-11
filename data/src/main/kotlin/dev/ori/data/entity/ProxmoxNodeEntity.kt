package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxmox_nodes")
data class ProxmoxNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 8006,
    val tokenId: String,
    val tokenSecretRef: String,
    val certFingerprint: String? = null,
    val lastSyncAt: Long? = null
)
