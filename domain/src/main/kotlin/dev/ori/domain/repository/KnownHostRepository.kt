package dev.ori.domain.repository

import kotlinx.coroutines.flow.Flow

interface KnownHostRepository {
    suspend fun findHost(host: String, port: Int): KnownHostEntry?
    suspend fun trustHost(host: String, port: Int, keyType: String, fingerprint: String)
    suspend fun removeHost(host: String, port: Int)
    fun getAllKnownHosts(): Flow<List<KnownHostEntry>>
}

data class KnownHostEntry(
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
    val firstSeen: Long,
    val lastSeen: Long
)
