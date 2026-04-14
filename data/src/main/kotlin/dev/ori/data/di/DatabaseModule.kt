package dev.ori.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ori.data.dao.BookmarkDao
import dev.ori.data.dao.CommandSnippetDao
import dev.ori.data.dao.KnownHostDao
import dev.ori.data.dao.ProxmoxNodeDao
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.dao.SessionLogDao
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.db.MIGRATION_1_2
import dev.ori.data.db.MIGRATION_2_3
import dev.ori.data.db.OriDevDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OriDevDatabase =
        Room.databaseBuilder(
            context,
            OriDevDatabase::class.java,
            "oridev.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideServerProfileDao(db: OriDevDatabase): ServerProfileDao = db.serverProfileDao()

    @Provides
    fun provideTransferRecordDao(db: OriDevDatabase): TransferRecordDao = db.transferRecordDao()

    @Provides
    fun provideBookmarkDao(db: OriDevDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideCommandSnippetDao(db: OriDevDatabase): CommandSnippetDao = db.commandSnippetDao()

    @Provides
    fun provideSessionLogDao(db: OriDevDatabase): SessionLogDao = db.sessionLogDao()

    @Provides
    fun provideProxmoxNodeDao(db: OriDevDatabase): ProxmoxNodeDao = db.proxmoxNodeDao()

    @Provides
    fun provideKnownHostDao(db: OriDevDatabase): KnownHostDao = db.knownHostDao()
}
