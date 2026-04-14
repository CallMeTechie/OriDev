package dev.ori.core.network.ssh

import dev.ori.core.network.model.RemoteFile

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

@Suppress("TooManyFunctions")
interface SshClient {
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray? = null,
        privateKey: ByteArray? = null,
    ): SshSession

    suspend fun disconnect(sessionId: String)

    suspend fun isConnected(sessionId: String): Boolean

    suspend fun listFiles(sessionId: String, path: String): List<RemoteFile>

    suspend fun executeCommand(sessionId: String, command: String): CommandResult

    suspend fun uploadFile(
        sessionId: String,
        localPath: String,
        remotePath: String,
        onProgress: (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    suspend fun downloadFile(
        sessionId: String,
        remotePath: String,
        localPath: String,
        onProgress: (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    /**
     * Resumable upload overload: streams bytes from [localPath] starting at
     * [offsetBytes] and appends them to [remotePath].
     *
     * Used by the Transfer Engine (Phase 12) to resume a paused transfer without
     * re-sending bytes that already made it to the destination. Throws on any
     * SSHJ/IO error; on success, [remotePath] contains exactly `offsetBytes +
     * (localSize - offsetBytes)` bytes.
     */
    suspend fun uploadFileResumable(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    /**
     * Resumable download overload: streams bytes from [remotePath] starting at
     * [offsetBytes] and writes them into [localPath] at the same offset.
     *
     * Used by the Transfer Engine (Phase 12) to resume a paused download. Throws
     * on any SSHJ/IO error.
     */
    suspend fun downloadFileResumable(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    /**
     * Phase 12 P12.5 — minimal stat helper the transfer engine calls to
     * decide whether a remote destination already exists (overwrite policy)
     * and whether an in-progress resume offset matches the server-side size.
     * Returns `null` when [remotePath] does not exist or cannot be stat-ed.
     */
    suspend fun fileSize(sessionId: String, remotePath: String): Long?

    suspend fun deleteFile(sessionId: String, path: String)

    suspend fun rename(sessionId: String, oldPath: String, newPath: String)

    suspend fun mkdir(sessionId: String, path: String)

    suspend fun chmod(sessionId: String, path: String, permissions: Int)

    suspend fun openShell(
        sessionId: String,
        cols: Int = 80,
        rows: Int = 24,
    ): ShellHandle
}
