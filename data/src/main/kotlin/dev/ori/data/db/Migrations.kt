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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE server_profiles ADD COLUMN maxBandwidthKbps INTEGER")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transfer_chunks (
                id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                transferId  INTEGER NOT NULL,
                chunkIndex  INTEGER NOT NULL,
                offsetBytes INTEGER NOT NULL,
                lengthBytes INTEGER NOT NULL,
                sha256      TEXT,
                status      TEXT NOT NULL DEFAULT 'PENDING',
                attempts    INTEGER NOT NULL DEFAULT 0,
                lastError   TEXT,
                FOREIGN KEY(transferId) REFERENCES transfer_records(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_transfer_chunks_transferId_chunkIndex " +
                "ON transfer_chunks(transferId, chunkIndex)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_chunks_transfer_status ON transfer_chunks(transferId, status)",
        )
    }
}
