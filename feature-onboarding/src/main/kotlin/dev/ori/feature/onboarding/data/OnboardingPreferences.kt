package dev.ori.feature.onboarding.data

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

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore("onboarding")

/**
 * Persists the "has the user finished the first-run onboarding flow" flag.
 *
 * Default is `false`. MainActivity gates the app behind [completed]; while the
 * flag is `false`, [dev.ori.feature.onboarding.OnboardingFlow] is shown and
 * `FLAG_SECURE` is set on the window to prevent screenshots during the
 * permission prompts.
 *
 * Visible secondary constructor accepts a [DataStore] so unit tests can
 * supply an in-memory implementation; the Hilt-injected constructor resolves
 * the production DataStore from an [ApplicationContext].
 */
@Singleton
class OnboardingPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.onboardingDataStore)

    private val completedKey = booleanPreferencesKey("onboarding_completed")

    val completed: Flow<Boolean> = dataStore.data
        .map { it[completedKey] ?: false }

    suspend fun markCompleted() {
        dataStore.edit { it[completedKey] = true }
    }
}
