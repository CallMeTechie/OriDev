package dev.ori.data.session

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppErrorException
import dev.ori.core.security.crash.NonFatalErrorLogger
import dev.ori.data.dao.ServerProfileDao
import dev.ori.domain.model.ResumeAction
import dev.ori.domain.model.ResumeSnackbar
import dev.ori.domain.preferences.AutoResumePreferences
import dev.ori.domain.preferences.SessionResumePreferences
import dev.ori.domain.repository.SessionRegistry
import dev.ori.domain.usecase.TrustHostUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.UnknownHostException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of a pending Trust-On-First-Use prompt that the
 * [ResumeCoordinator] is waiting on. The app-level UI collects
 * [ResumeCoordinator.hostKeyPrompts] and renders the dialog; the user's
 * answer comes back through [ResumeCoordinator.respondToPrompt]. The
 * coordinator serialises these — at most one prompt is surfaced at a
 * time so the user is never asked to trust two hosts simultaneously.
 */
data class HostKeyPrompt(
    val id: String,
    val profileId: Long,
    val profileName: String,
    val host: String,
    val fingerprint: String,
)

/**
 * Drives the cold-start auto-resume flow (spec Section 11 + §6.1).
 *
 * Exposes a non-suspending [start] that is fire-and-forget and
 * idempotent per-process. Owns its own [CoroutineScope] so Activity
 * recreation mid-handshake cannot cancel in-flight connects.
 *
 * Constructor split: the Hilt-visible `@Inject` constructor supplies the
 * default IO-backed scope; the primary constructor accepts an injected
 * [CoroutineScope] so unit tests can drive the flow through virtual time
 * (see [ResumeCoordinatorTest]).
 */
