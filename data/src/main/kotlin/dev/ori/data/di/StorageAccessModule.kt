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
import dev.ori.data.repository.StorageAccessRepositoryImpl
import dev.ori.data.storage.PersistedTreeStore
import dev.ori.domain.repository.StorageAccessRepository
import javax.inject.Singleton

/**
 * Phase 15 Task 15.6 — Hilt graph for the SAF storage-access subsystem.
 *
 * - Exposes a dedicated DataStore backing file
 *   ([PersistedTreeStore.DATASTORE_NAME]) for the persisted tree URIs.
 *   Kept separate from `ori_settings` and `ori_keyboard` so corruption
 *   of the grant set cannot wipe out user preferences (each DataStore
 *   has its own recovery path).
 * - Binds [StorageAccessRepository] as a `@Singleton`. The ViewModel
 *   side consumes `Flow<List<GrantedTree>>` so a single instance keeps
 *   the file-manager and Settings sections observing the same source of
 *   truth.
 *
 * The ContentResolver is obtained from `@ApplicationContext` inside
 * [StorageAccessRepositoryImpl] — no separate binding needed.
 */
private val Context.storageTreesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PersistedTreeStore.DATASTORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
public object StorageAccessModule {

    @Provides
    @Singleton
    public fun providePersistedTreeStore(
        @ApplicationContext context: Context,
    ): PersistedTreeStore = PersistedTreeStore(
        dataStore = context.storageTreesDataStore,
    )

    @Provides
    @Singleton
    public fun provideStorageAccessRepository(
        @ApplicationContext context: Context,
        store: PersistedTreeStore,
    ): StorageAccessRepository = StorageAccessRepositoryImpl(
        context = context,
        store = store,
    )
}
