package dev.ori.data.repository

import dev.ori.core.common.model.AuthMethod
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.network.ssh.SshSession
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.mapper.toDomain
import dev.ori.data.mapper.toEntity
import dev.ori.domain.model.Connection
import dev.ori.domain.model.ConnectionStatus
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val serverProfileDao: ServerProfileDao,
    private val sshClient: SshClient,
    private val credentialStore: CredentialStore,
) : ConnectionRepository {

    private val activeSessions = ConcurrentHashMap<Long, SshSession>()
    private val _activeConnections = MutableStateFlow<List<Connection>>(emptyList())

    override fun getAllProfiles(): Flow<List<ServerProfile>> =
        serverProfileDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getFavoriteProfiles(): Flow<List<ServerProfile>> =
        serverProfileDao.getFavorites().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getProfileById(id: Long): ServerProfile? =
        serverProfileDao.getById(id)?.toDomain()

    override suspend fun getProfileCount(): Int =
        serverProfileDao.getCount()

    override suspend fun saveProfile(profile: ServerProfile): Long =
        serverProfileDao.insert(profile.toEntity())

    override suspend fun updateProfile(profile: ServerProfile) {
        serverProfileDao.update(profile.toEntity())
    }

    override suspend fun deleteProfile(profile: ServerProfile) {
        serverProfileDao.delete(profile.toEntity())
    }

    override suspend fun connect(profileId: Long): Connection {
        val profile = serverProfileDao.getById(profileId)?.toDomain()
            ?: throw IllegalArgumentException("Profile not found: $profileId")

        var password: CharArray? = null
        var privateKey: ByteArray? = null

        try {
            when (profile.authMethod) {
                AuthMethod.PASSWORD -> {
                    password = credentialStore.getPassword(profile.credentialRef)
                }
                AuthMethod.SSH_KEY -> {
                    privateKey = credentialStore.getSshKey(profile.credentialRef)
                }
                AuthMethod.KEY_AGENT -> {
                    // Key agent handles auth externally
                }
            }

            val session = sshClient.connect(
                host = profile.host,
                port = profile.port,
                username = profile.username,
                password = password,
                privateKey = privateKey,
            )

            activeSessions[profileId] = session
            serverProfileDao.updateLastConnected(profileId)

            val connection = Connection(
                profileId = profileId,
                serverName = profile.name,
                host = profile.host,
                status = ConnectionStatus.CONNECTED,
                connectedSince = session.connectedAt,
            )

            updateActiveConnections()
            return connection
        } finally {
            password?.fill('\u0000')
        }
    }

    override suspend fun disconnect(profileId: Long) {
        val session = activeSessions.remove(profileId)
        if (session != null) {
            sshClient.disconnect(session.sessionId)
        }
        updateActiveConnections()
    }

    override fun getActiveConnections(): Flow<List<Connection>> =
        _activeConnections.asStateFlow()

    private fun updateActiveConnections() {
        _activeConnections.value = activeSessions.map { (profileId, session) ->
            Connection(
                profileId = profileId,
                serverName = session.host,
                host = session.host,
                status = ConnectionStatus.CONNECTED,
                connectedSince = session.connectedAt,
            )
        }
    }
}
