package dev.ori.core.network.ftp

import dev.ori.core.network.model.RemoteFile

interface FtpClient {
    /**
     * Connects and authenticates against [host]:[port] as [username] using
     * the [password] char buffer.
     *
     * Security contract (Option 5 S1): the [password] buffer is **consumed**
     * by this call — implementations zero-fill it (`Arrays.fill(..., '\u0000')`)
     * in a `try/finally` on both the happy path and on exception.
     *
     * Known limitation: Apache Commons Net `FTPClient.login` takes a `String`
     * internally, so the password will transit a JVM `String` object once
     * during the login handshake. That `String` is then eligible for GC but
     * may linger in the string pool until collected. The CharArray buffer
     * owned by the caller IS wiped — that is the part we control.
     */
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray,
        useTls: Boolean = false,
    )

    /**
     * Deprecated String overload kept for backward compatibility. Converts
     * the [password] String to a CharArray, delegates to the CharArray
     * variant, and zero-fills the intermediate buffer. The original String
     * remains in the JVM string pool until GC — new code should use the
     * CharArray variant.
     */
    @Deprecated(
        "Use CharArray variant for zero-fill security",
        ReplaceWith("connect(host, port, username, password.toCharArray(), useTls)"),
    )
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: String,
        useTls: Boolean = false,
    ) {
        val chars = password.toCharArray()
        try {
            connect(host, port, username, chars, useTls)
        } finally {
            chars.fill('\u0000')
        }
    }

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

    /**
     * Tier 2 T2a — dedicated resumable upload that owns its own `FTPClient`
     * connection for the duration of the call.
     *
     * Unlike [uploadFileResumable], this overload does NOT touch the
     * singleton-backed `FTPClient` field on [FtpClientImpl]. It creates a
     * fresh `FTPClient` (or `FTPSClient` when [tls] is true), connects +
     * authenticates, performs the resumable upload, and tears the connection
     * down in a `finally` block — making it safe to call from multiple
     * coroutines in parallel. Used by the Transfer Engine's
     * `FtpTransferExecutor` so two concurrent FTP transfers can run without
     * racing on shared field state.
     *
     * Password ownership: the caller retains ownership of the [password]
     * CharArray and is responsible for zero-filling it after the call
     * returns. This method does NOT wipe it — the executor needs to reuse
     * the lookup from [dev.ori.domain.repository.CredentialStore] directly
     * and wipe in its own `finally`.
     */
    suspend fun uploadFileResumableDedicated(
        host: String,
        port: Int,
        username: String,
        password: CharArray,
        tls: Boolean,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    /**
     * Tier 2 T2a — dedicated resumable download that owns its own `FTPClient`
     * connection for the duration of the call. See [uploadFileResumableDedicated]
     * for the connection lifetime and password ownership contract.
     */
    suspend fun downloadFileResumableDedicated(
        host: String,
        port: Int,
        username: String,
        password: CharArray,
        tls: Boolean,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    /**
     * Phase 12 P12.5 — minimal stat helper the transfer engine calls to
     * decide whether a remote destination already exists (overwrite policy).
     * Returns `null` when [remotePath] does not exist, is a directory, or
     * cannot be stat-ed via the FTP LIST command.
     */
    suspend fun fileSize(remotePath: String): Long?

    suspend fun deleteFile(path: String)

    suspend fun rename(oldPath: String, newPath: String)

    suspend fun mkdir(path: String)
}
