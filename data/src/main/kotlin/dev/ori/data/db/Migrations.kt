package dev.ori.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE server_profiles ADD COLUMN require2fa INTEGER NOT NULL DEFAULT 0",
        )
    }
}
