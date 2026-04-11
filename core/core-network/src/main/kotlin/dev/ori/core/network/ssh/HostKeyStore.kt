package dev.ori.core.network.ssh

data class StoredHostKey(
    val fingerprint: String,
    val keyType: String,
)

interface HostKeyStore {
    suspend fun findHost(host: String, port: Int): StoredHostKey?
    suspend fun updateLastSeen(host: String, port: Int)
}
