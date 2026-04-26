package dev.ori.core.network.ssh

import android.content.ContentResolver
import android.net.Uri
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.model.DeleteResult
import dev.ori.core.network.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("LongParameterList")
class SshScpClientImpl @Inject constructor(
    private val sessionStore: SshSessionStore,
) : SshClient {

    override suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray?,
        privateKey: ByteArray?,
        protocol: Protocol,
    ): SshSession = sessionStore.connect(host, port, username, password, privateKey, protocol)

    override suspend fun disconnect(sessionId: String) = sessionStore.disconnect(sessionId)

    override suspend fun isConnected(sessionId: String): Boolean = sessionStore.isConnected(sessionId)

    override suspend fun openShell(sessionId: String, cols: Int, rows: Int): ShellHandle =
        withContext(Dispatchers.IO) {
            val client = sessionStore.getSession(sessionId).client
            val session = client.startSession()
            session.allocatePTY("xterm", cols, rows, 0, 0, emptyMap())
            val shell = session.startShell()
            val shellSession = SshShellSession(session, shell)
            val shellId = UUID.randomUUID().toString()
            ShellHandle(
                shellId = shellId,
                inputStream = shellSession.inputStream,
                outputStream = shellSession.outputStream,
                onResize = { c, r -> shellSession.resize(c, r) },
                onClose = { shellSession.close() },
            )
        }

    override suspend fun executeCommand(sessionId: String, command: String): CommandResult =
        withContext(Dispatchers.IO) {
            val live = sessionStore.getSession(sessionId)
            val r = ShellInvocation.run(live.client, command, live.bashAvailable)
            CommandResult(exitCode = r.exitCode, stdout = r.stdout, stderr = r.stderr)
        }

    override suspend fun listFiles(sessionId: String, path: String): List<RemoteFile> =
        withContext(Dispatchers.IO) {
            val live = sessionStore.getSession(sessionId)
            val cache = sessionStore.ensureNameCache(sessionId)
            val cmd = "LANG=C ls -la --numeric-uid-gid --time-style='+%Y-%m-%dT%H:%M:%S' ${shellEscape(path)}"
            val r = ShellInvocation.run(live.client, cmd, live.bashAvailable)
            if (r.exitCode != 0) {
                val first = r.stderr.lineSequence().firstOrNull()?.trim().orEmpty()
                throw java.io.IOException("ls failed: ${first.ifEmpty { "exit ${r.exitCode}" }}")
            }
            ScpListingParser.parse(r.stdout, parentPath = path, nameCache = cache)
        }

    override suspend fun uploadFile(
        sessionId: String,
        localPath: String,
        remotePath: String,
        onProgress: (Long, Long) -> Unit,
    ) = TODO("Task 12")

    override suspend fun uploadFile(
        sessionId: String,
        sourceUri: Uri,
        remotePath: String,
        contentResolver: ContentResolver,
        onProgress: (Long, Long) -> Unit,
    ) = TODO("Task 12")

    override suspend fun downloadFile(
        sessionId: String,
        remotePath: String,
        localPath: String,
        onProgress: (Long, Long) -> Unit,
    ) = TODO("Task 12")

    override suspend fun downloadFile(
        sessionId: String,
        remotePath: String,
        destUri: Uri,
        contentResolver: ContentResolver,
        onProgress: (Long, Long) -> Unit,
    ) = TODO("Task 12")

    override suspend fun uploadFileResumable(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ): Unit = throw UnsupportedOperationException("SCP does not support resume")

    override suspend fun downloadFileResumable(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ): Unit = throw UnsupportedOperationException("SCP does not support resume")

    override suspend fun mkdir(sessionId: String, path: String) = TODO("Task 13")

    override suspend fun rename(sessionId: String, oldPath: String, newPath: String) = TODO("Task 13")

    override suspend fun chmod(sessionId: String, path: String, permissions: Int) = TODO("Task 13")

    override suspend fun delete(sessionId: String, paths: List<String>): DeleteResult = TODO("Task 14")

    override suspend fun fileSize(sessionId: String, remotePath: String): Long? = TODO("Task 13")
}

internal fun shellEscape(path: String): String = "'" + path.replace("'", "'\\''") + "'"
