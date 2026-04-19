package dev.ori.domain.repository

import dev.ori.domain.model.GrantedTree
import kotlinx.coroutines.flow.Flow

/**
 * Phase 15 Task 15.6 — contract for the Storage Access Framework grant
 * registry.
 *
 * The file-manager's local pane relies on this repository for the list of
 * tree URIs the user has granted via the system folder picker. The `:data`
 * implementation ([dev.ori.data.repository.StorageAccessRepositoryImpl])
 * wraps `ContentResolver.takePersistableUriPermission` / `release…` and
 * mirrors the URI list into a dedicated DataStore file so grants survive
 * process death.
 *
 * ## Architectural notes
 *
 * - **Pure-Kotlin signature.** All URIs are passed as `String` so this
 *   interface stays Android-free. The implementation parses with
 *   `Uri.parse(...)` at the boundary.
 * - **`Flow<List<GrantedTree>>` over snapshot reads.** Consumers (the
 *   file manager + the Settings section) react to grant additions and
 *   removals without re-subscribing.
 * - **Play-Store posture.** This is the sole permitted path to local
 *   filesystem access. `MANAGE_EXTERNAL_STORAGE` would force the app
 *   through Play's Permission Declaration review; SAF does not.
 *
 * @see GrantedTree
 */
public interface StorageAccessRepository {

    /**
     * Emits the currently-granted trees. Starts with the persisted
     * snapshot and re-emits whenever [grant] or [revoke] mutates the
     * store. May emit an empty list on a fresh install.
     */
    public val grantedTrees: Flow<List<GrantedTree>>

    /**
     * Registers [uri] as a persisted SAF grant. Implementations MUST:
     *
     * 1. Call `ContentResolver.takePersistableUriPermission(uri,
     *    FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION)`
     *    so the grant survives device reboot.
     * 2. Append [uri] to the persisted URI set (no-op if already present).
     *
     * Duplicates are ignored. If the caller passes an unparseable URI
     * the implementation should swallow the error rather than crash —
     * the launcher only ever produces valid `content://…` URIs.
     */
    public suspend fun grant(uri: String)

    /**
     * Releases the persistable grant for [uri] and removes it from the
     * persisted set. Safe to call on a URI that is not currently
     * persisted (no-op).
     *
     * Used when the user taps "Remove" in the Settings → Storage Access
     * section.
     */
    public suspend fun revoke(uri: String)
}
