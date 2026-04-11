package dev.ori.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.ori.core.common.model.TransferDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.ori.core.common.model.TransferStatus
import dev.ori.core.network.ftp.FtpClient
import dev.ori.core.network.ssh.SshClient
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.entity.TransferRecordEntity

@HiltWorker
class TransferWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TransferRecordDao,
    private val sshClient: SshClient,
    private val ftpClient: FtpClient,
    private val serverProfileDao: ServerProfileDao,
    private val connectionRepository: dev.ori.domain.repository.ConnectionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val transferId = inputData.getLong(KEY_TRANSFER_ID, -1)
        if (transferId == -1L) return Result.failure()

        val record = dao.getById(transferId) ?: return Result.failure()

        try {
            setForeground(createForegroundInfo(record))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Foreground notification may fail if permissions are missing; continue anyway
        }

        dao.update(
            record.copy(
                status = TransferStatus.ACTIVE,
                startedAt = record.startedAt ?: System.currentTimeMillis(),
            ),
        )

        return try {
            val profile = serverProfileDao.getById(record.serverProfileId)
            when {
                profile?.protocol?.isSshBased == true -> transferViaSsh(record)
                else -> transferViaFtp(record)
            }

            dao.getById(transferId)?.let { current ->
                dao.update(
                    current.copy(
                        status = TransferStatus.COMPLETED,
                        completedAt = System.currentTimeMillis(),
                    ),
                )
            }
            Result.success()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            handleFailure(transferId, e)
        }
    }

    private suspend fun transferViaSsh(record: TransferRecordEntity) {
        val sessionId = connectionRepository.getActiveSessionId(record.serverProfileId)
            ?: error("Not connected to server")

        val progressCallback = createProgressCallback(record.id)
        when (record.direction) {
            TransferDirection.UPLOAD -> sshClient.uploadFile(
                sessionId,
                record.sourcePath,
                record.destinationPath,
                onProgress = progressCallback,
            )

            TransferDirection.DOWNLOAD -> sshClient.downloadFile(
                sessionId,
                record.destinationPath,
                record.sourcePath,
                onProgress = progressCallback,
            )
        }
    }

    private suspend fun transferViaFtp(record: TransferRecordEntity) {
        val progressCallback = createProgressCallback(record.id)
        when (record.direction) {
            TransferDirection.UPLOAD -> ftpClient.uploadFile(
                record.sourcePath,
                record.destinationPath,
                onProgress = progressCallback,
            )

            TransferDirection.DOWNLOAD -> ftpClient.downloadFile(
                record.destinationPath,
                record.sourcePath,
                onProgress = progressCallback,
            )
        }
    }

    private fun createProgressCallback(transferId: Long): (Long, Long) -> Unit {
        val scope = CoroutineScope(Dispatchers.IO)
        return { transferred, total ->
            scope.launch {
                dao.getById(transferId)?.let { current ->
                    dao.update(current.copy(transferredBytes = transferred, totalBytes = total))
                }
                setProgress(workDataOf("transferred" to transferred, "total" to total))
            }
        }
    }

    private suspend fun handleFailure(transferId: Long, e: Exception): Result {
        val current = dao.getById(transferId)
        if (current != null) {
            val newRetryCount = current.retryCount + 1
            if (newRetryCount < MAX_RETRY_COUNT) {
                dao.update(
                    current.copy(
                        status = TransferStatus.QUEUED,
                        retryCount = newRetryCount,
                        errorMessage = e.message,
                    ),
                )
                return Result.retry()
            }
            dao.update(
                current.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = e.message,
                    completedAt = System.currentTimeMillis(),
                    retryCount = newRetryCount,
                ),
            )
        }
        return Result.failure()
    }

    private fun createForegroundInfo(record: TransferRecordEntity): ForegroundInfo {
        val channelId = CHANNEL_ID
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "File Transfers", NotificationManager.IMPORTANCE_LOW),
            )
        }

        val fileName = record.sourcePath.substringAfterLast('/')
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Transferring: $fileName")
            .setProgress(PROGRESS_MAX, 0, true)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID_BASE + record.id.toInt(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        const val KEY_TRANSFER_ID = "transfer_id"
        const val KEY_OFFSET_BYTES = "offset_bytes"
        private const val CHANNEL_ID = "oridev_transfers"
        private const val NOTIFICATION_ID_BASE = 10_000
        private const val MAX_RETRY_COUNT = 3
        private const val PROGRESS_MAX = 100
    }
}
