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
 * The registry is intended to be a process-wide singleton (bound via
 * Hilt in `:data`). All three flows are hot and survive ViewModel
 * recreation. [connect] is idempotent per [Session.profileId] —
 * concurrent calls for the same profile share one in-flight
 * `Deferred`. Mid-handshake teardown goes through [cancelConnect]
 * (sessionId doesn't exist yet during handshake); the symmetric path
 * [disconnect] operates on sessions that are already registered.
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
     * Tear down [sessionId]. No-op if [sessionId] is not in
     * [openSessions] — a mid-handshake session has no id yet, so
     * callers who want to abort a pending handshake must use
     * [cancelConnect] with the profileId instead.
     *
     * If the session's profile also has an in-flight connect Deferred
     * (e.g. an auto-reconnect racing with user teardown), that Deferred
     * is cancelled so a late-landing handshake cannot re-insert the
     * session. If the disconnected session was focused, focus moves to
     * the next open session (or null if none remain).
     */
    suspend fun disconnect(sessionId: String)

    /**
     * Cancel a still-in-flight [connect] for [profileId]. No-op if no
     * handshake is pending for that profile. Closes any socket SSHJ
     * already opened via the connect-coroutine's cancellation cleanup.
     *
     * Exists because [disconnect] takes a `sessionId`, which doesn't
     * exist yet during handshake. The UI path that surfaces this is:
     * a ViewModel calling `connect()` on another coroutine, then a
     * "Cancel"/back-press triggering `cancelConnect(profileId)` from
     * the same ViewModel.
     */
    suspend fun cancelConnect(profileId: Long)
}
