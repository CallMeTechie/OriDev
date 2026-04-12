package dev.ori.core.security.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.crashPrefsDataStore: DataStore<Preferences> by preferencesDataStore("crash_reporting")

/**
 * Stores the user's opt-in choice for anonymous crash reporting.
 *
 * Default is `false` (opt-in only) to comply with GDPR and Google Play
 * data-transparency policies. The toggle is consulted asynchronously by
 * `OriDevApplication.onCreate()` after Hilt has injected this class.
 *
 * The visible constructor accepts a [DataStore] so unit tests can supply an
 * in-memory implementation; the [Hilt-injected][Inject] secondary constructor
 * resolves the production DataStore from an [ApplicationContext].
 */
@Singleton
class CrashReportingPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.crashPrefsDataStore)

    private val enabledKey = booleanPreferencesKey("enabled")

    val enabled: Flow<Boolean> = dataStore.data
        .map { it[enabledKey] ?: DEFAULT_ENABLED }

    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { it[enabledKey] = value }
    }

    companion object {
        const val DEFAULT_ENABLED: Boolean = false
    }
}
