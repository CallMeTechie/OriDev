package dev.ori.app.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.entity.TransferRecordEntity
import dev.ori.domain.model.ConflictRequest
import dev.ori.domain.model.ConflictResolution
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.PremiumRepository
import dev.ori.domain.repository.TransferChunkRepository
import dev.ori.domain.repository.TransferConflictRepository
import dev.ori.domain.repository.TransferRepository
import dev.ori.feature.settings.data.AppPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 12 P12.4 — unit tests for [TransferDispatcher] covering the six
 * acceptance cases from the plan §10.
 *
 * Uses hand-rolled fakes (no mockk) to keep the dispatcher tests pure —
 * the graph-heavy mockk wiring + the dispatcher's worker-launch loop
 * produced ByteBuddy instrumentation OOMs on first attempt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferDispatcherTest {

    @Test
    fun start_withOneQueued_dispatchesOneWorker() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeTransferRecordDao()
        val prefs = FakeTransferPrefs(capFlow = MutableStateFlow(3))
        val executor = HoldingTransferExecutor()
        val factory = TransferWorkerCoroutineFactory(
            FakeTransferRepository(dao),
            executor,
            NoOpConflictRepo,
            prefs,
            NoOpPremiumRepo,
            NoOpChunkRepo,
            NoOpConnectionRepo,
        )
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val dispatcher = TransferDispatcher(dao, prefs, scope, factory)

        dao.insertFake(queued(1L))
        dispatcher.start()
        testScheduler.runCurrent()

        assertThat(dispatcher.activeIds()).containsExactly(1L)

        executor.complete(1L)
        testScheduler.runCurrent()
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun start_capThree_dispatchesMaxThreeConcurrently() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeTransferRecordDao()
        val prefs = FakeTransferPrefs(capFlow = MutableStateFlow(3))
        val executor = HoldingTransferExecutor()
        val factory = TransferWorkerCoroutineFactory(
            FakeTransferRepository(dao),
            executor,
            NoOpConflictRepo,
            prefs,
            NoOpPremiumRepo,
            NoOpChunkRepo,
            NoOpConnectionRepo,
        )
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val dispatcher = TransferDispatcher(dao, prefs, scope, factory)

        (1..5L).forEach { dao.insertFake(queued(it)) }
        dispatcher.start()
        testScheduler.runCurrent()

        assertThat(dispatcher.activeIds()).containsExactly(1L, 2L, 3L)

        executor.complete(1L)
        testScheduler.runCurrent()
        assertThat(dispatcher.activeIds()).containsExactly(2L, 3L, 4L)

        executor.complete(2L)
        executor.complete(3L)
        executor.complete(4L)
        executor.complete(5L)
        testScheduler.runCurrent()
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun capDecreasedToOne_newRowsNotDispatched() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeTransferRecordDao()
        val capFlow = MutableStateFlow(3)
        val prefs = FakeTransferPrefs(capFlow)
        val executor = HoldingTransferExecutor()
        val factory = TransferWorkerCoroutineFactory(
            FakeTransferRepository(dao),
            executor,
            NoOpConflictRepo,
            prefs,
            NoOpPremiumRepo,
            NoOpChunkRepo,
            NoOpConnectionRepo,
        )
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val dispatcher = TransferDispatcher(dao, prefs, scope, factory)

        (1..3L).forEach { dao.insertFake(queued(it)) }
        dispatcher.start()
        testScheduler.runCurrent()
        assertThat(dispatcher.activeIds()).hasSize(3)

        capFlow.value = 1
        dao.insertFake(queued(4L))
        testScheduler.runCurrent()

        // Cap shrank to 1 but 3 workers are still holding slots — row 4
        // must not start until at least 2 of them finish.
        assertThat(dispatcher.activeIds()).containsExactly(1L, 2L, 3L)

        executor.complete(1L)
        executor.complete(2L)
        executor.complete(3L)
        executor.complete(4L)
        testScheduler.runCurrent()
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun capIncreasedToFive_pendingRowsPickedUp() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeTransferRecordDao()
        val capFlow = MutableStateFlow(2)
        val prefs = FakeTransferPrefs(capFlow)
        val executor = HoldingTransferExecutor()
        val factory = TransferWorkerCoroutineFactory(
            FakeTransferRepository(dao),
            executor,
            NoOpConflictRepo,
            prefs,
            NoOpPremiumRepo,
            NoOpChunkRepo,
            NoOpConnectionRepo,
        )
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val dispatcher = TransferDispatcher(dao, prefs, scope, factory)

        (1..5L).forEach { dao.insertFake(queued(it)) }
        dispatcher.start()
        testScheduler.runCurrent()
        assertThat(dispatcher.activeIds()).hasSize(2)

        capFlow.value = 5
        testScheduler.runCurrent()
        assertThat(dispatcher.activeIds()).hasSize(5)

        (1..5L).forEach { executor.complete(it) }
        testScheduler.runCurrent()
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun cancelTransfer_cancelsTargetJobOnly() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeTransferRecordDao()
        val prefs = FakeTransferPrefs(capFlow = MutableStateFlow(3))
        val executor = HoldingTransferExecutor()
        val factory = TransferWorkerCoroutineFactory(
            FakeTransferRepository(dao),
            executor,
            NoOpConflictRepo,
            prefs,
            NoOpPremiumRepo,
            NoOpChunkRepo,
            NoOpConnectionRepo,
        )
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val dispatcher = TransferDispatcher(dao, prefs, scope, factory)

        (1..3L).forEach { dao.insertFake(queued(it)) }
        dispatcher.start()
        testScheduler.runCurrent()
        assertThat(dispatcher.activeIds()).containsExactly(1L, 2L, 3L)

        dispatcher.cancelWorker(2L)
        testScheduler.runCurrent()

        val ids = dispatcher.activeIds()
        assertThat(ids).doesNotContain(2L)
        assertThat(ids).contains(1L)
        assertThat(ids).contains(3L)

        executor.complete(1L)
        executor.complete(3L)
        testScheduler.runCurrent()
        scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun dispatchLoop_skipsRowsWithFutureNextRetryAt() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeTransferRecordDao(nowProvider = { 10_000L })
        val prefs = FakeTransferPrefs(capFlow = MutableStateFlow(3))
        val executor = HoldingTransferExecutor()
        val factory = TransferWorkerCoroutineFactory(
            FakeTransferRepository(dao),
            executor,
            NoOpConflictRepo,
            prefs,
            NoOpPremiumRepo,
            NoOpChunkRepo,
            NoOpConnectionRepo,
        )
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val dispatcher = TransferDispatcher(dao, prefs, scope, factory)

        dao.insertFake(queued(1L, nextRetryAt = 20_000L)) // future → skip
        dao.insertFake(queued(2L, nextRetryAt = 5_000L)) // past → dispatch
        dao.insertFake(queued(3L, nextRetryAt = null)) // no retry → dispatch

        dispatcher.start()
        testScheduler.runCurrent()

        assertThat(dispatcher.activeIds()).containsExactly(2L, 3L)

        executor.complete(2L)
        executor.complete(3L)
        testScheduler.runCurrent()
        scope.coroutineContext[Job]!!.cancel()
    }

    // ---- helpers -----------------------------------------------------------

    private fun queued(id: Long, nextRetryAt: Long? = null): TransferRecordEntity =
        TransferRecordEntity(
            id = id,
            serverProfileId = 1L,
            sourcePath = "/src/$id",
            destinationPath = "/dst/$id",
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.QUEUED,
            totalBytes = 100L,
            transferredBytes = 0L,
            queuedAt = id,
            nextRetryAt = nextRetryAt,
        )
}

