package dev.ori.data.session

import dev.ori.core.network.ssh.SshClient
import dev.ori.domain.model.Session
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.CredentialStore
import dev.ori.domain.repository.SessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton impl of [SessionRegistry]. See spec Sections 3, 3.1.
 *
 * The registry owns an internal supervisor scope so in-flight connects
 * survive ViewModel deaths — the coroutine that runs a TCP handshake
 * must not be cancelled just because the activity rotated.
 */
@Singleton
class SessionRegistryImpl @Inject constructor(
    private val sshClient: SshClient,
    private val credentialStore: CredentialStore,
    private val connectionRepository: ConnectionRepository,
) : SessionRegistry {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _openSessions = MutableStateFlow<List<Session>>(emptyList())
    override val openSessions: StateFlow<List<Session>> = _openSessions.asStateFlow()

    private val _focusedSessionId = MutableStateFlow<String?>(null)
    override val focusedSessionId: StateFlow<String?> = _focusedSessionId.asStateFlow()

    /**
     * Pending connects keyed by profileId. Lets concurrent `connect()`
     * calls for the same profile share one handshake and lets
     * `disconnect()` cancel an in-flight connect for that profile.
     */
    private val inFlight = ConcurrentHashMap<Long, Deferred<Result<Session>>>()

    override suspend fun connect(profileId: Long): Result<Session> {
        _openSessions.value.firstOrNull { it.profileId == profileId }?.let { existing ->
            _focusedSessionId.value = existing.id
            return Result.success(existing)
        }
        inFlight[profileId]?.let { return it.await() }
        val deferred = scope.async { runConnect(profileId) }
        inFlight[profileId] = deferred
        return try {
            deferred.await()
        } finally {
            inFlight.remove(profileId, deferred)
        }
    }

    private suspend fun runConnect(profileId: Long): Result<Session> = runCatching {
        val profile = connectionRepository.getProfileById(profileId)
            ?: error("Profile not found: $profileId")
        val password = credentialStore.getPassword(profile.credentialRef)
        val sshSession = sshClient.connect(
            host = profile.host,
            port = profile.port,
            username = profile.username,
            password = password,
        )
        val session = Session(
            id = sshSession.sessionId,
            profileId = profile.id,
            profileName = profile.name,
            host = sshSession.host,
            port = sshSession.port,
            connectedAt = sshSession.connectedAt,
        )
        _openSessions.update { it + session }
        _focusedSessionId.value = session.id
        session
    }

    override fun focus(sessionId: String) {
        if (_openSessions.value.any { it.id == sessionId }) {
            _focusedSessionId.value = sessionId
        }
    }

    /**
     * Tear down [sessionId]. If the session is currently in
     * [openSessions], close its SSH socket, remove it, and (if it
     * was focused) promote the next session to focus. Also cancels
     * any in-flight connect for the same profile so a handshake
     * finishing after we tear down doesn't produce a zombie entry.
     *
     * No-op if [sessionId] is not in [openSessions] — a mid-handshake
     * session has no id yet, so callers who want to cancel a pending
     * connect must use [cancelConnect] with the profileId instead.
     */
    override suspend fun disconnect(sessionId: String) {
        val session = _openSessions.value.firstOrNull { it.id == sessionId } ?: return
        inFlight[session.profileId]?.cancel()
        runCatching { sshClient.disconnect(sessionId) }
        _openSessions.update { list -> list.filterNot { it.id == sessionId } }
        if (_focusedSessionId.value == sessionId) {
            _focusedSessionId.value = _openSessions.value.firstOrNull()?.id
        }
    }

    override suspend fun cancelConnect(profileId: Long) {
        inFlight[profileId]?.cancel()
    }
}
