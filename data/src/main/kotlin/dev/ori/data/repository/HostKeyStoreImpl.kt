package dev.ori.data.repository

import dev.ori.core.network.ssh.HostKeyStore
import dev.ori.core.network.ssh.StoredHostKey
import dev.ori.data.dao.KnownHostDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostKeyStoreImpl @Inject constructor(
    private val knownHostDao: KnownHostDao,
) : HostKeyStore {

    override suspend fun findHost(host: String, port: Int): StoredHostKey? {
        val entity = knownHostDao.find(host, port) ?: return null
        return StoredHostKey(
            fingerprint = entity.fingerprint,
            keyType = entity.keyType,
        )
    }

    override suspend fun updateLastSeen(host: String, port: Int) {
        val entity = knownHostDao.find(host, port) ?: return
        knownHostDao.upsert(entity.copy(lastSeen = System.currentTimeMillis()))
    }
}
