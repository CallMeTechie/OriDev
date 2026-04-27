package dev.ori.data.repository

import dev.ori.core.common.model.Protocol
import dev.ori.core.network.model.RemoteFile
import dev.ori.core.network.ssh.SshClient
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.repository.RemoteFileSystemSession
import dev.ori.domain.repository.SessionRegistry
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteFileSystemRepository @Inject constructor(
    private val clients: Map<Protocol, @JvmSuppressWildcards SshClient>,
    private val sessionRegistry: SessionRegistry,
) : FileSystemRepository, RemoteFileSystemSession {

    private val activeSessionId = AtomicReference<String?>(null)

    /**
     * Sets the active SSH session ID. Must be called before any file operations.
     * The [RemoteFileSystemSession] interface exposes this binding so feature
     * modules can stay free of direct data-layer imports.
     */
    override fun setActiveSession(sessionId: String) {
        activeSessionId.set(sessionId)
    }

    private fun requireSession(): String =
        activeSessionId.get()
            ?: throw IOException("No active SSH session")

    /**
     * Resolves the correct [SshClient] for the active session's protocol.
     * Consults [SessionRegistry.openSessions] to find the [Session] whose id
     * matches the active session, then looks up the protocol-keyed client map.
     */
    private fun client(): SshClient {
        val sid = requireSession()
        val proto = sessionRegistry.openSessions.value.firstOrNull { it.id == sid }?.protocol
            ?: throw IOException("Session $sid not in registry")
        return clients[proto] ?: throw IOException("No SshClient registered for protocol $proto")
    }

    override suspend fun listFiles(path: String): List<FileItem> {
        val sessionId = requireSession()
        return client().listFiles(sessionId, path).map { it.toFileItem() }
    }

    override suspend fun deleteFile(path: String) {
        require(path.isNotBlank() && path != "/") { "Refusing to delete protected path: '$path'" }
        val r = client().delete(requireSession(), listOf(path))
        if (!r.isFullSuccess) throw IOException("Delete failed: ${r.failed.firstOrNull()?.second ?: "unknown"}")
    }

    override suspend fun renameFile(oldPath: String, newPath: String) {
        val sessionId = requireSession()
        client().rename(sessionId, oldPath, newPath)
    }

    override suspend fun createDirectory(path: String) {
        val sessionId = requireSession()
        client().mkdir(sessionId, path)
    }

    // chmod permissions are parsed as OCTAL: e.g. "755" -> 0755 (493 decimal).
    // The String comes from the UI/domain layer in standard Unix notation.
    override suspend fun chmod(path: String, permissions: String) {
        val sessionId = requireSession()
        val octalPermissions = permissions.toInt(8)
        client().chmod(sessionId, path, octalPermissions)
    }

    override suspend fun getFileContent(path: String): ByteArray {
        val sessionId = requireSession()
        val tempFile = File.createTempFile("oridev_download_", ".tmp")
        try {
            client().downloadFile(sessionId, path, tempFile.absolutePath)
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
            client().uploadFile(sessionId, tempFile.absolutePath, path)
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