/** In-memory fake [TransferRecordDao]. */
internal class FakeTransferRecordDao(
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : TransferRecordDao {
    private val rows = ConcurrentHashMap<Long, TransferRecordEntity>()
    private val count = MutableStateFlow(0)

    fun insertFake(entity: TransferRecordEntity) {
        rows[entity.id] = entity
        count.value = rows.size
    }

    fun snapshot(): List<TransferRecordEntity> = rows.values.toList()

    override fun getAll(): Flow<List<TransferRecordEntity>> = flowOf(rows.values.toList())
    override fun getActive(): Flow<List<TransferRecordEntity>> = flowOf(rows.values.toList())

    override suspend fun getById(id: Long): TransferRecordEntity? = rows[id]

    override suspend fun insert(record: TransferRecordEntity): Long {
        rows[record.id] = record
        count.value = rows.size
        return record.id
    }

    override suspend fun update(record: TransferRecordEntity) {
        rows[record.id] = record
    }

    override suspend fun clearCompleted() {
        rows.values.removeAll { it.status == TransferStatus.COMPLETED }
    }

    override suspend fun getReadyQueued(now: Long, limit: Int): List<TransferRecordEntity> =
        rows.values
            .filter { it.status == TransferStatus.QUEUED }
            .filter { it.nextRetryAt == null || it.nextRetryAt!! <= nowProvider() }
            .sortedBy { it.queuedAt }
            .take(limit)

    override suspend fun updateProgress(id: Long, transferred: Long, total: Long) {
        rows[id]?.let { rows[id] = it.copy(transferredBytes = transferred, totalBytes = total) }
    }

    override suspend fun updateStatus(
        id: Long,
        status: TransferStatus,
        error: String?,
        completedAt: Long?,
    ) {
        rows[id]?.let {
            rows[id] = it.copy(status = status, errorMessage = error, completedAt = completedAt)
        }
    }

    override suspend fun scheduleRetry(id: Long, nextRetryAt: Long) {
        rows[id]?.let {
            rows[id] = it.copy(
                status = TransferStatus.QUEUED,
                nextRetryAt = nextRetryAt,
                retryCount = it.retryCount + 1,
            )
        }
    }

    override suspend fun setNextRetryAt(id: Long, nextRetryAt: Long) {
        rows[id]?.let { rows[id] = it.copy(nextRetryAt = nextRetryAt) }
    }

    override fun observeNonTerminalCount(): Flow<Int> = count

    override suspend fun getByStatus(status: TransferStatus): List<TransferRecordEntity> =
        rows.values.filter { it.status == status }

    override suspend fun getByStatuses(statuses: List<TransferStatus>): List<TransferRecordEntity> =
        rows.values.filter { it.status in statuses }
}

/** Adapts the DAO-backed fake into a [TransferRepository]. */
internal class FakeTransferRepository(
    private val dao: FakeTransferRecordDao,
) : TransferRepository {
    override fun getAllTransfers(): Flow<List<TransferRequest>> = flowOf(emptyList())
    override fun getActiveTransfers(): Flow<List<TransferRequest>> = flowOf(emptyList())
    override suspend fun enqueue(transfer: TransferRequest): Long = 0L
    override suspend fun pause(transferId: Long) = Unit
    override suspend fun resume(transferId: Long) = Unit
    override suspend fun cancel(transferId: Long) = Unit
    override suspend fun clearCompleted() = Unit

    override suspend fun updateProgress(id: Long, transferred: Long, total: Long) {
        dao.updateProgress(id, transferred, total)
    }

    override suspend fun updateStatus(
        id: Long,
        status: TransferStatus,
        error: String?,
        completedAt: Long?,
    ) {
        dao.updateStatus(id, status, error, completedAt)
    }

    override suspend fun setNextRetryAt(id: Long, nextRetryAt: Long) {
        dao.setNextRetryAt(id, nextRetryAt)
    }

    override suspend fun scheduleRetry(id: Long, nextRetryAt: Long) {
        dao.scheduleRetry(id, nextRetryAt)
    }

    override suspend fun getTransferById(id: Long): TransferRequest? {
        val e = dao.snapshot().firstOrNull { it.id == id } ?: return null
        return TransferRequest(
            id = e.id,
            serverProfileId = e.serverProfileId,
            sourcePath = e.sourcePath,
            destinationPath = e.destinationPath,
            direction = e.direction,
            status = e.status,
            totalBytes = e.totalBytes,
            transferredBytes = e.transferredBytes,
            retryCount = e.retryCount,
        )
    }
}

/** Fake prefs backed entirely by constant flows + one mutable cap flow. */
internal class FakeTransferPrefs(
    private val capFlow: Flow<Int>,
) : AppPreferences(FakeDataStore) {
    override val maxParallelTransfers: Flow<Int> get() = capFlow
    override val autoResume: Flow<Boolean> get() = flowOf(false)
    override val overwriteMode: Flow<String> get() = flowOf("overwrite")
    override val maxRetryAttempts: Flow<Int> get() = flowOf(0)
    override val retryBackoffSeconds: Flow<Int> get() = flowOf(1)
}

/**
 * Unused [DataStore] supplied to [AppPreferences]'s primary constructor.
 * The fake prefs override every accessor it cares about, so the store is
 * never actually read.
 */
private object FakeDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = emptyFlow()

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences = emptyPreferences()
}

