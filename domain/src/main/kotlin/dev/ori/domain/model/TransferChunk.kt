package dev.ori.domain.model

data class TransferChunk(
    val id: Long = 0,
    val transferId: Long,
    val index: Int,
    val offsetBytes: Long,
    val lengthBytes: Long,
    val sha256Expected: String?,
    val status: ChunkStatus,
    val attempts: Int = 0,
    val lastError: String? = null,
)

enum class ChunkStatus { PENDING, ACTIVE, COMPLETED, FAILED }
