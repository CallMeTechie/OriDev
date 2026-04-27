package dev.ori.app.service

import dev.ori.core.network.ssh.SshClient
import dev.ori.data.di.DefaultSshClient
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.SessionRegistry
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
        sshClient.uploadFileResumable(
            sessionId = resolved,
            localPath = localPath,
            remotePath = remotePath,
            offsetBytes = offsetBytes,
            onProgress = onProgress,
        )
    }

    override suspend fun download(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val resolved = resolveSessionId(sessionId)
        sshClient.downloadFileResumable(
            sessionId = resolved,
            remotePath = remotePath,
            localPath = localPath,
            offsetBytes = offsetBytes,
            onProgress = onProgress,
        )
    }

    override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? {
        val resolved = resolveActiveSessionIdOrNull(sessionId) ?: return null
        return sshClient.fileSize(resolved, remotePath)
    }

    private suspend fun resolveSessionId(sessionId: String): String {
        val profileId = sessionId.toLongOrNull()
            ?: error("SshTransferExecutor: invalid sessionId=$sessionId (expected serverProfileId)")
        connectionRepository.getActiveSessionId(profileId)?.let { return it }
        // Bug J — auto-reconnect: the SSH session has been torn down (idle
        // timeout, network drop, app process killed) since the transfer was
        // queued. Resume should not surface "no active SSH session" to the
        // user when we hold the credentials and host-key TOFU entry; just
        // re-establish the session and use the freshly-issued sessionId.
        return sessionRegistry.connect(profileId).fold(
            onSuccess = { it.id },
            onFailure = { cause ->
                error(
                    "SshTransferExecutor: reconnect failed for profile=$profileId: ${cause.message}",
                )
            },
        )
    }

    private suspend fun resolveActiveSessionIdOrNull(sessionId: String): String? {
        val profileId = sessionId.toLongOrNull() ?: return null
        return connectionRepository.getActiveSessionId(profileId)
    }
}
