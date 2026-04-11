package dev.ori.domain.repository

import dev.ori.domain.model.TransferRequest
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    fun getAllTransfers(): Flow<List<TransferRequest>>
    fun getActiveTransfers(): Flow<List<TransferRequest>>
    suspend fun enqueue(transfer: TransferRequest): Long
    suspend fun pause(transferId: Long)
    suspend fun resume(transferId: Long)
    suspend fun cancel(transferId: Long)
    suspend fun clearCompleted()
}
