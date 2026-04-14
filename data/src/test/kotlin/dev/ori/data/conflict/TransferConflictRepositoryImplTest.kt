package dev.ori.data.conflict

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.domain.model.ConflictRequest
import dev.ori.domain.model.ConflictResolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransferConflictRepositoryImplTest {

    private val sampleRequest = ConflictRequest(
        id = "conflict-1",
        transferId = 42L,
        conflictedPath = "/remote/a.txt",
        existingSize = 1024L,
        existingLastModified = 10_000L,
    )

    @Test
    fun emitConflict_sharedFlowReceivesItem() = runTest(UnconfinedTestDispatcher()) {
        val repo = TransferConflictRepositoryImpl()

        repo.conflictRequests.test {
            repo.emitConflict(sampleRequest)
            assertThat(awaitItem()).isEqualTo(sampleRequest)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun awaitResolution_suspends_untilResolveCalled() = runTest(UnconfinedTestDispatcher()) {
        val repo = TransferConflictRepositoryImpl()
        repo.emitConflict(sampleRequest)

        val deferred = async { repo.awaitResolution(sampleRequest.id) }

        // Not yet completed — resolve has not been called.
        assertThat(deferred.isCompleted).isFalse()

        repo.resolve(sampleRequest.id, ConflictResolution.OVERWRITE)

        assertThat(deferred.await()).isEqualTo(ConflictResolution.OVERWRITE)
    }

    @Test
    fun awaitResolution_multipleConflicts_resolvedIndependently() = runTest(UnconfinedTestDispatcher()) {
        val repo = TransferConflictRepositoryImpl()
        val req1 = sampleRequest.copy(id = "a", transferId = 1L)
        val req2 = sampleRequest.copy(id = "b", transferId = 2L)
        repo.emitConflict(req1)
        repo.emitConflict(req2)

        val d1 = async { repo.awaitResolution(req1.id) }
        val d2 = async { repo.awaitResolution(req2.id) }

        repo.resolve(req2.id, ConflictResolution.RENAME)
        assertThat(d2.await()).isEqualTo(ConflictResolution.RENAME)
        assertThat(d1.isCompleted).isFalse()

        repo.resolve(req1.id, ConflictResolution.SKIP)
        assertThat(d1.await()).isEqualTo(ConflictResolution.SKIP)
    }
}
