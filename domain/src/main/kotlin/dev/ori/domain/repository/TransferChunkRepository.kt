package dev.ori.domain.repository

import dev.ori.domain.model.ChunkStatus
import dev.ori.domain.model.TransferChunk

interface TransferChunkRepository {
    suspend fun upsertChunk(chunk: TransferChunk): Long
    suspend fun getChunksForTransfer(transferId: Long): List<TransferChunk>
    suspend fun updateChunkStatus(id: Long, status: ChunkStatus, error: String? = null)
    suspend fun deleteChunksForTransfer(transferId: Long)
}
