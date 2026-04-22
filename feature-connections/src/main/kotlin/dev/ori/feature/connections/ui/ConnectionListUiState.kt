package dev.ori.feature.connections.ui

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
