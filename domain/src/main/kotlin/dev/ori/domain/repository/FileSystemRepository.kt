package dev.ori.domain.repository

import dev.ori.domain.model.FileItem

interface FileSystemRepository {
    suspend fun listFiles(path: String): List<FileItem>
    suspend fun deleteFile(path: String)
    suspend fun renameFile(oldPath: String, newPath: String)
    suspend fun createDirectory(path: String)
    suspend fun chmod(path: String, permissions: String)
    suspend fun getFileContent(path: String): ByteArray
    suspend fun writeFileContent(path: String, content: ByteArray)
}
