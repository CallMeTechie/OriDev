package dev.ori.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transfer_chunks",
    foreignKeys = [
        ForeignKey(
            entity = TransferRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["transferId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transferId", "status"], name = "idx_chunks_transfer_status"),
        Index(value = ["transferId", "chunkIndex"], unique = true),
    ],
)
data class TransferChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transferId: Long,
    val chunkIndex: Int,
    val offsetBytes: Long,
    val lengthBytes: Long,
    val sha256: String? = null,
    @ColumnInfo(defaultValue = "PENDING")
    val status: String = "PENDING",
    @ColumnInfo(defaultValue = "0")
    val attempts: Int = 0,
    val lastError: String? = null,
)
