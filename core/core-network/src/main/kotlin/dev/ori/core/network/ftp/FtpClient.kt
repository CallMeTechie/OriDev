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

    suspend fun deleteFile(path: String)

    suspend fun rename(oldPath: String, newPath: String)

    suspend fun mkdir(path: String)
}
