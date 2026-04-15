package dev.ori.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.db.OriDevDatabase
import dev.ori.data.entity.ServerProfileEntity
import dev.ori.data.entity.TransferRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferRecordDaoTest {

    private lateinit var db: OriDevDatabase
    private lateinit var dao: TransferRecordDao
    private var profileId: Long = 0L

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OriDevDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transferRecordDao()
        profileId = db.serverProfileDao().insert(
            ServerProfileEntity(
                name = "test",
                host = "192.168.1.1",
                port = 22,
                protocol = Protocol.SSH,
                username = "user",
                authMethod = AuthMethod.PASSWORD,
                credentialRef = "alias_test",
                sshKeyType = null,
                startupCommand = null,
                projectDirectory = null,
                claudeCodeModel = null,
                claudeMdPath = null,
            ),
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun record(
        status: TransferStatus = TransferStatus.QUEUED,
        queuedAt: Long = 0L,
        nextRetryAt: Long? = null,
        retryCount: Int = 0,
        transferredBytes: Long = 0L,
        totalBytes: Long = 1000L,
    ) = TransferRecordEntity(
        serverProfileId = profileId,
        sourcePath = "/src",
        destinationPath = "/dst",
        direction = TransferDirection.UPLOAD,
        status = status,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        queuedAt = queuedAt,
        nextRetryAt = nextRetryAt,
        retryCount = retryCount,
    )

    @Test
    fun getReadyQueued_skipsRowsWithFutureNextRetryAt() = runTest {
        val now = 10_000L
        val readyId = dao.insert(record(queuedAt = 1L, nextRetryAt = null))
        dao.insert(record(queuedAt = 2L, nextRetryAt = now + 60_000L))

        val ready = dao.getReadyQueued(now = now, limit = 10)

        assertThat(ready).hasSize(1)
        assertThat(ready[0].id).isEqualTo(readyId)
    }

    @Test
    fun updateProgress_updatesOnlyTargetRow() = runTest {
        val id1 = dao.insert(record(transferredBytes = 0L, totalBytes = 100L))
        val id2 = dao.insert(record(transferredBytes = 0L, totalBytes = 100L))

        dao.updateProgress(id = id1, transferred = 500L, total = 1000L)

        val r1 = dao.getById(id1)!!
        val r2 = dao.getById(id2)!!
        assertThat(r1.transferredBytes).isEqualTo(500L)
        assertThat(r1.totalBytes).isEqualTo(1000L)
        assertThat(r2.transferredBytes).isEqualTo(0L)
        assertThat(r2.totalBytes).isEqualTo(100L)
    }

    @Test
    fun observeNonTerminalCount_emitsOnStatusChange() = runTest {
        val id = dao.insert(record(status = TransferStatus.QUEUED))

        dao.observeNonTerminalCount().test {
            assertThat(awaitItem()).isEqualTo(1)
            dao.updateStatus(
                id = id,
                status = TransferStatus.COMPLETED,
                error = null,
                completedAt = 123L,
            )
            assertThat(awaitItem()).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun insert_cancelled_roundTripsThroughSchema() = runTest {
        // Tier 2 T2b — adding TransferStatus.CANCELLED does not change the
        // Room schema hash (enum stored as TEXT via Converters), so we must
        // prove that inserting a CANCELLED row against the existing v3
        // schema succeeds and that the value round-trips cleanly.
        val id = dao.insert(record(status = TransferStatus.CANCELLED))

        val row = dao.getById(id)!!
        assertThat(row.status).isEqualTo(TransferStatus.CANCELLED)
        assertThat(row.status.isTerminal).isTrue()
        assertThat(row.status.isActive).isFalse()

        // CANCELLED rows must not be counted as non-terminal (the
        // foreground service uses observeNonTerminalCount to decide when
        // to stopSelf()).
        dao.observeNonTerminalCount().test {
            assertThat(awaitItem()).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateStatus_toCancelled_marksRowTerminal() = runTest {
        val id = dao.insert(record(status = TransferStatus.ACTIVE))

        dao.updateStatus(
            id = id,
            status = TransferStatus.CANCELLED,
            error = null,
            completedAt = 999L,
        )

        val row = dao.getById(id)!!
        assertThat(row.status).isEqualTo(TransferStatus.CANCELLED)
        assertThat(row.completedAt).isEqualTo(999L)
        assertThat(row.errorMessage).isNull()
    }

    @Test
    fun scheduleRetry_setsNextRetryAtAndStatus() = runTest {
        val nowMillis = 50_000L
        val id = dao.insert(record(status = TransferStatus.ACTIVE, retryCount = 0))

        dao.scheduleRetry(id = id, nextRetryAt = nowMillis + 10_000L)

        val updated = dao.getById(id)!!
        assertThat(updated.status).isEqualTo(TransferStatus.QUEUED)
        assertThat(updated.nextRetryAt).isEqualTo(nowMillis + 10_000L)
        assertThat(updated.retryCount).isEqualTo(1)
    }
}
