package dev.ori.app.service

import dev.ori.core.network.ftp.FtpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.5 — FTP/FTPS [TransferExecutor] wired to [FtpClient]'s
 * resumable overloads (`uploadFileResumable` / `downloadFileResumable`
 * landed in P12.3).
 *
 * TODO(P12.7+): per the plan's Q3 decision, each transfer should ultimately
 * open its own dedicated `FtpClient` connection to avoid serialising all
 * FTP transfers through the singleton Apache-Commons-Net client. For P12.5
 * we reuse the app-scoped singleton — the parallelism budget is still
 * honoured at the [TransferDispatcher] level — and will split the backing
 * field into a `FtpClientFactory` in a follow-up PR once the connection
 * lifecycle is wired into `ConnectionRepository` for FTP.
 */
@Singleton
internal class FtpTransferExecutor @Inject constructor(
    private val ftpClient: FtpClient,
) : TransferExecutor {

    override suspend fun upload(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        ftpClient.uploadFileResumable(
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
        ftpClient.downloadFileResumable(
            remotePath = remotePath,
            localPath = localPath,
            offsetBytes = offsetBytes,
            onProgress = onProgress,
        )
    }

    override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? =
        if (ftpClient.isConnected) ftpClient.fileSize(remotePath) else null
}
