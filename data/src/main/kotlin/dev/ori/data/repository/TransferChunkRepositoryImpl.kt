package dev.ori.data.repository

import dev.ori.data.dao.TransferChunkDao
import dev.ori.data.mapper.toDomain
import dev.ori.data.mapper.toEntity
import dev.ori.domain.model.ChunkStatus
import dev.ori.domain.model.TransferChunk
import dev.ori.domain.repository.TransferChunkRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferChunkRepositoryImpl @Inject constructor(
    private val dao: TransferChunkDao,
) : TransferChunkRepository {

    override suspend fun upsertChunk(chunk: TransferChunk): Long =
        dao.upsert(chunk.toEntity())

    override suspend fun getChunksForTransfer(transferId: Long): List<TransferChunk> =
        dao.getByTransferId(transferId).map { it.toDomain() }

    override suspend fun updateChunkStatus(id: Long, status: ChunkStatus, error: String?) {
        dao.updateStatus(id, status.name, error)
    }

    override suspend fun deleteChunksForTransfer(transferId: Long) {
        dao.deleteByTransferId(transferId)
    }
}
