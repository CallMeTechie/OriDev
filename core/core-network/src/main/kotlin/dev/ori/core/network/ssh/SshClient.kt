package dev.ori.core.network.ssh

import dev.ori.core.network.model.RemoteFile

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

interface SshClient {
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray? = null,
        privateKey: ByteArray? = null
    ): SshSession

    suspend fun disconnect(sessionId: String)

    suspend fun isConnected(sessionId: String): Boolean

    suspend fun listFiles(sessionId: String, path: String): List<RemoteFile>

    suspend fun executeCommand(sessionId: String, command: String): CommandResult

    suspend fun uploadFile(sessionId: String, localPath: String, remotePath: String)

    suspend fun downloadFile(sessionId: String, remotePath: String, localPath: String)

    suspend fun deleteFile(sessionId: String, path: String)

    suspend fun rename(sessionId: String, oldPath: String, newPath: String)

    suspend fun mkdir(sessionId: String, path: String)

    suspend fun chmod(sessionId: String, path: String, permissions: Int)
}
