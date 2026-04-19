package dev.ori.domain.model

/**
 * Phase 15 Task 15.6 — a single Storage Access Framework tree that the user
 * has explicitly granted Ori:Dev persistent read/write access to.
 *
 * ## Why [uri] is a `String`, not `android.net.Uri`
 *
 * This type lives in `:domain`, which CLAUDE.md forbids from importing any
 * `android.*` packages (pure Kotlin/JVM rule). The underlying URI is an
 * opaque content-URI; the `:data` layer parses it with `Uri.parse(...)` at
 * the boundary when it needs to talk to `ContentResolver` or
 * `DocumentFile.fromTreeUri`. String round-tripping is safe because SAF
 * content URIs are plain ASCII.
 *
 * @param uri the `content://com.android.externalstorage.documents/tree/…`
 *   URI stringified. Persisted verbatim in `PersistedTreeStore`.
 * @param displayName a human-readable label for the tree (e.g. "Downloads",
 *   "Primary:Documents"). Derived at read time from the tree's root
 *   `DocumentFile.name`. Falls back to the last path segment of [uri] if
 *   the DocumentFile cannot be resolved (e.g. after a revocation).
 * @param documentId the SAF document ID of the tree root — used when
 *   constructing child URIs for listing. Null when the tree is
 *   inaccessible and we only know its recorded URI.
 */
public data class GrantedTree(
    val uri: String,
    val displayName: String,
    val documentId: String?,
)
