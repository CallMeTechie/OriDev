package dev.ori.feature.connections.ui

import dev.ori.data.session.FailedResume
import dev.ori.domain.model.Connection
import dev.ori.domain.model.ServerProfile

data class ConnectionListUiState(
    val profiles: List<ServerProfile> = emptyList(),
    val favorites: List<ServerProfile> = emptyList(),
    val activeConnections: List<Connection> = emptyList(),
    /**
     * PR 2 Section 8 — profiles whose id appears in
     * [dev.ori.domain.repository.SessionRegistry.openSessions]. Fed by
     * the [ConnectionListViewModel.activeProfiles] derived StateFlow.
     * Drives the "Aktiv" section of [ConnectionListScreen] plus the
     * "N aktiv" TopBar pill.
     */
    val activeProfiles: List<ServerProfile> = emptyList(),
    /**
     * PR 3 Section 11 safety-net — profiles whose id appears in
     * [dev.ori.domain.repository.SessionRegistry.persistedProfileIds]
     * *and* whose registry [dev.ori.domain.repository.SessionRegistry.openSessions]
     * is currently empty. When non-empty, the [ConnectionListScreen]
     * shows a dismissible [ReconnectBanner] offering one-tap batch
     * reconnect. Kept separate from [activeProfiles] because the
     * banner is an after-kill recovery affordance, not live state.
     */
    val reconnectBannerProfiles: List<ServerProfile> = emptyList(),
    /**
     * Task 15 — entries from [dev.ori.data.session.FailedResumeRegistry]
     * mirrored into uiState. When non-empty the [ConnectionListScreen]
     * renders a [FailedResumeBanner] so the user can jump to the
     * affected profile or dismiss the whole batch. The banner takes
     * precedence over [reconnectBannerProfiles] — a failed auto-resume
     * pass is "newer news" than the safety-net reconnect affordance.
     */
    val failedResume: List<FailedResume> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val hostKeyPrompt: HostKeyPrompt? = null,
)

/**
 * TOFU prompt raised when [dev.ori.domain.usecase.ConnectUseCase] returns
 * an [dev.ori.core.common.error.AppError.HostKeyUnknown] or
 * [dev.ori.core.common.error.AppError.HostKeyMismatch]. The UI layer turns
 * this into an accept/reject dialog; on accept the ViewModel persists the
 * fingerprint via [dev.ori.domain.usecase.TrustHostUseCase] and retries
 * the original connect attempt for [profileId].
 */
data class HostKeyPrompt(
    val profileId: Long,
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyType: String,
    val expectedFingerprint: String? = null,
    /**
     * Task 15 — when non-null this prompt originated from
     * [dev.ori.data.session.ResumeCoordinator.hostKeyPrompts] rather than
     * the manual-connect failure path. The accept / reject handlers must
     * call [dev.ori.data.session.ResumeCoordinator.respondToPrompt] with
     * this id so the coordinator's `promptResponses` channel receives a
     * matching ack (stale ids are treated as decline).
     */
    val coordinatorPromptId: String? = null,
)

sealed class ConnectionListEvent {
    data class Connect(val profileId: Long) : ConnectionListEvent()
    data class Disconnect(val profileId: Long) : ConnectionListEvent()
    data class Delete(val profile: ServerProfile) : ConnectionListEvent()
    data class ToggleFavorite(val profile: ServerProfile) : ConnectionListEvent()
    data class Search(val query: String) : ConnectionListEvent()
    data object ClearError : ConnectionListEvent()
    data object AcceptHostKey : ConnectionListEvent()
    data object RejectHostKey : ConnectionListEvent()
}

/**
 * PR 2 — disambiguates which top-level tab the sheet's primary CTA is
 * wiring toward so the single [ConnectionListViewModel.openProfile]
 * entry point can drive both flows.
 */
enum class OpenTarget { TERMINAL, FILES }

/**
 * PR 2 — one-shot navigation effect emitted by
 * [ConnectionListViewModel.openProfile] after a successful
 * `sessionRegistry.connect()`. Carries the real [sessionId] so the
 * Screen-level nav callback can focus the registry on it.
 */
data class OpenProfileEffect(val target: OpenTarget, val sessionId: String)
