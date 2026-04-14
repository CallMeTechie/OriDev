package dev.ori.app.service

/**
 * Phase 12 P12.4 — thin internal abstraction the per-transfer
 * [TransferWorkerCoroutine] calls to actually move bytes. Has two concrete
 * implementations ([SshTransferExecutor], [FtpTransferExecutor]) that are
 * skeletal in this PR: they throw [NotImplementedError] until P12.5 wires
 * them to the resumable overloads on `SshClient` / `FtpClient` that land in
 * P12.3.
 *
 * Tests pass a fake [TransferExecutor] directly into the worker, so the
 * skeletons are never invoked from production code paths in this PR.
 *
 * Contract:
 *  - [upload] / [download] honour [offsetBytes] by seeking / APPEND-ing on
 *    the remote side and `RandomAccessFile` on the local side.
 *  - [onProgress] is invoked with `(transferredSoFar, total)` in bytes; the
 *    worker throttles writes to Room at ~500 ms via a flow sample.
 *  - [remoteFileSize] returns the existing remote size for overwrite-policy
 *    decisions, or `null` when the path does not exist.
 */
internal interface TransferExecutor {
    suspend fun upload(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    )

    suspend fun download(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    )

    suspend fun remoteFileSize(sessionId: String, remotePath: String): Long?
}
