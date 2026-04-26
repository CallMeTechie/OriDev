package dev.ori.data.session

import dev.ori.core.common.model.Protocol
import dev.ori.core.network.ssh.SshClient
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.mapper.toDomain
import dev.ori.domain.model.Session
import dev.ori.domain.repository.CredentialStore
import dev.ori.domain.repository.SessionRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton impl of [SessionRegistry]. See spec Sections 3, 3.1.
 *
 * The registry owns an internal supervisor scope so in-flight connects
 * survive ViewModel deaths — the coroutine that runs a TCP handshake
 * must not be cancelled just because the activity rotated.
 *
 * Injects [ServerProfileDao] directly rather than going through
 * `ConnectionRepository` because `ConnectionRepositoryImpl` itself
 * depends on this registry — a Dagger-level dependency cycle would
 * otherwise form (registry → repo → registry). The Dao is the narrow
 * dependency the profile-lookup actually needs.
 */
@Singleton
class SessionRegistryImpl(
    private val sshClient: SshClient,
    private val credentialStore: CredentialStore,
    private val serverProfileDao: ServerProfileDao,
    private val serviceLifecycleBinder: ServiceLifecycleBinder,
    private val sessionPersistence: SessionPersistencePreferences,
    registryDispatcher: CoroutineDispatcher,
) : SessionRegistry {

    /**
     * Hilt-visible constructor: delegates to the primary constructor with
     * [Dispatchers.IO]. The primary constructor accepts an injectable
     * dispatcher so unit tests can drive [scheduleGraceDisconnect]'s
     * `delay` through virtual time via `UnconfinedTestDispatcher(testScheduler)`.
     * We keep this secondary constructor rather than defaulting the
     * primary param because Dagger/Hilt ignores Kotlin default values on
     * `@Inject`-annotated constructors.
     */
    @Inject
    constructor(
        sshClient: SshClient,
        credentialStore: CredentialStore,
        serverProfileDao: ServerProfileDao,
        serviceLifecycleBinder: ServiceLifecycleBinder,
        sessionPersistence: SessionPersistencePreferences,
    ) : this(
        sshClient,
        credentialStore,
        serverProfileDao,
        serviceLifecycleBinder,
        sessionPersistence,
        Dispatchers.IO,
    )

    fun interface ServiceLifecycleBinder {
        fun onOpenSessionsChanged(anyOpen: Boolean)
    }

    private val scope = CoroutineScope(SupervisorJob() + registryDispatcher)

    private val _openSessions = MutableStateFlow<List<Session>>(emptyList())
    override val openSessions: StateFlow<List<Session>> = _openSessions.asStateFlow()

    private val _focusedSessionId = MutableStateFlow<String?>(null)
    override val focusedSessionId: StateFlow<String?> = _focusedSessionId.asStateFlow()

    /**
     * Read-only projection of [SessionPersistencePreferences.profileIds]
     * (spec Section 11). `SharingStarted.Eagerly` so the DataStore-backed
     * flow starts collecting immediately on registry construction —
     * consumers (the reconnect banner) must see the pre-kill set on the
     * very first composition after process restart, not after a 5 s
     * subscription grace. The registry scope lives for the whole process
     * so this has no teardown concern.
     */
    override val persistedProfileIds: StateFlow<Set<Long>> =
        sessionPersistence.profileIds
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Pending connects keyed by profileId. Lets concurrent `connect()`
     * calls for the same profile share one handshake and lets
     * `disconnect()` cancel an in-flight connect for that profile.
     */
    private val inFlight = ConcurrentHashMap<Long, Deferred<Result<Session>>>()

    private val filesUsed = ConcurrentHashMap.newKeySet<String>()
    private val graceJobs = ConcurrentHashMap<String, Job>()

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
        val profile = serverProfileDao.getById(profileId)?.toDomain()
            ?: error("Profile not found: $profileId")
        val password = credentialStore.getPassword(profile.credentialRef)
        val sshSession = sshClient.connect(
            host = profile.host,
            port = profile.port,
            username = profile.username,
            password = password,
            protocol = profile.protocol,
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
        serviceLifecycleBinder.onOpenSessionsChanged(true)
        persistCurrentProfileIds()
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
        filesUsed.remove(sessionId)
        graceJobs.remove(sessionId)?.cancel()
        if (_focusedSessionId.value == sessionId) {
            _focusedSessionId.value = _openSessions.value.firstOrNull()?.id
        }
        serviceLifecycleBinder.onOpenSessionsChanged(_openSessions.value.isNotEmpty())
        persistCurrentProfileIds()
    }

    private fun persistCurrentProfileIds() {
        scope.launch {
            sessionPersistence.setProfileIds(
                _openSessions.value.map { it.profileId }.toSet(),
            )
        }
    }

    override suspend fun cancelConnect(profileId: Long) {
        inFlight[profileId]?.cancel()
    }

    override fun markFilesUsed(sessionId: String) {
        filesUsed.add(sessionId)
        // If a grace job is pending for this session, cancel — Files wants it.
        graceJobs.remove(sessionId)?.cancel()
    }

    override fun scheduleGraceDisconnect(sessionId: String) {
        if (filesUsed.contains(sessionId)) return
        graceJobs.remove(sessionId)?.cancel()
        graceJobs[sessionId] = scope.launch {
            delay(GRACE_MILLIS)
            if (!filesUsed.contains(sessionId)) {
                disconnect(sessionId)
            }
            graceJobs.remove(sessionId)
        }
    }

    override fun cancelGraceDisconnect(sessionId: String) {
        graceJobs.remove(sessionId)?.cancel()
    }

    private companion object {
        const val GRACE_MILLIS = 5_000L
    }
}
