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
        // Remove the Keystore entry alongside the row so we don't leak
        // encrypted password blobs for profiles the user deleted. Only
        // aliases this repo actually minted (prefix `kref_`) are touched —
        // legacy plaintext credentialRef strings are left alone.
        if (profile.credentialRef.startsWith(MANAGED_ALIAS_PREFIX)) {
            credentialStore.deleteCredential(profile.credentialRef)
        }
    }

    override suspend fun connect(profileId: Long): Connection {
        val profile = serverProfileDao.getById(profileId)?.toDomain()
            ?: throw IllegalArgumentException("Profile not found: $profileId")

        var password: CharArray? = null
        var privateKey: ByteArray? = null

        try {
            when (profile.authMethod) {
                AuthMethod.PASSWORD -> {
                    password = loadPasswordOrFail(profile, profileId)
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

    override suspend fun getActiveSessionId(profileId: Long): String? =
        activeSessions[profileId]?.sessionId

    /**
     * Look up the stored password for a PASSWORD-auth profile and raise
     * a typed error instead of passing `null` down to [SshClient]. SSHJ's
     * own `IllegalArgumentException("Either password or private key
     * must be provided")` is opaque in crash reports — this wrapper
     * spells out whether the alias was managed by the Keystore flow or
     * whether the row still carries a legacy plaintext ref from before
     * PR #171 (those need an edit-form re-save to migrate).
     */
    private suspend fun loadPasswordOrFail(
        profile: ServerProfile,
        profileId: Long,
    ): CharArray {
        val stored = credentialStore.getPassword(profile.credentialRef)
        if (stored != null) return stored
        val isManagedAlias = profile.credentialRef.startsWith(MANAGED_ALIAS_PREFIX)
        val detail = if (isManagedAlias) {
            "Keystore alias ${profile.credentialRef} has no stored password"
        } else {
            "Profile $profileId has a legacy credentialRef; open the edit form " +
                "and re-save the password to migrate it to the Keystore"
        }
        throw IllegalStateException("Password missing for profile $profileId: $detail")
    }

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

    private companion object {
        // Kept in sync with AddEditConnectionViewModel.KEYSTORE_ALIAS_PREFIX.
        // The ViewModel mints aliases with this prefix for every PASSWORD
        // profile it saves; the repo uses it as the cleanup trigger on
        // delete. Extracting the constant to a shared domain type would
        // require an inter-module utility that doesn't yet exist — the
        // duplication is explicit so the relationship is visible.
        const val MANAGED_ALIAS_PREFIX = "kref_"
    }
}