internal object NoOpConflictRepo : TransferConflictRepository {
    private val _flow = MutableSharedFlow<ConflictRequest>(extraBufferCapacity = 8)
    override val conflictRequests: SharedFlow<ConflictRequest> = _flow.asSharedFlow()
    override fun emitConflict(request: ConflictRequest) { _flow.tryEmit(request) }
    override suspend fun awaitResolution(conflictId: String): ConflictResolution = ConflictResolution.SKIP
    override fun resolve(conflictId: String, resolution: ConflictResolution) { /* no-op */ }
}

internal object NoOpPremiumRepo : PremiumRepository {
    override val isPremium: Flow<Boolean> = flowOf(false)
    override suspend fun refreshEntitlement() = Unit
    override suspend fun cacheEntitlement(value: Boolean) = Unit
    override suspend fun getCachedEntitlement(): Boolean = false
    override suspend fun getLastRefreshedAt(): Long? = null
}

internal object NoOpChunkRepo : TransferChunkRepository {
    override suspend fun upsertChunk(chunk: dev.ori.domain.model.TransferChunk): Long = 0L
    override suspend fun getChunksForTransfer(transferId: Long): List<dev.ori.domain.model.TransferChunk> = emptyList()
    override suspend fun updateChunkStatus(id: Long, status: dev.ori.domain.model.ChunkStatus, error: String?) = Unit
    override suspend fun deleteChunksForTransfer(transferId: Long) = Unit
}

