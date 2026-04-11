package dev.ori.core.network.ssh

import dev.ori.core.network.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
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
    }
}
