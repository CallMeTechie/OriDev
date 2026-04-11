package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_logs",
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("serverProfileId")],
)
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val logFilePath: String,
)
