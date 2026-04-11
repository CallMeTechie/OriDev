package dev.ori.data.repository

import dev.ori.core.network.model.RemoteFile
import dev.ori.core.network.ssh.SshClient
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class RemoteFileSystemRepository @Inject constructor(
    private val sshClient: SshClient,
) : FileSystemRepository {

    private val activeSessionId = AtomicReference<String?>(null)

    /**
     * Sets the active SSH session ID. Must be called before any file operations.
     * This is NOT part of the [FileSystemRepository] interface -- the ViewModel
     * obtains the concrete type via a separate Hilt binding to call this method.
     */
    fun setActiveSession(sessionId: String) {
        activeSessionId.set(sessionId)
    }

    private fun requireSession(): String =
        activeSessionId.get()
            ?: throw IllegalStateException("No active SSH session. Call setActiveSession() first.")

    override suspend fun listFiles(path: String): List<FileItem> {
        val sessionId = requireSession()
        return sshClient.listFiles(sessionId, path).map { it.toFileItem() }
    }

    override suspend fun deleteFile(path: String) {
        val sessionId = requireSession()
        sshClient.deleteFile(sessionId, path)
    }

    override suspend fun renameFile(oldPath: String, newPath: String) {
        val sessionId = requireSession()
        sshClient.rename(sessionId, oldPath, newPath)
    }

    override suspend fun createDirectory(path: String) {
        val sessionId = requireSession()
        sshClient.mkdir(sessionId, path)
    }

    // chmod permissions are parsed as OCTAL: e.g. "755" -> 0755 (493 decimal).
    // The String comes from the UI/domain layer in standard Unix notation.
    override suspend fun chmod(path: String, permissions: String) {
        val sessionId = requireSession()
        val octalPermissions = permissions.toInt(8)
        sshClient.chmod(sessionId, path, octalPermissions)
    }

    override suspend fun getFileContent(path: String): ByteArray {
        val sessionId = requireSession()
        val tempFile = File.createTempFile("oridev_download_", ".tmp")
        try {
            sshClient.downloadFile(sessionId, path, tempFile.absolutePath)
            return tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun writeFileContent(path: String, content: ByteArray) {
        val sessionId = requireSession()
        val tempFile = File.createTempFile("oridev_upload_", ".tmp")
        try {
            tempFile.writeBytes(content)
            sshClient.uploadFile(sessionId, tempFile.absolutePath, path)
        } finally {
            tempFile.delete()
        }
    }

    private fun RemoteFile.toFileItem(): FileItem = FileItem(
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        permissions = permissions,
        owner = owner,
    )
}
