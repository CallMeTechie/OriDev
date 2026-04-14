package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus

@Entity(
    tableName = "transfer_records",
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("serverProfileId"),
        Index(value = ["status", "queuedAt"]),
    ],
)
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long,
    val sourcePath: String,
    val destinationPath: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val totalBytes: Long,
    val transferredBytes: Long = 0,
    val fileCount: Int = 1,
    val filesTransferred: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val queuedAt: Long = System.currentTimeMillis(),
    val nextRetryAt: Long? = null,
)
