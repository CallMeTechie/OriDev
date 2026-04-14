package dev.ori.app.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.4 — skeletal SSH/SFTP [TransferExecutor]. P12.5 wires the
 * real SSHJ-backed calls once P12.3's resumable overloads
 * (`SshClient.uploadFileResumable`, `downloadFileResumable`) are merged.
 *
 * Intentionally throws [NotImplementedError] from every method: no
 * production code calls it in this PR. Tests use a fake [TransferExecutor]
 * injected into [TransferWorkerCoroutine] directly.
 */
@Singleton
internal class SshTransferExecutor @Inject constructor() : TransferExecutor {

    override suspend fun upload(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        throw NotImplementedError("wired in P12.5")
    }

    override suspend fun download(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        throw NotImplementedError("wired in P12.5")
    }

    override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? = null
}
