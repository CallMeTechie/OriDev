package dev.ori.data.repository

import dev.ori.core.network.ssh.SshClient
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.di.DefaultSshClient
import dev.ori.data.mapper.toDomain
import dev.ori.data.mapper.toEntity
import dev.ori.domain.model.Connection
import dev.ori.domain.model.ConnectionStatus
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.CredentialStore
import dev.ori.domain.repository.SessionRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session state lives in [SessionRegistry]; this repo keeps only the
 * Room-backed profile CRUD and the legacy [ConnectionRepository]
 * surface the rest of the app still consumes. `connect`, `disconnect`,
 * `getActiveSessionId`, and `getActiveConnections` are now thin
 * projections over the registry. The previous `activeSessions` map and
 * `loadPasswordOrFail` helper move into
 * [dev.ori.data.session.SessionRegistryImpl].
 *
 * [sshClient] stays injected purely so the existing DI graph keeps
 * resolving; nothing in this class uses it directly any more. Removing
 * it is tracked for PR 2 (the UI consolidation phase), where the
 * `SshClient` dep drops out of this constructor entirely.
 */
@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val serverProfileDao: ServerProfileDao,
    @DefaultSshClient @Suppress("unused") private val sshClient: SshClient,
    private val credentialStore: CredentialStore,
    private val sessionRegistry: SessionRegistry,
) : ConnectionRepository {

    override fun getAllProfiles(): Flow<List<ServerProfile>> =
        serverProfileDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getFavoriteProfiles(): Flow<List<ServerProfile>> =
        serverProfileDao.getFavorites().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getProfileById(id: Long): ServerProfile? =
        serverProfileDao.getById(id)?.toDomain()

    override suspend fun getProfileCount(): Int = serverProfileDao.getCount()

    override suspend fun saveProfile(profile: ServerProfile): Long =
        serverProfileDao.insert(profile.toEntity())

    override suspend fun updateProfile(profile: ServerProfile) {
        serverProfileDao.update(profile.toEntity())
    }

    override suspend fun deleteProfile(profile: ServerProfile) {
        serverProfileDao.delete(profile.toEntity())
        if (profile.credentialRef.startsWith(MANAGED_ALIAS_PREFIX)) {
            credentialStore.deleteCredential(profile.credentialRef)
        }
    }

    override suspend fun connect(profileId: Long): Connection {
        val session = sessionRegistry.connect(profileId).getOrThrow()
        serverProfileDao.updateLastConnected(profileId)
        return Connection(
            profileId = session.profileId,
            serverName = session.profileName,
            host = session.host,
            status = ConnectionStatus.CONNECTED,
            connectedSince = session.connectedAt,
        )
    }

    override suspend fun disconnect(profileId: Long) {
        val session = sessionRegistry.openSessions.value
            .firstOrNull { it.profileId == profileId }
        if (session != null) {
            sessionRegistry.disconnect(session.id)
        }
    }

    override fun getActiveConnections(): Flow<List<Connection>> =
        sessionRegistry.openSessions.map { sessions ->
            sessions.map { s ->
                Connection(
                    profileId = s.profileId,
                    serverName = s.profileName,
                    host = s.host,
                    status = ConnectionStatus.CONNECTED,
                    connectedSince = s.connectedAt,
                )
            }
        }

    override suspend fun getActiveSessionId(profileId: Long): String? =
        sessionRegistry.openSessions.value
            .firstOrNull { it.profileId == profileId }?.id

    private companion object {
        // Kept in sync with AddEditConnectionViewModel.KEYSTORE_ALIAS_PREFIX.
        const val MANAGED_ALIAS_PREFIX = "kref_"
    }
}
