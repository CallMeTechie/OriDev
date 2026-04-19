package dev.ori.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phase 15 Task 15.6 — local-filesystem repository implemented against the
 * Storage Access Framework rather than `java.io.File`.
 *
 * ## Why SAF
 *
 * On API 30+ a Play-Store-safe app cannot read arbitrary
 * `/storage/emulated/0/…` paths without `MANAGE_EXTERNAL_STORAGE`, which
 * Google requires a manual review for. SAF gives us persistent, scoped
 * grants to individual trees the user picked — no scary permission and
 * no review. The trade-off is that "path" is now a document URI
 * (`content://com.android.externalstorage.documents/tree/…/document/…`)
 * rather than a POSIX path.
 *
 * ## Path convention
 *
 * - Tree URIs (root of a granted folder) look like
 *   `content://….documents/tree/primary%3ADocuments`.
 * - Child-document URIs look like
 *   `content://….documents/tree/primary%3ADocuments/document/primary%3ADocuments%2Freadme.txt`.
 *
 * Both are stored verbatim in [FileItem.path]. Navigation and transfers
 * treat them as opaque identifiers — the file manager does not attempt
 * to parse them.
 *
 * ## Constraints
 *
 * - **chmod** is and remains a no-op. DocumentFile has no POSIX concept.
 * - **rename** uses `DocumentFile.renameTo(newName)` and accepts a plain
 *   filename, not a new full URI — callers should pass just the new
 *   name.
 * - **git status enrichment** is dropped for SAF paths. `GitStatusParser`
 *   needs a `java.io.File` root and git on the shell path, which SAF
 *   documents do not expose. Remote (SSH) git-status still works.
 */
public class LocalFileSystemRepository @Inject constructor(
    private val context: Context,
) : FileSystemRepository {

    override suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val doc = resolveDocument(path) ?: return@withContext emptyList()
        if (!doc.isDirectory) return@withContext emptyList()
        doc.listFiles().map { it.toFileItem() }
    }

    override suspend fun deleteFile(path: String) {
        withContext(Dispatchers.IO) {
            val doc = resolveDocument(path)
                ?: throw IllegalStateException("Document not found: $path")
            if (!doc.delete()) {
                throw IllegalStateException("Failed to delete: $path")
            }
        }
    }

    override suspend fun renameFile(oldPath: String, newPath: String) {
        withContext(Dispatchers.IO) {
            val doc = resolveDocument(oldPath)
                ?: throw IllegalStateException("Document not found: $oldPath")
            // SAF renameTo expects a name, not a path. If the caller
            // passed a slash-separated path, take the last segment.
            val newName = newPath.substringAfterLast('/').ifBlank { newPath }
            if (!doc.renameTo(newName)) {
                throw IllegalStateException("Failed to rename $oldPath to $newName")
            }
        }
    }

    override suspend fun createDirectory(path: String) {
        withContext(Dispatchers.IO) {
            // `path` here is treated as "parentUri + child dir name" —
            // but callers historically pass a POSIX path. For SAF we
            // split on the last '/' and treat the left side as the
            // parent URI, the right side as the new folder name.
            val parent = resolveDocument(path.substringBeforeLast('/'))
                ?: throw IllegalStateException("Parent not found: $path")
            val name = path.substringAfterLast('/').ifBlank { "New Folder" }
            if (parent.createDirectory(name) == null) {
                throw IllegalStateException("Failed to create directory: $path")
            }
        }
    }

    // chmod is a no-op for SAF just as it was for java.io.File on
    // non-rooted Android. Remote chmod still works via SshClient.
    override suspend fun chmod(path: String, permissions: String) {
        // No-op
    }

    override suspend fun getFileContent(path: String): ByteArray = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(path) }.getOrNull()
            ?: throw IllegalStateException("Invalid URI: $path")
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open input stream for $path")
    }

    override suspend fun writeFileContent(path: String, content: ByteArray) {
        withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(path) }.getOrNull()
                ?: throw IllegalStateException("Invalid URI: $path")
            context.contentResolver.openOutputStream(uri)?.use { it.write(content) }
                ?: throw IllegalStateException("Cannot open output stream for $path")
        }
    }

    private fun resolveDocument(path: String): DocumentFile? {
        val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return null
        // Tree URIs → fromTreeUri; child URIs (contain "/document/") →
        // fromSingleUri. Both produce a DocumentFile whose listFiles()
        // and delete()/rename() work against the underlying provider.
        return if (path.contains("/document/")) {
            DocumentFile.fromSingleUri(context, uri)
        } else {
            DocumentFile.fromTreeUri(context, uri)
        }
    }

    private fun DocumentFile.toFileItem(): FileItem = FileItem(
        name = name.orEmpty(),
        path = uri.toString(),
        isDirectory = isDirectory,
        size = length(),
        lastModified = lastModified(),
        permissions = null,
        owner = null,
    )
}
