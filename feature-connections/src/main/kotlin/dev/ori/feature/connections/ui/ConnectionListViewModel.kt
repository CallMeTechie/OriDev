package dev.ori.feature.connections.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppErrorException
import dev.ori.core.common.result.getAppError
import dev.ori.core.security.biometric.CredentialUnlockGate
import dev.ori.core.security.crash.NonFatalErrorLogger
import dev.ori.data.session.FailedResume
import dev.ori.data.session.FailedResumeRegistry
import dev.ori.data.session.ResumeCoordinator
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.preferences.SessionResumePreferences
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.SessionRegistry
import dev.ori.domain.usecase.ConnectUseCase
import dev.ori.domain.usecase.DeleteProfileUseCase
import dev.ori.domain.usecase.DisconnectUseCase
import dev.ori.domain.usecase.GetConnectionsUseCase
import dev.ori.domain.usecase.GetFavoriteConnectionsUseCase
import dev.ori.domain.usecase.SaveProfileUseCase
import dev.ori.domain.usecase.TrustHostUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionListViewModel @Inject constructor(
    private val getConnections: GetConnectionsUseCase,
    private val getFavorites: GetFavoriteConnectionsUseCase,
    private val connectUseCase: ConnectUseCase,
    private val disconnectUseCase: DisconnectUseCase,
    private val deleteProfileUseCase: DeleteProfileUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val trustHostUseCase: TrustHostUseCase,
    private val credentialUnlockGate: CredentialUnlockGate,
    private val connectionRepository: ConnectionRepository,
    private val sessionRegistry: SessionRegistry,
    private val sessionResumePrefs: SessionResumePreferences,
    private val failedRegistry: FailedResumeRegistry,
    private val resumeCoordinator: ResumeCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionListUiState())
    val uiState: StateFlow<ConnectionListUiState> = _uiState.asStateFlow()

    /**
     * Task 15 — direct pass-through of the process-wide
     * [FailedResumeRegistry]. Entries auto-clear once the corresponding
     * profile re-enters [SessionRegistry.openSessions] (handled inside
     * the registry itself), so the VM does not own dismissal logic
     * beyond the explicit [dismissFailedResume] affordance.
     */
    val failedResume: StateFlow<List<FailedResume>> = failedRegistry.failed

    /**
     * PR 2 Section 8 — profiles with an open session, derived by joining
     * [ConnectionRepository.getAllProfiles] against
     * [SessionRegistry.openSessions]. The "Aktiv" section of the
     * [ConnectionListScreen] renders this list, and the
     * [ConnectionListUiState.activeProfiles] mirror is wired from here
     * so the screen sees a single source of truth. Derivation (instead
     * of a parallel field) closes the "roter Punkt bleibt rot"-bug
     * structurally: a session that is not in the registry cannot
     * appear in the active list.
     */
    val activeProfiles: StateFlow<List<ServerProfile>> =
        combine(
            connectionRepository.getAllProfiles(),
            sessionRegistry.openSessions,
        ) { profiles, sessions ->
            val activeIds = sessions.map { it.profileId }.toSet()
            profiles.filter { it.id in activeIds }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * PR 3 Section 11 safety-net — profiles the OS killed the app with
     * open sessions for. Derived by joining
     * [SessionRegistry.persistedProfileIds] against the profile catalog
     * and filtering out the banner entirely when the registry already
     * has live sessions (the banner is only relevant *after* a process
     * kill, when [SessionRegistry.openSessions] is empty but persistence
     * has entries). The screen renders this as a [ReconnectBanner] and
     * wires [reconnectAll] / [dismissReconnectBanner] off it.
     *
     * The `openSessions` short-circuit keeps the banner from flickering
     * in between the first successful reconnect and the persistence
     * update: as soon as one session is live we hide the banner, even
     * if DataStore has not yet emitted the new set.
     */
    val reconnectBannerProfiles: StateFlow<List<ServerProfile>> =
        combine(
            sessionRegistry.persistedProfileIds,
            sessionRegistry.openSessions,
            connectionRepository.getAllProfiles(),
        ) { persisted, currentlyOpen, profiles ->
            if (currentlyOpen.isNotEmpty()) {
                emptyList()
            } else {
                profiles.filter { it.id in persisted }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * PR 2 — one-shot navigation effects emitted by [openProfile] on a
     * successful `sessionRegistry.connect()`. The screen collects this
     * flow and drives the nav callback (Terminal / Files) with the real
     * sessionId. Buffered so an emission made just before the Screen
     * collector attaches (e.g. during a recomposition) is not dropped.
     */
    private val _openEffects = MutableSharedFlow<OpenProfileEffect>(extraBufferCapacity = 1)
    val openEffects: SharedFlow<OpenProfileEffect> = _openEffects.asSharedFlow()

    /**
     * Connect the profile on-demand and emit a [OpenProfileEffect] so the
     * Screen can focus the session and navigate to the requested
     * bottom-tab. Replaces the explicit Connect button in the detail
     * sheet — the first tap on "Terminal öffnen" / "Dateien öffnen" is
     * what establishes the session (Termux/Blink/JuiceSSH pattern).
     *
     * On failure the sheet stays open and the error surfaces through
     * [ConnectionListUiState.error]; a Downloads log file is written via
     * [NonFatalErrorLogger] so the failure is diagnosable without adb.
     */
    fun openProfile(profileId: Long, target: OpenTarget) {
        viewModelScope.launch {
            val result = sessionRegistry.connect(profileId)
            result.onSuccess { session ->
                _openEffects.emit(OpenProfileEffect(target, session.id))
            }.onFailure { cause ->
                // Bug P fix — `sessionRegistry.connect` returns the raw
                // SSHJ TransportException on host-key failure, with the
                // verifier's `AppErrorException(AppError.HostKeyUnknown)`
                // buried in the cause chain. Without this walk the user
                // would only ever see "Unknown host key for …" as an
                // error snackbar, never the TOFU dialog, and retrying
                // would never persist the fingerprint — exactly the
                // fail2ban-bait pattern the manual-connect path already
                // guards against in `connect(profileId)` above.
                val hostKeyError = findHostKeyError(cause)
                if (hostKeyError != null) {
                    _uiState.update {
                        it.copy(hostKeyPrompt = hostKeyPromptFor(profileId, hostKeyError, target))
                    }
                    return@onFailure
                }
                val message = cause.message ?: "Verbindungsaufbau fehlgeschlagen"
                // Surface the error to the UI first, THEN write the
                // Downloads log — the logger touches `android.util.Log`
                // which throws "not mocked" under pure-JVM unit tests,
                // so running it last keeps the VM side-effect ordering
                // deterministic for tests while still producing the
                // diagnostic artifact on-device.
                _uiState.update { it.copy(error = message) }
                @Suppress("TooGenericExceptionCaught")
                try {
                    NonFatalErrorLogger.log(
                        category = "connect-${target.name.lowercase()}",
                        throwable = cause,
                        contextNote = "profileId=$profileId",
                    )
                } catch (_: Throwable) {
                    // Swallow: the logger is best-effort; see JVM-unit-test
                    // caveat above. The user-facing error already lives in
                    // `_uiState.error`, so nothing more is owed here.
                }
            }
        }
    }

    /**
     * Walk the [Throwable.cause] chain looking for the
     * [AppErrorException] our [dev.ori.core.network.ssh.OriDevHostKeyVerifier]
     * raises on TOFU failure. SSHJ wraps that exception in its own
     * `TransportException` → `SSHException` chain (see
     * `error/oridev-error-connect-files-2026-04-27-20-37-50.txt`), so a
     * shallow `cause as? AppErrorException` cast misses the marker.
     * Returns the host-key [AppError] if found (either
     * [AppError.HostKeyUnknown] or [AppError.HostKeyMismatch]), or null
     * if the failure had a different root cause.
     */
    private fun findHostKeyError(throwable: Throwable?): AppError? {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is AppErrorException) {
                val error = current.error
                if (error is AppError.HostKeyUnknown || error is AppError.HostKeyMismatch) {
                    return error
                }
            }
            current = current.cause
        }
        return null
    }

    private fun hostKeyPromptFor(
        profileId: Long,
        error: AppError,
        pendingOpenTarget: OpenTarget,
    ): HostKeyPrompt = when (error) {
        is AppError.HostKeyUnknown -> HostKeyPrompt(
            profileId = profileId,
            host = error.host,
            port = portForHost(error.host),
            fingerprint = error.fingerprint,
            keyType = error.keyType,
            pendingOpenTarget = pendingOpenTarget,
        )
        is AppError.HostKeyMismatch -> HostKeyPrompt(
            profileId = profileId,
            host = error.host,
            port = portForHost(error.host),
            fingerprint = error.actualFingerprint,
            keyType = "unknown",
            expectedFingerprint = error.expectedFingerprint,
            pendingOpenTarget = pendingOpenTarget,
        )
        // The caller filters via `findHostKeyError` so anything else here
        // is a programmer error: fall back to a placeholder prompt rather
        // than crash. The dialog still renders something sensible, and
        // `acceptHostKey` will short-circuit on the empty fingerprint.
        else -> HostKeyPrompt(
            profileId = profileId,
            host = "",
            port = 22,
            fingerprint = "",
            keyType = "unknown",
            pendingOpenTarget = pendingOpenTarget,
        )
    }

    init {
        viewModelScope.launch {
            combine(
                getConnections(),
                getFavorites(),
            ) { profiles, favorites ->
                _uiState.value.copy(
                    profiles = profiles,
                    favorites = favorites,
                    isLoading = false,
                )
            }.catch { e ->
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to load profiles",
                        isLoading = false,
                    )
                }
            }.collect { state ->
                _uiState.update {
                    it.copy(
                        profiles = state.profiles,
                        favorites = state.favorites,
                        isLoading = state.isLoading,
                    )
                }
            }
        }
        // Observe active SSH sessions so the connection dot and the
        // "X aktiv" top-bar pill reflect real state. Without this the
        // per-row indicator stayed permanently red even after a
        // successful connect, and the detail sheet's "Connect"/
        // "Disconnect" label was always wrong.
        viewModelScope.launch {
            connectionRepository.getActiveConnections().collect { active ->
                _uiState.update { it.copy(activeConnections = active) }
            }
        }
        // PR 2 Section 8 — mirror the derived [activeProfiles] StateFlow
        // into [ConnectionListUiState.activeProfiles] so the screen can
        // read everything off a single uiState snapshot (the "Aktiv"
        // LazyColumn section + the TopBar "N aktiv" pill share one
        // source).
        viewModelScope.launch {
            activeProfiles.collect { profiles ->
                _uiState.update { it.copy(activeProfiles = profiles) }
            }
        }
        // PR 3 Section 11 — same pattern for the reconnect banner so the
        // screen reads a single uiState snapshot instead of collecting a
        // second StateFlow itself.
        viewModelScope.launch {
            reconnectBannerProfiles.collect { profiles ->
                _uiState.update { it.copy(reconnectBannerProfiles = profiles) }
            }
        }
        // Task 15 — mirror the FailedResumeRegistry so the screen can
        // render the FailedResumeBanner off uiState. Auto-cleanup on
        // successful retry happens inside the registry itself
        // (openSessions observer in FailedResumeRegistry.init), so no
        // further bookkeeping is owed here.
        viewModelScope.launch {
            failedRegistry.failed.collect { entries ->
                _uiState.update { it.copy(failedResume = entries) }
            }
        }
        // Task 15 — the ResumeCoordinator serialises TOFU prompts from
        // auto-resume via its own Mutex + Channel. We surface the active
        // prompt through the same uiState.hostKeyPrompt field the
        // manual-connect failure path uses so the screen renders a
        // single dialog regardless of origin. The `coordinatorPromptId`
        // discriminator tells `acceptHostKey`/`rejectHostKey` to ack the
        // coordinator in addition to (or instead of) the
        // TrustHostUseCase round-trip.
        viewModelScope.launch {
            resumeCoordinator.hostKeyPrompts.collect { coordinatorPrompt ->
                if (coordinatorPrompt != null) {
                    _uiState.update {
                        it.copy(
                            hostKeyPrompt = HostKeyPrompt(
                                profileId = coordinatorPrompt.profileId,
                                host = coordinatorPrompt.host,
                                port = portForHost(coordinatorPrompt.host),
                                fingerprint = coordinatorPrompt.fingerprint,
                                keyType = "unknown",
                                expectedFingerprint = null,
                                coordinatorPromptId = coordinatorPrompt.id,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * PR 3 Section 11 safety-net — batch-reconnect every profile in the
     * current banner. Each call routes through [SessionRegistry.connect],
     * which coalesces concurrent handshakes and pulls passwords from the
     * Keystore (#171), so the user never sees a credential prompt. A
     * failed reconnect surfaces via the same error-toast channel as the
     * single-profile flow; successful ones repopulate
     * [SessionRegistry.openSessions] which in turn empties
     * [reconnectBannerProfiles] via the `openSessions.isNotEmpty()`
     * short-circuit.
     */
    fun reconnectAll() {
        viewModelScope.launch {
            reconnectBannerProfiles.value.forEach { profile ->
                sessionRegistry.connect(profile.id)
            }
        }
    }

    /**
     * PR 3 Section 11 — "Schließen" on the banner. Clears the persisted
     * profile-id set (plus the rest of the resume subset —
     * tabMemos / focusedProfileId / remotePaths) so the banner stays
     * hidden across subsequent app launches until new sessions are
     * opened. Does not disconnect any live sessions (there are none by
     * definition in the banner path) and leaves `lastTopLevelRoute`
     * alone so cold start still returns to the user's last tab.
     */
    fun dismissReconnectBanner() {
        viewModelScope.launch {
            sessionResumePrefs.clearResumeSubset()
        }
    }

    /**
     * Task 15 — "Schließen" on the [FailedResumeBanner]. Clears the
     * in-memory [FailedResumeRegistry] (process-scoped) so the banner
     * disappears for the rest of the session. Entries are not
     * persisted, so subsequent app launches start with an empty
     * registry regardless of this call; dismissal is purely for the
     * current session's UI affordance.
     */
    fun dismissFailedResume() {
        failedRegistry.clear()
    }

    /**
     * PR 2 Section 8 — quick-disconnect for the "Aktiv" section's eject
     * icon. Looks up the session whose [Session.profileId] matches
     * [profileId] and routes to [SessionRegistry.disconnect]. No-op if
     * the profile has no open session (race: the user tapped eject
     * just as the session tore down on its own).
     */
    fun quickDisconnect(profileId: Long) {
        viewModelScope.launch {
            val sessionId = sessionRegistry.openSessions.value
                .firstOrNull { it.profileId == profileId }?.id ?: return@launch
            sessionRegistry.disconnect(sessionId)
        }
    }

    fun onEvent(event: ConnectionListEvent) {
        when (event) {
            is ConnectionListEvent.Connect -> connect(event.profileId)
            is ConnectionListEvent.Disconnect -> disconnect(event.profileId)
            is ConnectionListEvent.Delete -> delete(event)
            is ConnectionListEvent.ToggleFavorite -> toggleFavorite(event)
            is ConnectionListEvent.Search -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is ConnectionListEvent.ClearError -> {
                _uiState.update { it.copy(error = null) }
            }
            is ConnectionListEvent.AcceptHostKey -> acceptHostKey()
            is ConnectionListEvent.RejectHostKey -> rejectHostKey()
        }
    }

    private fun connect(profileId: Long) {
        viewModelScope.launch {
            val result = connectUseCase(profileId)
            result.getAppError()?.let { error ->
                // Dump the full cause chain to Downloads/oridev-error-*.txt
                // so "connection failed" is diagnosable without adb logcat.
                // The user-facing message stays short and friendly; the
                // Throwable-level detail lives in the log file.
                val cause = error.cause ?: AppErrorCarrier(error.message)
                NonFatalErrorLogger.log(
                    category = "connect-${error::class.simpleName?.lowercase() ?: "error"}",
                    throwable = cause,
                    contextNote = "profileId=$profileId; userMessage=${error.message}",
                )
                // Unknown/mismatched host keys must surface as a TOFU
                // dialog — if we quietly show an error toast the user
                // keeps retrying, which leads to fail2ban bans against
                // the client IP on the target host.
                when (error) {
                    is AppError.HostKeyUnknown -> _uiState.update {
                        it.copy(
                            hostKeyPrompt = HostKeyPrompt(
                                profileId = profileId,
                                host = error.host,
                                port = portForHost(error.host),
                                fingerprint = error.fingerprint,
                                keyType = error.keyType,
                            ),
                        )
                    }
                    is AppError.HostKeyMismatch -> _uiState.update {
                        it.copy(
                            hostKeyPrompt = HostKeyPrompt(
                                profileId = profileId,
                                host = error.host,
                                port = portForHost(error.host),
                                fingerprint = error.actualFingerprint,
                                keyType = "unknown",
                                expectedFingerprint = error.expectedFingerprint,
                            ),
                        )
                    }
                    else -> _uiState.update { it.copy(error = error.message) }
                }
            }
        }
    }

    private fun portForHost(host: String): Int =
        _uiState.value.profiles.firstOrNull { it.host == host }?.port ?: 22

    private fun acceptHostKey() {
        val prompt = _uiState.value.hostKeyPrompt ?: return
        // Task 15 — coordinator-originated prompts are acked directly
        // through `respondToPrompt`; the coordinator itself will call
        // `TrustHostUseCase` + retry the connect on the accept path
        // (see `ResumeCoordinator.enqueueHostKeyPrompt`). Running the
        // manual-connect trust-then-retry pipeline in that case would
        // double-trust and double-connect.
        prompt.coordinatorPromptId?.let { id ->
            resumeCoordinator.respondToPrompt(id, accept = true)
            _uiState.update { it.copy(hostKeyPrompt = null) }
            return
        }
        viewModelScope.launch {
            val result = trustHostUseCase(
                host = prompt.host,
                port = prompt.port,
                keyType = prompt.keyType,
                fingerprint = prompt.fingerprint,
            )
            result.getAppError()?.let { error ->
                _uiState.update {
                    it.copy(
                        hostKeyPrompt = null,
                        error = error.message,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(hostKeyPrompt = null) }
            // Bug P fix — when the prompt was raised by `openProfile`
            // we MUST retry through `openProfile` so the navigation
            // effect (`OpenProfileEffect`) still fires after the trust
            // round-trip. Routing into `connect(profileId)` here would
            // open the SSH session but never tell the screen to switch
            // to Terminal/Files, leaving the user staring at the
            // Connections list while the connection is in fact live.
            prompt.pendingOpenTarget?.let { target ->
                openProfile(prompt.profileId, target)
                return@launch
            }
            connect(prompt.profileId)
        }
    }

    /**
     * Task 15 — reject the current TOFU dialog. Manual-connect prompts
     * merely clear the uiState field; coordinator-originated prompts
     * additionally send a `respondToPrompt(id, accept=false)` so the
     * coordinator's `promptResponses` channel can deliver the decline
     * ack instead of timing out (30 s).
     */
    private fun rejectHostKey() {
        val prompt = _uiState.value.hostKeyPrompt
        prompt?.coordinatorPromptId?.let { id ->
            resumeCoordinator.respondToPrompt(id, accept = false)
        }
        _uiState.update { it.copy(hostKeyPrompt = null) }
    }

    /**
     * Wrapper so that error paths that never carried a [Throwable] (e.g.
     * an [dev.ori.core.common.error.AppError.PermissionDenied]) still end
     * up in the Downloads log with at least the user-facing message.
     */
    private class AppErrorCarrier(message: String) : Throwable(message)

    /**
     * Phase 11 Tier-1 T1d — biometric gate wrapper for the connect flow.
     * Called from [ConnectionListScreen] tap handler with the host
     * [FragmentActivity] so [CredentialUnlockGate] can raise a BiometricPrompt
     * when the user has enabled the unlock toggle. If the gate clears,
     * dispatches the existing [ConnectionListEvent.Connect] pipeline; if it
     * fails (user cancelled or sensor locked out), surfaces the error to
     * [ConnectionListUiState.error].
     */
    fun unlockAndConnect(activity: FragmentActivity, profileId: Long) {
        viewModelScope.launch {
            val unlockResult = credentialUnlockGate.requireUnlock(
                activity = activity,
                title = "Verbindung entsperren",
                subtitle = "Biometrie bestätigen, um Zugangsdaten zu laden",
            )
            if (unlockResult.isFailure) {
                // Funnel the biometric rejection through the same
                // Downloads/oridev-error-*.txt channel as every other
                // connect failure so "tap Connect, nothing happens" has
                // at least one artifact to diagnose from.
                val cause = unlockResult.exceptionOrNull()
                    ?: AppErrorCarrier("Biometrie abgebrochen")
                NonFatalErrorLogger.log(
                    category = "connect-biometric",
                    throwable = cause,
                    contextNote = "profileId=$profileId",
                )
                val message = cause.message ?: "Biometrie abgebrochen"
                _uiState.update { it.copy(error = message) }
                return@launch
            }
            connect(profileId)
        }
    }

    private fun disconnect(profileId: Long) {
        viewModelScope.launch {
            try {
                disconnectUseCase(profileId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to disconnect")
                }
            }
        }
    }

    private fun delete(event: ConnectionListEvent.Delete) {
        viewModelScope.launch {
            try {
                deleteProfileUseCase(event.profile)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete profile")
                }
            }
        }
    }

    private fun toggleFavorite(event: ConnectionListEvent.ToggleFavorite) {
        viewModelScope.launch {
            val updated = event.profile.copy(isFavorite = !event.profile.isFavorite)
            val result = saveProfileUseCase(updated)
            result.getAppError()?.let { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    private companion object {
        /**
         * `WhileSubscribed` stop timeout for the `activeProfiles`
         * `stateIn` — matches the standard 5 s Compose-recomposition
         * grace window.
         */
        const val STATE_SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
