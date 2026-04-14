package dev.ori.core.network.ftp

import dev.ori.core.network.model.RemoteFile

interface FtpClient {
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray,
        useTls: Boolean = false,
    )

    suspend fun disconnect()

    val isConnected: Boolean

    suspend fun listFiles(path: String): List<RemoteFile>

    suspend fun uploadFile(
        localPath: String,
        remotePath: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    )

    suspend fun downloadFile(
        remotePath: String,
        localPath: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    )

    /**
     * Resumable upload overload: uploads the local file starting at
     * [offsetBytes], appending to an existing remote file via FTP REST.
     *
     * Used by the Transfer Engine (Phase 12). The implementation calls
     * `FTPClient.setRestartOffset(offsetBytes)` and skips `offsetBytes` of the
     * local stream before invoking `storeFile`. Throws `IOException` on any
     * FTP protocol failure.
     */
    suspend fun uploadFileResumable(
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    /**
     * Resumable download overload: downloads [remotePath] starting at
     * [offsetBytes], writing into the local file at the same offset via FTP
     * REST. Throws `IOException` on any FTP protocol failure.
     */
    suspend fun downloadFileResumable(
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    suspend fun deleteFile(path: String)

    suspend fun rename(oldPath: String, newPath: String)

    suspend fun mkdir(path: String)
}
