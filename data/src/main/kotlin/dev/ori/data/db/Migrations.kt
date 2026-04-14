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

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE transfer_records ADD COLUMN queuedAt INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE transfer_records ADD COLUMN nextRetryAt INTEGER",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transfer_records_status_queuedAt " +
                "ON transfer_records(status, queuedAt)",
        )
    }
}
