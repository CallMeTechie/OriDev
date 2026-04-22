package dev.ori.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionPersistenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ori_session_persistence",
)

/**
 * Survives process death so the Connections tab's reconnect banner
 * (spec Section 11 safety-net — devil's-advocate concern 4) knows
 * which profiles had a live session before the OS killed us.
 * Passwords stay in the Keystore (#171), so batch-reconnect after
 * resume needs no password prompt.
 *
 * Stored as `Set<String>` because DataStore Preferences has no
 * native Set<Long>; we round-trip through `Long.toString()`. The
 * value is always a full snapshot — last-writer-wins, no merge.
 */
@Singleton
class SessionPersistencePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringSetPreferencesKey("open_profile_ids")

    val profileIds: Flow<Set<Long>> =
        context.sessionPersistenceDataStore.data.map { prefs ->
            prefs[key].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        }

    suspend fun setProfileIds(ids: Set<Long>) {
        context.sessionPersistenceDataStore.edit { prefs ->
            prefs[key] = ids.map { it.toString() }.toSet()
        }
    }

    suspend fun clear() {
        context.sessionPersistenceDataStore.edit { prefs ->
            prefs[key] = emptySet()
        }
    }
}
