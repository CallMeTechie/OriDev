package dev.ori.app.service

import dev.ori.core.network.ssh.SshClient
import dev.ori.domain.repository.ConnectionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.5 — SSH/SFTP [TransferExecutor] wired to [SshClient]'s
 * resumable overloads (`uploadFileResumable` / `downloadFileResumable`
 * landed in P12.3).
 *
 * The `sessionId` parameter arriving from [TransferWorkerCoroutine] is the
 * stringified `serverProfileId`. This executor resolves it to the
 * currently-active SSHJ session id via [ConnectionRepository]; if no
 * session is active we throw [IllegalStateException] and let the worker's
 * retry machinery handle it (the dispatcher will back off and retry per
 * the user's `maxRetryAttempts` / `retryBackoffSeconds` prefs).
 */
@Singleton
internal class SshTransferExecutor @Inject constructor(
    private val sshClient: SshClient,
    private val connectionRepository: ConnectionRepository,
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
        return connectionRepository.getActiveSessionId(profileId)
            ?: error("SshTransferExecutor: no active SSH session for profile=$profileId")
    }

    private suspend fun resolveActiveSessionIdOrNull(sessionId: String): String? {
        val profileId = sessionId.toLongOrNull() ?: return null
        return connectionRepository.getActiveSessionId(profileId)
    }
}
