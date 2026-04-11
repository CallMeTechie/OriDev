package dev.ori.data.repository

import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class LocalFileSystemRepository @Inject constructor() : FileSystemRepository {

    override suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(path)
        dir.listFiles()?.map { it.toFileItem() } ?: emptyList()
    }

    override suspend fun deleteFile(path: String): Unit = withContext(Dispatchers.IO) {
        val file = File(path)
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!deleted) throw IllegalStateException("Failed to delete: $path")
    }

    override suspend fun renameFile(oldPath: String, newPath: String): Unit = withContext(Dispatchers.IO) {
        val source = File(oldPath)
        val target = File(newPath)
        if (!source.renameTo(target)) {
            throw IllegalStateException("Failed to rename $oldPath to $newPath")
        }
    }

    override suspend fun createDirectory(path: String): Unit = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.mkdirs()) {
            throw IllegalStateException("Failed to create directory: $path")
        }
    }

    // chmod is a no-op on non-rooted Android -- the standard java.io.File API
    // does not support POSIX permission changes, and Runtime.exec("chmod") is
    // unreliable without root. Remote chmod works via SshClient instead.
    override suspend fun chmod(path: String, permissions: String) {
        // No-op on Android
    }

    override suspend fun getFileContent(path: String): ByteArray = withContext(Dispatchers.IO) {
        File(path).readBytes()
    }

    override suspend fun writeFileContent(path: String, content: ByteArray): Unit = withContext(Dispatchers.IO) {
        File(path).writeBytes(content)
    }

    private fun File.toFileItem(): FileItem = FileItem(
        name = name,
        path = absolutePath,
        isDirectory = isDirectory,
        size = length(),
        lastModified = lastModified(),
        permissions = null,
        owner = null,
    )
}
