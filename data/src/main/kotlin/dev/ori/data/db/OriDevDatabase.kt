package dev.ori.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.ori.data.dao.BookmarkDao
import dev.ori.data.dao.CommandSnippetDao
import dev.ori.data.dao.KnownHostDao
import dev.ori.data.dao.ProxmoxNodeDao
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.dao.SessionLogDao
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.entity.BookmarkEntity
import dev.ori.data.entity.CommandSnippetEntity
import dev.ori.data.entity.KnownHostEntity
import dev.ori.data.entity.ProxmoxNodeEntity
import dev.ori.data.entity.ServerProfileEntity
import dev.ori.data.entity.SessionLogEntity
import dev.ori.data.entity.TransferRecordEntity

@Database(
    entities = [
        ServerProfileEntity::class,
        TransferRecordEntity::class,
        BookmarkEntity::class,
        CommandSnippetEntity::class,
        SessionLogEntity::class,
        ProxmoxNodeEntity::class,
        KnownHostEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OriDevDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun transferRecordDao(): TransferRecordDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun commandSnippetDao(): CommandSnippetDao
    abstract fun sessionLogDao(): SessionLogDao
    abstract fun proxmoxNodeDao(): ProxmoxNodeDao
    abstract fun knownHostDao(): KnownHostDao
}