@Singleton
class ResumeCoordinator internal constructor(
    private val sessionRegistry: SessionRegistry,
    private val resumePrefs: SessionResumePreferences,
    private val autoResumePrefs: AutoResumePreferences,
    private val failedRegistry: FailedResumeRegistry,
    private val trustHostUseCase: TrustHostUseCase,
    private val serverProfileDao: ServerProfileDao,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        sessionRegistry: SessionRegistry,
        resumePrefs: SessionResumePreferences,
        autoResumePrefs: AutoResumePreferences,
        failedRegistry: FailedResumeRegistry,
        trustHostUseCase: TrustHostUseCase,
        serverProfileDao: ServerProfileDao,
    ) : this(
        sessionRegistry,
        resumePrefs,
        autoResumePrefs,
        failedRegistry,
        trustHostUseCase,
        serverProfileDao,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val ran = AtomicBoolean(false)

    private val _snackbarEvents =
        MutableSharedFlow<ResumeSnackbar>(replay = 1, extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<ResumeSnackbar> = _snackbarEvents.asSharedFlow()

    private val _hostKeyPrompts = MutableStateFlow<HostKeyPrompt?>(null)
    val hostKeyPrompts: StateFlow<HostKeyPrompt?> = _hostKeyPrompts.asStateFlow()

    private val promptResponses = Channel<Pair<String, Boolean>>(Channel.UNLIMITED)
    private val promptMutex = Mutex()

    /**
     * Thread-safe: [runResume] launches N parallel `async` blocks that
     * can all call [handleFailure]; a plain `ArrayList` would CME.
     */
    private val pendingFailures: MutableList<FailedResume> =
        Collections.synchronizedList(mutableListOf())

    private companion object {
        /**
         * If the user never answers the TOFU dialog (app backgrounded,
         * VM not subscribed, navigated away) the coordinator must not
         * hang forever — fall through after 30 s and treat the prompt
         * as declined.
         */
        const val HOST_KEY_PROMPT_TIMEOUT_MS = 30_000L
    }

    /**
     * Idempotent fire-and-forget entry point called from
     * `MainActivity.onCreate`. Subsequent calls in the same process are
     * no-ops thanks to the [ran] gate — the first caller wins.
     */
    fun start() {
        if (!ran.compareAndSet(false, true)) return
        scope.launch { runResume() }
    }

    /** Called by the UI when the user accepts or declines a TOFU prompt. */
    fun respondToPrompt(promptId: String, accept: Boolean) {
        promptResponses.trySend(promptId to accept)
    }

    private suspend fun runResume() {
        val enabled = autoResumePrefs.autoResumeSessions.first()
        if (!enabled) return

        val persisted = resumePrefs.profileIds.first()
        if (persisted.isEmpty()) return

        pendingFailures.clear()
        persisted.map { profileId ->
            scope.async { connectWithHostKeyQueue(profileId) }
        }.awaitAll()

        // After all connects resolved, apply the persisted focus.
        val focusedProfileId = resumePrefs.focusedProfileId.first()
        val sessions = sessionRegistry.openSessions.first()
        sessions.firstOrNull { it.profileId == focusedProfileId }?.id
            ?.let { sessionRegistry.focus(it) }

        // Single fail-count-aware snackbar (spec §6.1).
        emitFailureSnackbarIfAny()
    }

    private suspend fun connectWithHostKeyQueue(profileId: Long) {
        val result = sessionRegistry.connect(profileId)
        result.fold(
            onSuccess = { /* registry emitted; observer handles tabs + paths */ },
            onFailure = { cause ->
                val appError = (cause as? AppErrorException)?.error
                when (appError) {
                    is AppError.HostKeyUnknown -> enqueueHostKeyPrompt(profileId, appError)
                    null -> handleFailure(profileId, cause)
                    else -> handleFailure(profileId, cause)
                }
            },
        )
    }

    private suspend fun enqueueHostKeyPrompt(profileId: Long, cause: AppError.HostKeyUnknown) {
        val profile = serverProfileDao.getById(profileId) ?: run {
            handleFailure(profileId, AppErrorException(AppError.ProfileNotFound(profileId)))
            return
        }
        val prompt = HostKeyPrompt(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            profileName = profile.name,
            host = cause.host,
            fingerprint = cause.fingerprint,
        )

        // Mutex + receive serialise prompts: user is never asked to
        // trust two hosts at the same time. Multiple profiles hitting
        // TOFU queue behind each other.
        promptMutex.withLock {
            _hostKeyPrompts.value = prompt
            val response = withTimeoutOrNull(HOST_KEY_PROMPT_TIMEOUT_MS) {
                promptResponses.receive()
            }
            _hostKeyPrompts.value = null
            when {
                // Timeout → treat as decline.
                response == null -> handleFailure(profileId, AppErrorException(cause))
                // Stale ack (id mismatch) → treat as decline.
                response.first != prompt.id -> handleFailure(profileId, AppErrorException(cause))
                response.second -> {
                    trustHostUseCase(
                        host = cause.host,
                        port = profile.port,
                        keyType = cause.keyType,
                        fingerprint = cause.fingerprint,
                    )
                    // Single retry after the user accepted the host key.
                    connectWithHostKeyQueue(profileId)
                }
                else -> handleFailure(profileId, AppErrorException(cause))
            }
        }
    }

    private suspend fun handleFailure(profileId: Long, cause: Throwable) {
        val profileName = runCatching { serverProfileDao.getById(profileId)?.name }
            .getOrNull() ?: "Profil #$profileId"
        val reason = humanReadable(cause)
        val entry = FailedResume(profileId, profileName, reason)
        pendingFailures += entry
        failedRegistry.add(entry)

        // NonFatalErrorLogger is best-effort. It internally catches its
        // own writer failures, but when the logger is called before
        // `install(applicationContext)` it falls back to `Log.w(...)`,
        // which throws "not mocked" under the unit-test classpath. We
        // swallow any such throw here so a missing install never
        // bubbles up into a failing resume pass.
        @Suppress("TooGenericExceptionCaught")
        try {
            val note = "profileId=$profileId; profileName=$profileName; " +
                "causeKind=${cause.javaClass.simpleName}"
            NonFatalErrorLogger.log(
                category = "auto-resume-fail",
                throwable = cause,
                contextNote = note,
            )
        } catch (ignore: Throwable) {
            // Intentional: logger failure must never break resume.
        }
    }

    private suspend fun emitFailureSnackbarIfAny() {
        val snapshot = synchronized(pendingFailures) { pendingFailures.toList() }
        when (snapshot.size) {
            0 -> return
            1 -> {
                val f = snapshot.single()
                _snackbarEvents.emit(
                    ResumeSnackbar(
                        message = "${f.profileName}: ${f.reason}",
                        actionLabel = "ÖFFNEN",
                        action = ResumeAction.OpenConnections(f.profileId),
                    ),
                )
            }
            else -> {
                _snackbarEvents.emit(
                    ResumeSnackbar(
                        message = "${snapshot.size} Wiederverbindungen fehlgeschlagen",
                        actionLabel = "DETAILS",
                        action = ResumeAction.OpenConnections(snapshot.first().profileId),
                    ),
                )
            }
        }
    }

    private fun humanReadable(cause: Throwable): String {
        val appError = (cause as? AppErrorException)?.error
        return when {
            appError is AppError.HostKeyMismatch -> "Host-Key abgelehnt"
            appError is AppError.HostKeyUnknown -> "Host-Key unbekannt"
            appError is AppError.CredentialMissing -> "Passwort nicht gespeichert"
            appError is AppError.ProfileNotFound -> "Profil entfernt"
            cause is UnknownHostException -> "Host nicht erreichbar"
            cause is IOException -> "Verbindungsfehler"
            else -> cause.message ?: "Unbekannter Fehler"
        }
    }
}
