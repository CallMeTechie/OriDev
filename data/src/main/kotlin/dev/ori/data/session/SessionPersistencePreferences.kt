package dev.ori.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.ori.core.security.crash.NonFatalErrorLogger
import dev.ori.domain.model.TabMemo
import dev.ori.domain.preferences.SessionResumePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Full-session resume preferences persisted via a dedicated DataStore
 * file (wired up in [dev.ori.data.di.SessionRegistryModule]).
 *
 * Holds:
 *  - `profileIds` — Set of profile ids that had open sessions at the
 *    last registry emission (Section 11 safety-net).
 *  - `tabMemos` — Per-profile terminal tab bookkeeping for resume
 *    (tab count + focused tab index).
 *  - `focusedProfileId` — Which profile the user last looked at.
 *  - `remotePaths` — Last remote path per profile for the Files pane.
 *  - `lastTopLevelRoute` — Last selected bottom-bar tab so cold start
 *    reopens where the user left off (defaults to `"connections"`).
 *
 * The four "resume subset" fields are cleared atomically via
 * [clearResumeSubset]; `lastTopLevelRoute` is intentionally preserved
 * across that reset because it is a UI preference, not a session
 * artefact.
 *
 * JSON-encoded fields (`tabMemos`, `remotePaths`) decode through
 * [decodeOrDefault], which swallows corruption, returns the default
 * (empty list / empty map) and routes the cause through
 * [NonFatalErrorLogger] so we get a diagnosable Downloads report
 * without crashing the app.
 */
class SessionPersistencePreferences(
    private val dataStore: DataStore<Preferences>,
) : SessionResumePreferences {

    private object Keys {
        val profileIds = stringSetPreferencesKey("open_profile_ids")
        val tabMemos = stringPreferencesKey("tab_memos")
        val focusedProfileId = stringPreferencesKey("focused_profile_id")
        val remotePaths = stringPreferencesKey("remote_paths")
        val lastTopLevelRoute = stringPreferencesKey("last_top_level_route")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val tabMemoListSerializer = ListSerializer(TabMemo.serializer())
    private val remotePathsSerializer = MapSerializer(Long.serializer(), String.serializer())

    override val profileIds: Flow<Set<Long>> =
        dataStore.data.map { prefs ->
            prefs[Keys.profileIds].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        }

    override val tabMemos: Flow<List<TabMemo>> =
        dataStore.data.map { prefs ->
            decodeOrDefault(prefs[Keys.tabMemos], emptyList(), tabMemoListSerializer, "tab_memos")
        }

    override val focusedProfileId: Flow<Long?> =
        dataStore.data.map { prefs ->
            val raw = prefs[Keys.focusedProfileId] ?: return@map null
            raw.toLongOrNull() ?: run {
                NonFatalErrorLogger.log(
                    category = "persist-corrupt",
                    throwable = IllegalStateException("focused_profile_id not a Long: length=${raw.length}"),
                    contextNote = "key=focused_profile_id",
                )
                null
            }
        }

    override val remotePaths: Flow<Map<Long, String>> =
        dataStore.data.map { prefs ->
            decodeOrDefault(prefs[Keys.remotePaths], emptyMap(), remotePathsSerializer, "remote_paths")
        }

    override val lastTopLevelRoute: Flow<String> =
        dataStore.data.map { prefs ->
            prefs[Keys.lastTopLevelRoute] ?: DEFAULT_TOP_LEVEL_ROUTE
        }

    override suspend fun setProfileIds(ids: Set<Long>) {
        dataStore.edit { it[Keys.profileIds] = ids.map(Long::toString).toSet() }
    }

    override suspend fun setTabMemos(memos: List<TabMemo>) {
        dataStore.edit {
            it[Keys.tabMemos] = json.encodeToString(tabMemoListSerializer, memos)
        }
    }

    override suspend fun setFocusedProfileId(id: Long?) {
        dataStore.edit {
            if (id == null) it.remove(Keys.focusedProfileId) else it[Keys.focusedProfileId] = id.toString()
        }
    }

    override suspend fun setRemotePath(profileId: Long, path: String) {
        dataStore.edit {
            val current = decodeOrDefault(
                it[Keys.remotePaths],
                emptyMap(),
                remotePathsSerializer,
                "remote_paths",
            )
            it[Keys.remotePaths] = json.encodeToString(
                remotePathsSerializer,
                current + (profileId to path),
            )
        }
    }

    override suspend fun setLastTopLevelRoute(route: String) {
        dataStore.edit { it[Keys.lastTopLevelRoute] = route }
    }

    override suspend fun clearResumeSubset() {
        dataStore.edit {
            it.remove(Keys.profileIds)
            it.remove(Keys.tabMemos)
            it.remove(Keys.focusedProfileId)
            it.remove(Keys.remotePaths)
        }
    }

    private fun <T> decodeOrDefault(
        raw: String?,
        default: T,
        serializer: KSerializer<T>,
        keyName: String,
    ): T {
        if (raw == null) return default
        return runCatching { json.decodeFromString(serializer, raw) }
            .onFailure { cause ->
                NonFatalErrorLogger.log(
                    category = "persist-corrupt",
                    throwable = cause,
                    contextNote = "key=$keyName; rawLen=${raw.length}",
                )
            }
            .getOrDefault(default)
    }

    companion object {
        /** Backing-file name for the session-resume DataStore. */
        const val DATASTORE_NAME: String = "ori_session_persistence"

        /**
         * Fallback when nothing has been persisted yet. Matches the
         * nav-graph's initial destination so cold start without prior
         * state opens the Connections list (no flicker to another tab).
         */
        const val DEFAULT_TOP_LEVEL_ROUTE: String = "connections"
    }
}
