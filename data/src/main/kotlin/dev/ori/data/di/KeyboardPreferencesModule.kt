package dev.ori.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ori.domain.preferences.KeyboardPreferences
import javax.inject.Singleton

/**
 * Phase 14 Task 14.5 — Hilt binding that materialises the Android
 * [DataStore] used by [KeyboardPreferences]. [KeyboardPreferences]
 * itself lives in `:domain` (pure Kotlin) and takes its DataStore as
 * a constructor argument so it stays testable without Android. This
 * Android-only module fills that gap for production.
 *
 * The backing file name MUST match [KeyboardPreferences.DATASTORE_NAME]
 * so upgrades (and Task 14.6 Settings writes) hit the same on-disk
 * file that the terminal reads at startup.
 */
private val Context.keyboardPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = KeyboardPreferences.DATASTORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
object KeyboardPreferencesModule {

    @Provides
    @Singleton
    fun provideKeyboardPreferences(
        @ApplicationContext context: Context,
    ): KeyboardPreferences = KeyboardPreferences(
        dataStore = context.keyboardPreferencesDataStore,
    )
}
