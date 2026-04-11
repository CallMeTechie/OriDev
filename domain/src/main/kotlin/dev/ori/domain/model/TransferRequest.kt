package dev.ori.domain.model

import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus

data class TransferRequest(
    val id: Long = 0,
    val serverProfileId: Long,
    val sourcePath: String,
    val destinationPath: String,
    val direction: TransferDirection,
    val status: TransferStatus = TransferStatus.QUEUED,
    val totalBytes: Long = 0,
    val transferredBytes: Long = 0,
    val fileCount: Int = 1,
    val filesTransferred: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
)
