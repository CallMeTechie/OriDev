package dev.ori.core.network.ftp

import dev.ori.core.network.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPSClient
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import javax.inject.Inject

class FtpClientImpl @Inject constructor() : FtpClient {

    private var client: FTPClient? = null

    override val isConnected: Boolean
        get() = client?.isConnected == true

    override suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray,
        useTls: Boolean,
    ) = withContext(Dispatchers.IO) {
        val ftpClient = if (useTls) FTPSClient(true) else FTPClient()
        ftpClient.connect(host, port)
        val loginSuccess = ftpClient.login(username, String(password))
        if (!loginSuccess) {
            ftpClient.disconnect()
            throw IOException("FTP login failed for user: $username")
        }
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
        ftpClient.enterLocalPassiveMode()
        client = ftpClient
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        client?.let { c ->
            try {
                if (c.isConnected) {
                    c.logout()
                    c.disconnect()
                }
            } catch (_: IOException) {
                // Best-effort disconnect
            }
        }
        client = null
    }

    override suspend fun listFiles(path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val c = requireClient()
        val ftpFiles = c.listFiles(path)
        ftpFiles
            .filter { it != null && it.name != "." && it.name != ".." }
            .map { it.toRemoteFile(path) }
    }

    override suspend fun uploadFile(
        localPath: String,
        remotePath: String,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val c = requireClient()
        val localFile = java.io.File(localPath)
        val totalSize = localFile.length()
        FileInputStream(localFile).use { fis ->
            val countingStream = CountingInputStream(fis, totalSize, onProgress)
            val success = c.storeFile(remotePath, countingStream)
            if (!success) {
                throw IOException("FTP upload failed: ${c.replyString}")
            }
        }
    }

    override suspend fun downloadFile(
        remotePath: String,
        localPath: String,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val c = requireClient()
        FileOutputStream(localPath).use { fos ->
            val countingStream = CountingOutputStream(fos, onProgress)
            val success = c.retrieveFile(remotePath, countingStream)
            if (!success) {
                throw IOException("FTP download failed: ${c.replyString}")
            }
        }
    }

    /**
     * Resumable upload using FTP REST (`setRestartOffset`).
     *
     * Uses the currently-connected `FTPClient` field; per the Phase 12 plan
     * (Q3), the higher-level `FtpTransferExecutor` is responsible for owning
     * its own dedicated `FtpClient`/`FTPClient` per invocation, so this
     * overload simply mirrors the existing `uploadFile` contract and does not
     * open a secondary connection itself. Throws on protocol failure.
     */
    override suspend fun uploadFileResumable(
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val c = requireClient()
        val localFile = java.io.File(localPath)
        val totalSize = localFile.length()
        val safeOffset = offsetBytes.coerceIn(0L, totalSize)
        FileInputStream(localFile).use { fis ->
            var skipped = 0L
            while (skipped < safeOffset) {
                val s = fis.skip(safeOffset - skipped)
                if (s <= 0) break
                skipped += s
            }
            if (safeOffset > 0L) {
                c.setRestartOffset(safeOffset)
            }
            val countingStream = ResumableCountingInputStream(
                delegate = fis,
                totalSize = totalSize,
                initialBytes = safeOffset,
                onProgress = onProgress,
            )
            val success = c.storeFile(remotePath, countingStream)
            if (!success) {
                throw IOException("FTP resumable upload failed: ${c.replyString}")
            }
        }
    }

    /**
     * Resumable download using FTP REST (`setRestartOffset`).
     *
     * Opens the local file as `RandomAccessFile("rw")`, seeks to `offsetBytes`,
     * then streams the remote file via `retrieveFile` into an output stream
     * backed by the seeked `FileChannel`. Throws on protocol failure.
     */
    override suspend fun downloadFileResumable(
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (transferred: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val c = requireClient()
        val localFile = java.io.File(localPath)
        val safeOffset = offsetBytes.coerceAtLeast(0L)
        RandomAccessFile(localFile, "rw").use { raf ->
            if (safeOffset == 0L) {
                raf.setLength(0L)
            }
            raf.seek(safeOffset)
            if (safeOffset > 0L) {
                c.setRestartOffset(safeOffset)
            }
            val out = java.nio.channels.Channels.newOutputStream(raf.channel)
            val countingStream = ResumableCountingOutputStream(
                delegate = out,
                initialBytes = safeOffset,
                onProgress = onProgress,
            )
            val success = c.retrieveFile(remotePath, countingStream)
            if (!success) {
                throw IOException("FTP resumable download failed: ${c.replyString}")
            }
        }
    }

    override suspend fun fileSize(remotePath: String): Long? = withContext(Dispatchers.IO) {
        val c = requireClient()
        try {
            val files = c.listFiles(remotePath)
            val match = files.firstOrNull { it != null && it.isFile }
            match?.size
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        val c = requireClient()
        val success = c.deleteFile(path)
        if (!success) {
            // Try as directory
            val dirSuccess = c.removeDirectory(path)
            if (!dirSuccess) {
                throw IOException("FTP delete failed: ${c.replyString}")
            }
        }
    }

    override suspend fun rename(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        val c = requireClient()
        val success = c.rename(oldPath, newPath)
        if (!success) {
            throw IOException("FTP rename failed: ${c.replyString}")
        }
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        val c = requireClient()
        val success = c.makeDirectory(path)
        if (!success) {
            throw IOException("FTP mkdir failed: ${c.replyString}")
        }
    }

    private fun requireClient(): FTPClient =
        client ?: throw IllegalStateException("FTP client is not connected")

    private fun FTPFile.toRemoteFile(parentPath: String): RemoteFile {
        val normalizedParent = if (parentPath.endsWith("/")) parentPath else "$parentPath/"
        return RemoteFile(
            name = name,
            path = "$normalizedParent$name",
            isDirectory = isDirectory,
            size = size,
            lastModified = timestamp?.timeInMillis ?: 0L,
            permissions = rawListing?.substring(0, 10) ?: "",
            owner = user ?: "",
        )
    }
}

private class ResumableCountingInputStream(
    private val delegate: InputStream,
    private val totalSize: Long,
    initialBytes: Long,
    private val onProgress: suspend (Long, Long) -> Unit,
) : InputStream() {

    private var bytesRead: Long = initialBytes

    override fun read(): Int {
        val b = delegate.read()
        if (b != -1) {
            bytesRead++
            kotlinx.coroutines.runBlocking { onProgress(bytesRead, totalSize) }
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) {
            bytesRead += n
            kotlinx.coroutines.runBlocking { onProgress(bytesRead, totalSize) }
        }
        return n
    }

    override fun close() {
        delegate.close()
    }
}

private class ResumableCountingOutputStream(
    private val delegate: OutputStream,
    initialBytes: Long,
    private val onProgress: suspend (Long, Long) -> Unit,
) : OutputStream() {

    private var bytesWritten: Long = initialBytes

    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten++
        kotlinx.coroutines.runBlocking { onProgress(bytesWritten, -1) }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len
        kotlinx.coroutines.runBlocking { onProgress(bytesWritten, -1) }
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}

private class CountingInputStream(
    private val delegate: InputStream,
    private val totalSize: Long,
    private val onProgress: (Long, Long) -> Unit,
) : InputStream() {

    private var bytesRead: Long = 0

    override fun read(): Int {
        val b = delegate.read()
        if (b != -1) {
            bytesRead++
            onProgress(bytesRead, totalSize)
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) {
            bytesRead += n
            onProgress(bytesRead, totalSize)
        }
        return n
    }

    override fun close() {
        delegate.close()
    }
}

private class CountingOutputStream(
    private val delegate: OutputStream,
    private val onProgress: (Long, Long) -> Unit,
) : OutputStream() {

    private var bytesWritten: Long = 0

    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten++
        onProgress(bytesWritten, -1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len
        onProgress(bytesWritten, -1)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}
