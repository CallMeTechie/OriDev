package dev.ori.core.network.ssh

import dev.ori.core.network.model.RemoteFile

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

@Suppress("TooManyFunctions")
interface SshClient {
    /**
     * Authenticates against [host]:[port] as [username] using either a
     * [password] char buffer or a [privateKey] byte array.
     *
     * Security contract (Option 5 S1): the [password] buffer is **consumed**
     * by this call — implementations zero-fill it (`Arrays.fill(..., '\u0000')`)
     * in a `try/finally` on both the happy path and on exception, so callers
     * can rely on the buffer being wiped when this method returns.
     *
     * Known limitation: SSHJ's `authPassword` internally accepts a `String`,
     * so the password will transit a JVM `String` object once during the
     * authentication handshake. That `String` is then eligible for GC but
     * may still linger in the string pool until collected. The CharArray
     * buffer owned by the caller IS wiped — that is the part we control.
     */
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray? = null,
        privateKey: ByteArray? = null,
    ): SshSession

    /**
     * Deprecated String overload kept for backward compatibility. Converts
     * the [password] String to a CharArray, delegates to the CharArray
     * variant, and zero-fills the intermediate buffer. The original String
     * itself cannot be wiped and remains in the JVM string pool until GC —
     * new code should use the CharArray variant.
     */
    @Deprecated(
        "Use CharArray variant for zero-fill security",
        ReplaceWith("connect(host, port, username, password.toCharArray(), privateKey)"),
    )
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: String,
        privateKey: ByteArray? = null,
    ): SshSession {
        val chars = password.toCharArray()
        return try {
            connect(host, port, username, chars, privateKey)
        } finally {
            chars.fill('\u0000')
        }
    }

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
