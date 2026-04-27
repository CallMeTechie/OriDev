package dev.ori.app.service

import dev.ori.core.network.ssh.SshClient
import dev.ori.data.di.DefaultSshClient
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.SessionRegistry
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.5 — SSH/SFTP [TransferExecutor] wired to [SshClient]'s
 * resumable overloads (`uploadFileResumable` / `downloadFileResumable`
 * landed in P12.3).
 *
 * The `sessionId` parameter arriving from [TransferWorkerCoroutine] is the
 * stringified `serverProfileId`. This executor resolves it to the
 * currently-active SSHJ session id via [ConnectionRepository]; if the
 * session has been closed in the meantime (e.g. user resumed a queued
 * transfer hours after disconnecting), the executor transparently asks
 * [SessionRegistry] for a fresh handshake before failing — see Bug J.
 * Only when reconnect itself fails do we throw [IllegalStateException]
 * and let the worker's retry machinery handle it (the dispatcher will
 * back off and retry per the user's `maxRetryAttempts` /
 * `retryBackoffSeconds` prefs).
 *
 * Bug O — the Bug J fix only handled the "no active session at all" case
 * (where `getActiveSessionId == null`). It missed two adjacent failure
 * modes that the user hit when pressing "Retry" after a Wi-Fi blip:
 *  1. The session id is still in [ConnectionRepository] but the underlying
 *     SSHJ client is half-dead. `SshSessionStore.getSession()` raises
 *     `IOException("SSH session terminated")` (or `"No active SSH session"`
 *     if the disconnect listener already pruned it).
 *  2. SSHJ's own `SSHClient.startSession()` raises
 *     `IllegalStateException("Not connected")` from `checkConnected()` if
 *     the transport closed between our `isConnected` probe and the channel
 *     open. Same root cause, different layer.
 * Both now re-route through [forceReconnect] so the worker sees a fresh,
 * usable session instead of a fatal exception bubbling out of the
 * transfer coroutine.
 */
@Singleton
internal class SshTransferExecutor @Inject constructor(
    @DefaultSshClient private val sshClient: SshClient,
    private val connectionRepository: ConnectionRepository,
    private val sessionRegistry: SessionRegistry,
) : TransferExecutor {

    override suspend fun upload(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val resolved = resolveSessionId(sessionId)
        try {
            sshClient.uploadFileResumable(
                sessionId = resolved,
                localPath = localPath,
                remotePath = remotePath,
                offsetBytes = offsetBytes,
                onProgress = onProgress,
            )
        } catch (e: IllegalStateException) {
            if (!isStaleSessionStateException(e)) throw e
            val freshId = forceReconnect(sessionId)
            sshClient.uploadFileResumable(
                sessionId = freshId,
                localPath = localPath,
                remotePath = remotePath,
                offsetBytes = offsetBytes,
                onProgress = onProgress,
            )
        } catch (e: IOException) {
            if (!isTerminatedSessionIoException(e)) throw e
            val freshId = forceReconnect(sessionId)
            sshClient.uploadFileResumable(
                sessionId = freshId,
                localPath = localPath,
                remotePath = remotePath,
                offsetBytes = offsetBytes,
                onProgress = onProgress,
            )
        }
    }

    override suspend fun download(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val resolved = resolveSessionId(sessionId)
        try {
            sshClient.downloadFileResumable(
                sessionId = resolved,
                remotePath = remotePath,
                localPath = localPath,
                offsetBytes = offsetBytes,
                onProgress = onProgress,
            )
        } catch (e: IllegalStateException) {
            if (!isStaleSessionStateException(e)) throw e
            val freshId = forceReconnect(sessionId)
            sshClient.downloadFileResumable(
                sessionId = freshId,
                remotePath = remotePath,
                localPath = localPath,
                offsetBytes = offsetBytes,
                onProgress = onProgress,
            )
        } catch (e: IOException) {
            if (!isTerminatedSessionIoException(e)) throw e
            val freshId = forceReconnect(sessionId)
            sshClient.downloadFileResumable(
                sessionId = freshId,
                remotePath = remotePath,
                localPath = localPath,
                offsetBytes = offsetBytes,
                onProgress = onProgress,
            )
        }
    }

    override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? {
        val resolved = resolveActiveSessionIdOrNull(sessionId) ?: return null
        return sshClient.fileSize(resolved, remotePath)
    }

    private suspend fun resolveSessionId(sessionId: String): String {
        val profileId = parseProfileId(sessionId)
        connectionRepository.getActiveSessionId(profileId)?.let { return it }
        // Bug J — auto-reconnect: the SSH session has been torn down (idle
        // timeout, network drop, app process killed) since the transfer was
        // queued. Resume should not surface "no active SSH session" to the
        // user when we hold the credentials and host-key TOFU entry; just
        // re-establish the session and use the freshly-issued sessionId.
        return reconnect(profileId)
    }

    private suspend fun resolveActiveSessionIdOrNull(sessionId: String): String? {
        val profileId = sessionId.toLongOrNull() ?: return null
        return connectionRepository.getActiveSessionId(profileId)
    }

    /**
     * Bug O — used after a transfer call observed a stale-session error.
     * Tears the half-dead session out of [ConnectionRepository]'s map (if
     * any) and asks [SessionRegistry] for a brand-new handshake. Returns
     * the freshly-issued SSHJ session id ready to be passed back into the
     * SFTP overload that just failed.
     */
    private suspend fun forceReconnect(sessionId: String): String {
        val profileId = parseProfileId(sessionId)
        return reconnect(profileId)
    }

    private suspend fun reconnect(profileId: Long): String =
        sessionRegistry.connect(profileId).fold(
            onSuccess = { it.id },
            onFailure = { cause ->
                error(
                    "SshTransferExecutor: reconnect failed for profile=$profileId: ${cause.message}",
                )
            },
        )

    private fun parseProfileId(sessionId: String): Long =
        sessionId.toLongOrNull()
            ?: error("SshTransferExecutor: invalid sessionId=$sessionId (expected serverProfileId)")

    private fun isStaleSessionStateException(e: IllegalStateException): Boolean {
        val message = e.message ?: return false
        return STALE_SESSION_MARKERS.any { message.contains(it, ignoreCase = true) }
    }

    private fun isTerminatedSessionIoException(e: IOException): Boolean {
        val message = e.message ?: return false
        return TERMINATED_SESSION_MARKERS.any { message.contains(it, ignoreCase = true) }
    }

    private companion object {
        // Markers for SSHJ's own checkConnected() (`IllegalStateException("Not connected")`)
        // and any state-error pre-check that effectively means "the session this id
        // pointed at has gone away under us".
        private val STALE_SESSION_MARKERS = listOf(
            "Not connected",
            "session terminated",
        )

        // Markers for SshSessionStore.getSession()'s defensive throws — we treat
        // both "session was pruned" and "client.isConnected returned false" the same
        // way: drop the dead handle and reconnect through the registry.
        private val TERMINATED_SESSION_MARKERS = listOf(
            "session terminated",
            "No active SSH session",
        )
    }
}
