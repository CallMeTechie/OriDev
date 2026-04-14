package dev.ori.data.mapper

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.entity.TransferRecordEntity
import dev.ori.domain.model.TransferRequest
import org.junit.jupiter.api.Test

class TransferMapperTest {

    @Test
    fun entityToDomain_mapsAllFields() {
        val entity = TransferRecordEntity(
            id = 1L,
            serverProfileId = 10L,
            sourcePath = "/local/test.txt",
            destinationPath = "/remote/test.txt",
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.ACTIVE,
            totalBytes = 5000L,
            transferredBytes = 2500L,
            fileCount = 3,
            filesTransferred = 1,
            startedAt = 1000L,
            completedAt = null,
            errorMessage = null,
            retryCount = 0,
        )

        val domain = entity.toDomain()

        assertThat(domain.id).isEqualTo(1L)
        assertThat(domain.serverProfileId).isEqualTo(10L)
        assertThat(domain.sourcePath).isEqualTo("/local/test.txt")
        assertThat(domain.destinationPath).isEqualTo("/remote/test.txt")
        assertThat(domain.direction).isEqualTo(TransferDirection.UPLOAD)
        assertThat(domain.status).isEqualTo(TransferStatus.ACTIVE)
        assertThat(domain.totalBytes).isEqualTo(5000L)
        assertThat(domain.transferredBytes).isEqualTo(2500L)
        assertThat(domain.fileCount).isEqualTo(3)
        assertThat(domain.filesTransferred).isEqualTo(1)
        assertThat(domain.startedAt).isEqualTo(1000L)
        assertThat(domain.completedAt).isNull()
        assertThat(domain.errorMessage).isNull()
        assertThat(domain.retryCount).isEqualTo(0)
    }

    @Test
    fun domainToEntity_mapsAllFields() {
        val domain = TransferRequest(
            id = 2L,
            serverProfileId = 20L,
            sourcePath = "/remote/data.zip",
            destinationPath = "/local/data.zip",
            direction = TransferDirection.DOWNLOAD,
            status = TransferStatus.FAILED,
            totalBytes = 10000L,
            transferredBytes = 7500L,
            fileCount = 1,
            filesTransferred = 0,
            startedAt = 2000L,
            completedAt = 3000L,
            errorMessage = "Connection timeout",
            retryCount = 2,
        )

        val entity = domain.toEntity()

        assertThat(entity.id).isEqualTo(2L)
        assertThat(entity.serverProfileId).isEqualTo(20L)
        assertThat(entity.sourcePath).isEqualTo("/remote/data.zip")
        assertThat(entity.destinationPath).isEqualTo("/local/data.zip")
        assertThat(entity.direction).isEqualTo(TransferDirection.DOWNLOAD)
        assertThat(entity.status).isEqualTo(TransferStatus.FAILED)
        assertThat(entity.totalBytes).isEqualTo(10000L)
        assertThat(entity.transferredBytes).isEqualTo(7500L)
        assertThat(entity.fileCount).isEqualTo(1)
        assertThat(entity.filesTransferred).isEqualTo(0)
        assertThat(entity.startedAt).isEqualTo(2000L)
        assertThat(entity.completedAt).isEqualTo(3000L)
        assertThat(entity.errorMessage).isEqualTo("Connection timeout")
        assertThat(entity.retryCount).isEqualTo(2)
    }

    @Test
    fun roundTrip_preservesAllFields() {
        val original = TransferRecordEntity(
            id = 5L,
            serverProfileId = 50L,
            sourcePath = "/a/b/c.bin",
            destinationPath = "/x/y/c.bin",
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.COMPLETED,
            totalBytes = 99999L,
            transferredBytes = 99999L,
            fileCount = 5,
            filesTransferred = 5,
            startedAt = 100L,
            completedAt = 200L,
            errorMessage = null,
            retryCount = 1,
        )

        val result = original.toDomain().toEntity()

        // queuedAt/nextRetryAt are data-layer-only and never surface on the domain model,
        // so round-trips normalize them. Compare everything else.
        assertThat(result.copy(queuedAt = original.queuedAt, nextRetryAt = original.nextRetryAt))
            .isEqualTo(original)
    }

    @Test
    fun roundTrip_withErrorMessage_preservesAllFields() {
        val original = TransferRecordEntity(
            id = 7L,
            serverProfileId = 70L,
            sourcePath = "/error/file.log",
            destinationPath = "/backup/file.log",
            direction = TransferDirection.DOWNLOAD,
            status = TransferStatus.FAILED,
            totalBytes = 0L,
            transferredBytes = 0L,
            fileCount = 1,
            filesTransferred = 0,
            startedAt = 300L,
            completedAt = null,
            errorMessage = "Permission denied",
            retryCount = 3,
        )

        val result = original.toDomain().toEntity()

        assertThat(result.copy(queuedAt = original.queuedAt, nextRetryAt = original.nextRetryAt))
            .isEqualTo(original)
    }
}
