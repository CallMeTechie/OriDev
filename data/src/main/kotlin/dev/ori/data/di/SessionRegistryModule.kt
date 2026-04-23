package dev.ori.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ori.data.session.SessionPersistencePreferences
import dev.ori.data.session.SessionRegistryImpl
import dev.ori.domain.preferences.SessionResumePreferences
import dev.ori.domain.repository.SessionRegistry
import javax.inject.Singleton

/**
 * Backing-file delegate for the session-resume DataStore. Kept at the
 * file level (mirrors [KeyboardPreferencesModule]) so the
 * `preferencesDataStore` extension's per-file singleton contract holds
 * process-wide.
 */
private val Context.sessionPersistenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SessionPersistencePreferences.DATASTORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionRegistryModule {

    /**
     * Bind the singleton [SessionRegistryImpl] to the domain
     * [SessionRegistry] interface. Both sides share the same instance
     * so any consumer that injects either type sees the same
     * `openSessions` / `focusedSessionId` streams.
     */
    @Binds
    @Singleton
    abstract fun bindSessionRegistry(impl: SessionRegistryImpl): SessionRegistry

    /**
     * Full Session Persistence Task 3 — bind the concrete preferences
     * class to the domain-side [SessionResumePreferences] interface so
     * :feature-connections / :feature-terminal / :feature-filemanager
     * can inject the interface without pulling in :data.
     */
    @Binds
    @Singleton
    abstract fun bindSessionResumePreferences(
        impl: SessionPersistencePreferences,
    ): SessionResumePreferences

    companion object {
        /**
         * Materialise [SessionPersistencePreferences] over the
         * Android-backed DataStore (`ori_session_persistence.preferences_pb`).
         * The preferences class itself takes a [DataStore] so it stays
         * JVM-testable under `@TempDir` + `PreferenceDataStoreFactory`;
         * this provider plugs the real on-disk file in at Hilt graph
         * time. Mirrors the pattern used by [KeyboardPreferencesModule].
         */
        @Provides
        @Singleton
        fun provideSessionPersistencePreferences(
            @ApplicationContext context: Context,
        ): SessionPersistencePreferences = SessionPersistencePreferences(
            dataStore = context.sessionPersistenceDataStore,
        )
    }
}
