package dev.ori.core.network.ssh

import android.content.ContentResolver
import android.net.Uri
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.model.DeleteResult
import dev.ori.core.network.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
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
            // Bug Q fix — serialise via channelOpenMutex (see
            // SshSftpClientImpl.openShell). Concurrent shell-opens on the
            // same SSHClient drove SSHJ's Reader thread into a state where
            // the transport tore down with a local-side EOF.
            val live = sessionStore.getSession(sessionId)
            val session = live.channelOpenMutex.withLock {
                live.client.startSession()
            }
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
    ) = withContext(Dispatchers.IO) {
        val client = sessionStore.getSession(sessionId).client
        client.newSCPFileTransfer()
            .upload(net.schmizz.sshj.xfer.FileSystemFile(java.io.File(localPath)), remotePath)
    }

    override suspend fun uploadFile(
        sessionId: String,
        sourceUri: Uri,
        remotePath: String,
        contentResolver: ContentResolver,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val client = sessionStore.getSession(sessionId).client
        val length = try {
            contentResolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) { 0L }
        val src = SafSourceFile(
            sourceUri,
            contentResolver,
            length,
            safDisplayName(sourceUri, contentResolver, fallback = "upload"),
        )
        client.newSCPFileTransfer().upload(src, remotePath)
    }

    override suspend fun downloadFile(
        sessionId: String,
        remotePath: String,
        localPath: String,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val client = sessionStore.getSession(sessionId).client
        client.newSCPFileTransfer()
            .download(remotePath, net.schmizz.sshj.xfer.FileSystemFile(java.io.File(localPath)))
    }

    override suspend fun downloadFile(
        sessionId: String,
        remotePath: String,
        destUri: Uri,
        contentResolver: ContentResolver,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val client = sessionStore.getSession(sessionId).client
        val dst = SafDestFile(destUri, contentResolver, safDisplayName(destUri, contentResolver, fallback = "download"))
        client.newSCPFileTransfer().download(remotePath, dst)
    }

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

    override suspend fun mkdir(sessionId: String, path: String) =
        runShellOrFail(sessionId, "mkdir", "mkdir -p ${shellEscape(path)}")

    override suspend fun rename(sessionId: String, oldPath: String, newPath: String) =
        runShellOrFail(sessionId, "rename", "mv -- ${shellEscape(oldPath)} ${shellEscape(newPath)}")

    override suspend fun chmod(sessionId: String, path: String, permissions: Int) {
        val asOctal = Integer.toOctalString(permissions).padStart(3, '0')
        runShellOrFail(sessionId, "chmod", "chmod $asOctal ${shellEscape(path)}")
    }

    override suspend fun delete(sessionId: String, paths: List<String>): DeleteResult =
        withContext(Dispatchers.IO) {
            if (paths.isEmpty()) return@withContext DeleteResult.EMPTY
            val live = sessionStore.getSession(sessionId)
            var aggregate = DeleteResult.EMPTY
            for (batch in paths.chunked(MAX_BATCH_ARGS)) {
                val joined = batch.joinToString(" ") { shellEscape(it) }
                val r = ShellInvocation.run(live.client, "rm -- $joined", live.bashAvailable)
                aggregate = aggregate.merge(parseRm(batch, r))
            }
            aggregate
        }

    private fun parseRm(batch: List<String>, r: ShellResult): DeleteResult {
        if (r.exitCode == 0) return DeleteResult(succeeded = batch, failed = emptyList())
        // Robust matcher: GNU rm's failure line shape is `rm: cannot remove 'X': REASON`,
        // where X may contain shell-escaped single quotes (`'\''`). A naive `[^']+` regex
        // truncates the path at the first quote and silently drops the entry from both
        // buckets. Instead match by anchored prefix/suffix and unescape inside.
        val prefix = "rm: cannot remove '"
        val failed = r.stderr.lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith(prefix)) return@mapNotNull null
            // After prefix, the path is everything up to the LAST occurrence of `': `.
            val end = line.lastIndexOf("': ")
            if (end < prefix.length) return@mapNotNull null
            val pathEscaped = line.substring(prefix.length, end)
            val reason = line.substring(end + 3)
            val path = pathEscaped.replace("'\\''", "'") // un-do shellEscape's POSIX trick
            path to reason
        }.toList()
        val failedSet = failed.map { it.first }.toSet()
        if (failed.isEmpty()) {
            // Non-zero exit but no parseable failure lines (rate-limit, OOM on remote shell,
            // generic error) — treat the entire batch as failed with a synthetic reason.
            return DeleteResult(
                succeeded = emptyList(),
                failed = batch.map { it to "rm exited ${r.exitCode}" },
            )
        }
        return DeleteResult(succeeded = batch.filter { it !in failedSet }, failed = failed)
    }

    override suspend fun fileSize(sessionId: String, remotePath: String): Long? = withContext(Dispatchers.IO) {
        val live = sessionStore.getSession(sessionId)
        val r = ShellInvocation.run(live.client, "stat -c %s ${shellEscape(remotePath)}", live.bashAvailable)
        if (r.exitCode != 0) null else r.stdout.trim().toLongOrNull()
    }

    companion object { private const val MAX_BATCH_ARGS = 200 }

    private suspend fun runShellOrFail(sessionId: String, verb: String, inner: String) =
        withContext(Dispatchers.IO) {
            val live = sessionStore.getSession(sessionId)
            val r = ShellInvocation.run(live.client, inner, live.bashAvailable)
            if (r.exitCode != 0) {
                val first = r.stderr.lineSequence().firstOrNull()?.trim().orEmpty()
                throw java.io.IOException("$verb failed: ${first.ifEmpty { "exit ${r.exitCode}" }}")
            }
        }
}

internal fun shellEscape(path: String): String = "'" + path.replace("'", "'\\''") + "'"
