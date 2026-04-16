package dev.ori.data.mapper

import dev.ori.data.entity.TransferChunkEntity
import dev.ori.domain.model.ChunkStatus
import dev.ori.domain.model.TransferChunk

fun TransferChunkEntity.toDomain(): TransferChunk = TransferChunk(
    id = id,
    transferId = transferId,
    index = chunkIndex,
    offsetBytes = offsetBytes,
    lengthBytes = lengthBytes,
    sha256Expected = sha256,
    status = ChunkStatus.valueOf(status),
    attempts = attempts,
    lastError = lastError,
)

fun TransferChunk.toEntity(): TransferChunkEntity = TransferChunkEntity(
    id = id,
    transferId = transferId,
    chunkIndex = index,
    offsetBytes = offsetBytes,
    lengthBytes = lengthBytes,
    sha256 = sha256Expected,
    status = status.name,
    attempts = attempts,
    lastError = lastError,
)
