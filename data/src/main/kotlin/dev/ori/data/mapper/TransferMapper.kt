package dev.ori.data.mapper

import dev.ori.data.entity.TransferRecordEntity
import dev.ori.domain.model.TransferRequest

fun TransferRecordEntity.toDomain(): TransferRequest =
    TransferRequest(
        id = id,
        serverProfileId = serverProfileId,
        sourcePath = sourcePath,
        destinationPath = destinationPath,
        direction = direction,
        status = status,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        fileCount = fileCount,
        filesTransferred = filesTransferred,
        startedAt = startedAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        retryCount = retryCount,
    )

fun TransferRequest.toEntity(): TransferRecordEntity =
    TransferRecordEntity(
        id = id,
        serverProfileId = serverProfileId,
        sourcePath = sourcePath,
        destinationPath = destinationPath,
        direction = direction,
        status = status,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        fileCount = fileCount,
        filesTransferred = filesTransferred,
        startedAt = startedAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        retryCount = retryCount,
    )
