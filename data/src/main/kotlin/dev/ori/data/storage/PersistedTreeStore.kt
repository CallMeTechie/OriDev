package dev.ori.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Phase 15 Task 15.6 — DataStore-backed persistence for the set of SAF
 * tree URIs the user has granted Ori:Dev.
 *
 * ## File
 *
 * Backed by its own DataStore file [DATASTORE_NAME] — intentionally
 * separate from `ori_settings` (Phase 11) and `ori_keyboard` (Phase 14)
 * so a corrupted storage-grant set can never nuke the user's UI
 * preferences. Each DataStore has its own lock file and recovery path.
 *
 * ## Shape
 *
 * The persisted data is a single `stringSetPreferencesKey` whose values
 * are the full `content://…` URI strings. A `Set` is chosen over a
 * `List` to make "already-granted, ignore duplicate" trivial and atomic
 * under `edit { }`.
 *
 * ## Ordering
 *
 * The underlying `Preferences.Set` is unordered. Consumers that need
 * stable UI ordering (the Settings list) should sort by [GrantedTree]
 * displayName downstream — the raw URI string rarely renders in an
 * intuitive order to a human.
 */
public class PersistedTreeStore(
    private val dataStore: DataStore<Preferences>,
) {

    private val key = stringSetPreferencesKey(KEY_TREE_URIS)

    /**
     * Emits the currently persisted URI strings. Empty on first launch.
     * Re-emits on every [add] / [remove] / [replace] call.
     */
    public val uris: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[key] ?: emptySet()
    }

    /**
     * Adds [uri] to the persisted set. No-op if already present. Writes
     * atomically — callers on different coroutines cannot observe a
     * partially-updated set.
     */
    public suspend fun add(uri: String) {
        dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            if (uri !in current) {
                prefs[key] = current + uri
            }
        }
    }

    /**
     * Removes [uri] from the persisted set. No-op if absent.
     */
    public suspend fun remove(uri: String) {
        dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            if (uri in current) {
                prefs[key] = current - uri
            }
        }
    }

    public companion object {
        /** DataStore preference key for the persisted URI set. */
        public const val KEY_TREE_URIS: String = "granted_tree_uris"

        /**
         * Backing-file name. The `_trees` suffix disambiguates from
         * `ori_storage_conflicts` (Phase 8) and
         * `ori_storage_preferences` (if ever added) — "trees" is
         * SAF-speak for a persistable folder grant.
         */
        public const val DATASTORE_NAME: String = "ori_storage_trees"
    }
}
