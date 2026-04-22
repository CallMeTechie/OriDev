package dev.ori.domain.repository

import dev.ori.domain.model.Session
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for open SSH sessions (spec Sections 3, 3.1).
 *
 * Replaces three previously-fragmented owners:
 *  - `ConnectionRepositoryImpl.activeSessions` (was the "official" map)
 *  - the session map inside `SshClient` (was the SSHJ-handle map)
 *  - `TerminalViewModel`'s implicit state from its direct
 *    `sshClient.connect(...)` call
 *
 * The registry is intended to be a process-wide singleton (bound via Hilt in `:data`). All three flows are hot and
 * survive ViewModel recreation. [connect] is idempotent per
 * [Session.profileId] — concurrent calls for the same profile share
 * one in-flight `Deferred`, and a mid-handshake [disconnect] cancels
 * that Deferred and closes any socket the SSH layer already opened.
 */
interface SessionRegistry {
    /** All currently-open sessions in insertion order. */
    val openSessions: StateFlow<List<Session>>

    /** The focused session id, or null when no session is open. */
    val focusedSessionId: StateFlow<String?>

    /**
     * Establish a session for [profileId]. Routes through
     * `CredentialStore.getPassword` (Keystore, post-PR #171) and the
     * TOFU host-key verifier. Coalesces concurrent callers for the
     * same profile. If a session for this profile is already open,
     * the existing session is focused and returned without a new
     * handshake.
     */
    suspend fun connect(profileId: Long): Result<Session>

    /** Make [sessionId] the focused session. No-op if already focused or unknown. */
    fun focus(sessionId: String)

    /**
     * Tear down [sessionId]. If a connect for the same profile is
     * still in flight, that connect is cancelled and its socket
     * closed. If the disconnected session was focused, focus moves
     * to the next open session (or null if none remain).
     */
    suspend fun disconnect(sessionId: String)
}