internal object NoOpConnectionRepo : ConnectionRepository {
    override fun getAllProfiles() = flowOf(emptyList<dev.ori.domain.model.ServerProfile>())
    override fun getFavoriteProfiles() = flowOf(emptyList<dev.ori.domain.model.ServerProfile>())
    override suspend fun getProfileById(id: Long) = null
    override suspend fun getProfileCount() = 0
    override suspend fun saveProfile(profile: dev.ori.domain.model.ServerProfile): Long = 0L
    override suspend fun updateProfile(profile: dev.ori.domain.model.ServerProfile) = Unit
    override suspend fun deleteProfile(profile: dev.ori.domain.model.ServerProfile) = Unit
    override suspend fun connect(profileId: Long) = throw UnsupportedOperationException()
    override suspend fun disconnect(profileId: Long) = Unit
    override fun getActiveConnections() = flowOf(emptyList<dev.ori.domain.model.Connection>())
    override suspend fun getActiveSessionId(profileId: Long): String? = null
}

/**
 * Fake [TransferExecutor] whose uploads block until the test calls
 * [complete] for that transfer id (decoded from "/src/<id>").
 */
internal class HoldingTransferExecutor : TransferExecutor {
    private val gates = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    fun complete(transferId: Long) {
        gates.computeIfAbsent(transferId) { CompletableDeferred() }.complete(Unit)
    }

    override suspend fun upload(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val id = localPath.substringAfterLast('/').toLong()
        gates.computeIfAbsent(id) { CompletableDeferred() }.await()
    }

    override suspend fun download(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) = upload(sessionId, localPath, remotePath, offsetBytes, onProgress)

    override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? = null
}
