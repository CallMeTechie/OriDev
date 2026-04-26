package dev.ori.core.network.ssh

import dev.ori.core.common.model.Protocol
import dev.ori.core.network.model.DeleteResult
import dev.ori.core.network.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.SFTPClient
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshSftpClientImpl @Inject constructor(
    private val sessionStore: SshSessionStore,
) : SshClient {

    /**
     * See [SshClient.connect] for the security contract — [SshSessionStore]
     * zero-fills [password] in a `try/finally` on both success and failure
     * paths. The intermediate `String(password)` passed to SSHJ's
     * `authPassword` is a limitation of the SSHJ API and is tracked as a
     * follow-up (Option 5 S1 known limitation).
     */
    override suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray?,
        privateKey: ByteArray?,
        protocol: Protocol,
    ): SshSession = sessionStore.connect(host, port, username, password, privateKey, protocol)

    override suspend fun disconnect(sessionId: String) {
        sessionStore.disconnect(sessionId)
    }

    override suspend fun isConnected(sessionId: String): Boolean =
        sessionStore.isConnected(sessionId)

    override suspend fun listFiles(sessionId: String, path: String): List<RemoteFile> {
        return withSftpClient(sessionId) { sftp ->
            sftp.ls(path).map { entry ->
                RemoteFile(
                    name = entry.name,
                    path = "$path/${entry.name}",
                    isDirectory = entry.isDirectory,
                    size = entry.attributes.size,
                    lastModified = entry.attributes.mtime * 1000L,
                    permissions = entry.attributes.permissions?.toString().orEmpty(),
                    owner = entry.attributes.uid.toString(),
                )
            }
        }
    }

    override suspend fun executeCommand(sessionId: String, command: String): CommandResult {
        val client = sessionStore.getSession(sessionId).client
        val session = client.startSession()
        return try {
            val cmd = session.exec(command)
            val stdout = cmd.inputStream.bufferedReader().readText()
            val stderr = cmd.errorStream.bufferedReader().readText()
            cmd.join()
            CommandResult(
                exitCode = cmd.exitStatus ?: -1,
                stdout = stdout,
                stderr = stderr,
            )
        } finally {
            session.close()
        }
    }

    override suspend fun uploadFile(
        sessionId: String,
        localPath: String,
        remotePath: String,
        onProgress: (transferred: Long, total: Long) -> Unit,
    ) {
        withSftpClient(sessionId) { sftp ->
            val localFile = java.io.File(localPath)
            val totalBytes = localFile.length()
            val remoteFile = sftp.open(
                remotePath,
                java.util.EnumSet.of(
                    net.schmizz.sshj.sftp.OpenMode.WRITE,
                    net.schmizz.sshj.sftp.OpenMode.CREAT,
                    net.schmizz.sshj.sftp.OpenMode.TRUNC,
                ),
            )
            try {
                val outputStream = remoteFile.RemoteFileOutputStream()
                localFile.inputStream().use { input ->
                    val buffer = ByteArray(TRANSFER_BUFFER_SIZE)
                    var transferred = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        transferred += bytesRead
                        onProgress(transferred, totalBytes)
                    }
                    outputStream.flush()
                }
            } finally {
                remoteFile.close()
            }
        }
    }

    override suspend fun downloadFile(
        sessionId: String,
        remotePath: String,
        localPath: String,
        onProgress: (transferred: Long, total: Long) -> Unit,
    ) {
        withSftpClient(sessionId) { sftp ->
            val attrs = sftp.stat(remotePath)
            val totalBytes = attrs.size
            val remoteFile = sftp.open(remotePath)
            try {
                val inputStream = remoteFile.RemoteFileInputStream()
                java.io.File(localPath).outputStream().use { output ->
                    val buffer = ByteArray(TRANSFER_BUFFER_SIZE)
                    var transferred = 0L
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        transferred += bytesRead
                        onProgress(transferred, totalBytes)
                    }
                    output.flush()
                }
            } finally {
                remoteFile.close()
            }
        }
    }

    /**
     * Resumable upload via direct positional `RemoteFile.write(offset, ...)`.
     *
     * Q2 decision: rather than relying on SSHJ's `APPEND` OpenMode — whose
     * semantics vary across SFTP server implementations — we open the remote
     * file with `WRITE | CREAT` and issue writes at an explicit offset using
     * `RemoteFile.write(fileOffset, buf, 0, n)`. This gives us byte-exact
     * placement and is independent of server-side append handling.
     *
     * Safety guard: when `offsetBytes > 0`, we verify the existing remote file
     * size equals `offsetBytes`. If it does not match, we fall back to a full
     * upload from offset 0 (TRUNC) to avoid corrupting a partially-written
     * destination. This matches the plan's fallback strategy.
     */
    override suspend fun uploadFileResumable(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val sftp = sessionStore.getSession(sessionId).client.newSFTPClient()
        try {
            val localFile = java.io.File(localPath)
            val localSize = localFile.length()

            val existingRemoteSize: Long = if (offsetBytes > 0L) {
                try {
                    sftp.stat(remotePath).size
                } catch (_: Exception) {
                    -1L
                }
            } else {
                0L
            }

            val useResume = offsetBytes > 0L && existingRemoteSize == offsetBytes
            val startOffset = if (useResume) offsetBytes else 0L
            val openModes = if (useResume) {
                java.util.EnumSet.of(
                    net.schmizz.sshj.sftp.OpenMode.WRITE,
                    net.schmizz.sshj.sftp.OpenMode.CREAT,
                )
            } else {
                java.util.EnumSet.of(
                    net.schmizz.sshj.sftp.OpenMode.WRITE,
                    net.schmizz.sshj.sftp.OpenMode.CREAT,
                    net.schmizz.sshj.sftp.OpenMode.TRUNC,
                )
            }
            val totalBytes = localSize

            val remoteFile = sftp.open(remotePath, openModes)
            try {
                RandomAccessFile(localFile, "r").use { raf ->
                    raf.seek(startOffset)
                    val buffer = ByteArray(CHUNK_SIZE)
                    var fileOffset = startOffset
                    var transferred = startOffset
                    while (true) {
                        val read = raf.read(buffer)
                        if (read <= 0) break
                        remoteFile.write(fileOffset, buffer, 0, read)
                        fileOffset += read
                        transferred += read
                        onProgress(transferred, totalBytes)
                    }
                }
            } finally {
                remoteFile.close()
            }
        } finally {
            sftp.close()
        }
    }

    /**
     * Resumable download via direct positional `RemoteFile.read(offset, ...)`.
     *
     * Opens the remote file for READ and pulls chunks at explicit offsets; on
     * the local side a `RandomAccessFile` is seeked to `offsetBytes` so bytes
     * land at the correct position. When the local file does not already have
     * `offsetBytes` bytes, the offset is capped to the actual local size and
     * the download restarts from that position — preventing a sparse/corrupt
     * local file.
     */
    override suspend fun downloadFileResumable(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val sftp = sessionStore.getSession(sessionId).client.newSFTPClient()
        try {
            val remoteFile = sftp.open(remotePath)
            try {
                val totalBytes = remoteFile.length()
                val localFile = java.io.File(localPath)
                val existingLocalSize = if (localFile.exists()) localFile.length() else 0L
                val startOffset = when {
                    offsetBytes <= 0L -> 0L
                    existingLocalSize == offsetBytes -> offsetBytes
                    else -> 0L
                }
                RandomAccessFile(localFile, "rw").use { raf ->
                    if (startOffset == 0L) {
                        raf.setLength(0L)
                    }
                    raf.seek(startOffset)
                    val buffer = ByteArray(CHUNK_SIZE)
                    var fileOffset = startOffset
                    var transferred = startOffset
                    while (fileOffset < totalBytes) {
                        val read = remoteFile.read(fileOffset, buffer, 0, buffer.size)
                        if (read <= 0) break
                        raf.write(buffer, 0, read)
                        fileOffset += read
                        transferred += read
                        onProgress(transferred, totalBytes)
                    }
                }
            } finally {
                remoteFile.close()
            }
        } finally {
            sftp.close()
        }
    }

    override suspend fun fileSize(sessionId: String, remotePath: String): Long? =
        withContext(Dispatchers.IO) {
            withSftpClient(sessionId) { sftp ->
                try {
                    sftp.stat(remotePath).size
                } catch (_: Exception) {
                    null
                }
            }
        }

    override suspend fun delete(sessionId: String, paths: List<String>): DeleteResult =
        withContext(Dispatchers.IO) {
            val ok = mutableListOf<String>()
            val bad = mutableListOf<Pair<String, String>>()
            withSftpClient(sessionId) { sftp ->
                for (p in paths) {
                    try {
                        sftp.rm(p)
                        ok += p
                    } catch (e: Exception) {
                        bad += p to (e.message ?: e.javaClass.simpleName)
                    }
                }
            }
            DeleteResult(ok, bad)
        }

    override suspend fun uploadFile(
        sessionId: String,
        sourceUri: android.net.Uri,
        remotePath: String,
        contentResolver: android.content.ContentResolver,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val length = try {
            contentResolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) {
            0L
        }
        val src = SafSourceFile(
            sourceUri,
            contentResolver,
            length,
            sourceUri.lastPathSegment ?: "upload",
        )
        withSftpClient(sessionId) { sftp -> sftp.fileTransfer.upload(src, remotePath) }
    }

    // mirror downloadFile, also streamed via SafDestFile per Decision 10
    override suspend fun downloadFile(
        sessionId: String,
        remotePath: String,
        destUri: android.net.Uri,
        contentResolver: android.content.ContentResolver,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val dst = SafDestFile(destUri, contentResolver, destUri.lastPathSegment ?: "download")
        withSftpClient(sessionId) { sftp -> sftp.fileTransfer.download(remotePath, dst) }
    }

    override suspend fun rename(sessionId: String, oldPath: String, newPath: String) {
        withSftpClient(sessionId) { sftp ->
            sftp.rename(oldPath, newPath)
        }
    }

    override suspend fun mkdir(sessionId: String, path: String) {
        withSftpClient(sessionId) { sftp ->
            sftp.mkdir(path)
        }
    }

    override suspend fun chmod(sessionId: String, path: String, permissions: Int) {
        withSftpClient(sessionId) { sftp ->
            sftp.chmod(path, permissions)
        }
    }

    override suspend fun openShell(
        sessionId: String,
        cols: Int,
        rows: Int,
    ): ShellHandle = withContext(Dispatchers.IO) {
        val client = sessionStore.getSession(sessionId).client
        SshShellManager().openShell(client, cols, rows)
    }

    // Defense-in-depth: `SshSessionStore.getSession` already rejects disconnected
    // clients, but we also gate here so every SFTP operation gets the same
    // IOException (not SSHJ's internal IllegalStateException) if the session
    // was already cleaned up before we reach `newSFTPClient()`.
    //
    // SSHJ's `newSFTPClient()` opens a fresh SFTP channel which writes to the
    // socket synchronously, and every `SFTPClient` op below similarly does
    // blocking network I/O. Without the explicit IO switch all eight callers
    // (listFiles, uploadFile, downloadFile, mkdir, rename, chmod, delete,
    // getFileContent) ran on the caller's coroutine context — which from
    // `FileManagerViewModel.viewModelScope` is `Dispatchers.Main`. That tripped
    // `NetworkOnMainThreadException` on Pixel Fold (API 36); reproduced four
    // times in oridev-error-listfiles-right-2026-04-25-22-04-*.txt.
    private suspend fun <T> withSftpClient(sessionId: String, block: (SFTPClient) -> T): T =
        withContext(Dispatchers.IO) {
            val client = sessionStore.getSession(sessionId).client
            val sftp = client.newSFTPClient()
            try {
                block(sftp)
            } finally {
                sftp.close()
            }
        }

    companion object {
        private const val TRANSFER_BUFFER_SIZE = 32_768
        private const val CHUNK_SIZE = 32 * 1024
    }
}
