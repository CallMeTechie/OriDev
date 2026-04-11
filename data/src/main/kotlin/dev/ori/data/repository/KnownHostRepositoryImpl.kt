package dev.ori.data.repository

import dev.ori.data.dao.KnownHostDao
import dev.ori.data.entity.KnownHostEntity
import dev.ori.domain.repository.KnownHostEntry
import dev.ori.domain.repository.KnownHostRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnownHostRepositoryImpl @Inject constructor(
    private val knownHostDao: KnownHostDao,
) : KnownHostRepository {

    override suspend fun findHost(host: String, port: Int): KnownHostEntry? =
        knownHostDao.find(host, port)?.toDomain()

    override suspend fun trustHost(
        host: String,
        port: Int,
        keyType: String,
        fingerprint: String,
    ) {
        val now = System.currentTimeMillis()
        val existing = knownHostDao.find(host, port)
        val entity = KnownHostEntity(
            id = existing?.id ?: 0,
            host = host,
            port = port,
            keyType = keyType,
            fingerprint = fingerprint,
            firstSeen = existing?.firstSeen ?: now,
            lastSeen = now,
        )
        knownHostDao.upsert(entity)
    }

    override suspend fun removeHost(host: String, port: Int) {
        val entity = knownHostDao.find(host, port) ?: return
        knownHostDao.delete(entity)
    }

    override fun getAllKnownHosts(): Flow<List<KnownHostEntry>> =
        knownHostDao.getAll().map { entities -> entities.map { it.toDomain() } }

    private fun KnownHostEntity.toDomain(): KnownHostEntry =
        KnownHostEntry(
            host = host,
            port = port,
            keyType = keyType,
            fingerprint = fingerprint,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
        )
}
