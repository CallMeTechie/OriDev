package dev.ori.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.ori.data.storage.PersistedTreeStore
import dev.ori.domain.model.GrantedTree
import dev.ori.domain.repository.StorageAccessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Phase 15 Task 15.6 — implementation of [StorageAccessRepository] that
 * backs the Play-Store-safe Storage Access Framework migration.
 *
 * Two concerns live here:
 *
 * 1. **Persistable permission** — `ContentResolver
 *    .takePersistableUriPermission` survives device reboot but only when
 *    the `Intent.FLAG_GRANT_READ_URI_PERMISSION |
 *    FLAG_GRANT_WRITE_URI_PERMISSION` bits are asserted. Both are
 *    requested unconditionally because the file manager writes as well
 *    as reads.
 * 2. **Display enrichment** — the URI alone is not human-readable. We
 *    resolve it with `DocumentFile.fromTreeUri` and read `.name`. If the
 *    user revoked the grant from System Settings, `fromTreeUri` returns
 *    null-or-doesn't-exist; we fall back to the URI's last path segment
 *    so the Settings list still shows *something* rather than blank
 *    rows — the user needs to see the stale entry to remove it.
 *
 * ## Error handling
 *
 * `take/releasePersistableUriPermission` can throw `SecurityException`
 * if the URI was not obtained through `ACTION_OPEN_DOCUMENT_TREE` (e.g.
 * a malformed URI from manual tampering). These are swallowed with the
 * URI still dropped from the persisted set on `revoke`; on `grant` we
 * skip the take but still persist nothing (fail-closed, safer than
 * recording a grant we can't actually use).
 */
public class StorageAccessRepositoryImpl(
    private val context: Context,
    private val store: PersistedTreeStore,
) : StorageAccessRepository {

    private val contentResolver: ContentResolver get() = context.contentResolver

    override val grantedTrees: Flow<List<GrantedTree>> =
        store.uris.map { uris ->
            uris.map { raw -> raw.toGrantedTree() }
                .sortedBy { it.displayName.lowercase() }
        }

    override suspend fun grant(uri: String) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val took = runCatching {
            contentResolver.takePersistableUriPermission(parsed, takeFlags)
        }.isSuccess
        if (took) {
            store.add(uri)
        }
    }

    override suspend fun revoke(uri: String) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull()
        if (parsed != null) {
            val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { contentResolver.releasePersistableUriPermission(parsed, releaseFlags) }
        }
        // Drop from the persisted set even if the release() call failed —
        // a user who taps "Remove" expects the row to disappear.
        store.remove(uri)
    }

    private fun String.toGrantedTree(): GrantedTree {
        val parsed = runCatching { Uri.parse(this) }.getOrNull()
        val doc = parsed?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
        val name = doc?.name.takeUnless { it.isNullOrBlank() } ?: fallbackDisplayName(this)
        val documentId = doc?.uri?.lastPathSegment
        return GrantedTree(
            uri = this,
            displayName = name,
            documentId = documentId,
        )
    }

    private fun fallbackDisplayName(rawUri: String): String {
        // `content://.../tree/primary%3ADocuments` → "primary:Documents".
        val segment = rawUri.substringAfterLast("/tree/", missingDelimiterValue = rawUri)
        return Uri.decode(segment).ifBlank { rawUri }
    }
}
