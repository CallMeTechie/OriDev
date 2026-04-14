package dev.ori.core.network.ssh

import dev.ori.core.network.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshClientImpl @Inject constructor(
    private val hostKeyVerifier: OriDevHostKeyVerifier,
    private val shellManager: SshShellManager,
) : SshClient {

    private val sessions = ConcurrentHashMap<String, SSHClient>()

    override suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray?,
        privateKey: ByteArray?,
    ): SshSession {
        val client = SSHClient()
        client.addHostKeyVerifier(hostKeyVerifier)
        client.connect(host, port)

        try {
            when {
                privateKey != null -> {
                    val keyProvider = PKCS8KeyFile()
                    keyProvider.init(InputStreamReader(ByteArrayInputStream(privateKey)))
                    client.authPublickey(username, keyProvider)
                }
                password != null -> {
                    client.authPassword(username, String(password))
                }
                else -> {
                    throw IllegalArgumentException("Either password or private key must be provided")
                }
            }

            client.connection.keepAlive.keepAliveInterval = KEEPALIVE_INTERVAL_SECONDS

            val sessionId = UUID.randomUUID().toString()
            sessions[sessionId] = client

            return SshSession(
                sessionId = sessionId,
                profileId = 0,
                host = host,
                port = port,
                connectedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            client.close()
            throw e
        }
    }

    override suspend fun disconnect(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    override suspend fun isConnected(sessionId: String): Boolean {
        return sessions[sessionId]?.isConnected == true
    }

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
        val client = getClient(sessionId)
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
        val sftp = getClient(sessionId).newSFTPClient()
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
        val sftp = getClient(sessionId).newSFTPClient()
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

    override suspend fun deleteFile(sessionId: String, path: String) {
        withSftpClient(sessionId) { sftp ->
            sftp.rm(path)
        }
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
        val client = getClient(sessionId)
        shellManager.openShell(client, cols, rows)
    }

    private fun getClient(sessionId: String): SSHClient {
        return sessions[sessionId]
            ?: throw IllegalStateException("No active session with id: $sessionId")
    }

    private fun <T> withSftpClient(sessionId: String, block: (SFTPClient) -> T): T {
        val client = getClient(sessionId)
        val sftp = client.newSFTPClient()
        return try {
            block(sftp)
        } finally {
            sftp.close()
        }
    }

    companion object {
        private const val KEEPALIVE_INTERVAL_SECONDS = 15
        private const val TRANSFER_BUFFER_SIZE = 32_768
        private const val CHUNK_SIZE = 32 * 1024
    }
}
