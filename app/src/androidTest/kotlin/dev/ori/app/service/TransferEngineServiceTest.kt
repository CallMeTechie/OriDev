package dev.ori.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.entity.ServerProfileEntity
import dev.ori.data.entity.TransferRecordEntity
import dev.ori.domain.repository.TransferEngineController
import dev.ori.feature.settings.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Tier 3 T3a — end-to-end Hilt instrumentation test for
 * [TransferEngineService].
 *
 * Walks the full engine lifecycle against the real :app Hilt graph with
 * [FakeTransferExecutor] substituted for the real SSH/FTP executor:
 *
 *  1. Pre-insert a `ServerProfileEntity` (FK target) and a QUEUED
 *     `TransferRecordEntity` via the injected DAOs.
 *  2. Call `controller.ensureRunning()` — brings up the foreground
 *     service, which boots the dispatcher and picks up the queued row.
 *  3. Wait until the DAO flow reports the row as ACTIVE.
 *  4. Send `ACTION_PAUSE_ALL` intent. Because the fake is slow (5 ticks
 *     * 50 ms) the worker is still in-flight when pause arrives.
 *  5. Wait until the row flips to PAUSED.
 *  6. Call `controller.resumeTransfer(id)` — flips the row back to
 *     QUEUED and re-starts the service so the dispatcher re-dispatches.
 *  7. Wait until the row reaches COMPLETED.
 *  8. Wait until `dao.observeNonTerminalCount()` emits 0 — this is the
 *     signal the service uses to stopSelf().
 *  9. Assert the aggregate foreground notification (id 2001) has been
 *     cancelled.
 *
 * The test does not require a real network connection — everything is
 * driven through the DAO + fake executor, with the real dispatcher,
 * worker, notification manager, and service glue in between.
 */
@HiltAndroidTest
class TransferEngineServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var controller: TransferEngineController

    @Inject lateinit var dao: TransferRecordDao

    @Inject lateinit var serverProfileDao: ServerProfileDao

    @Inject lateinit var prefs: AppPreferences

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            // Force overwrite mode so the worker's conflict path is never
            // entered regardless of the fake executor's remoteSize.
            prefs.setOverwriteMode("overwrite")
            // Make sure the database is pristine at the start of the test.
            dao.clearCompleted()
        }
    }

    @Test
    fun fullLifecycle_enqueueActivePauseResumeCompleted_stopsService() = runBlocking {
        // Arrange — FK parent row for the transfer.
        val profileId = serverProfileDao.insert(
            ServerProfileEntity(
                name = "fake",
                host = "127.0.0.1",
                port = 22,
                protocol = Protocol.SSH,
                username = "tester",
                authMethod = AuthMethod.PASSWORD,
                credentialRef = "fake-ref",
                sshKeyType = null,
                startupCommand = null,
                projectDirectory = null,
                claudeCodeModel = null,
                claudeMdPath = null,
            ),
        )

        // Pre-insert a QUEUED row — the service+dispatcher will pick it up
        // as soon as ensureRunning() wakes them.
        val transferId = dao.insert(
            TransferRecordEntity(
                serverProfileId = profileId,
                sourcePath = "/tmp/source.bin",
                destinationPath = "/tmp/dest.bin",
                direction = TransferDirection.UPLOAD,
                status = TransferStatus.QUEUED,
                totalBytes = FakeTransferExecutor.DEFAULT_TOTAL_BYTES,
            ),
        )

        // Act — start the service.
        ContextCompat.startForegroundService(
            context,
            Intent(context, TransferEngineService::class.java),
        )
        controller.ensureRunning()

        // 1. QUEUED -> ACTIVE
        awaitStatus(transferId, TransferStatus.ACTIVE)

        // 2. ACTIVE -> PAUSED via ACTION_PAUSE_ALL
        context.startService(
            Intent(context, TransferEngineService::class.java).apply {
                action = TransferEngineService.ACTION_PAUSE_ALL
            },
        )
        awaitStatus(transferId, TransferStatus.PAUSED)

        // 3. PAUSED -> QUEUED -> ACTIVE -> COMPLETED via resume.
        // Let the fake succeed quickly this time so the test stays snappy.
        withContext(Dispatchers.IO) {
            // no-op; included for readability of the phase boundary.
        }
        controller.resumeTransfer(transferId)
        awaitStatus(transferId, TransferStatus.COMPLETED)

        // 4. Non-terminal count should drain to 0; service should stopSelf().
        withTimeout(STOP_TIMEOUT_MS) {
            while (dao.observeNonTerminalCount().first() != 0) {
                // loop until the flow settles on 0.
            }
        }

        // 5. The aggregate foreground notification must be cancelled once
        //    the queue drains.
        val nm = context.getSystemService(NotificationManager::class.java)
        val active = nm.activeNotifications.map { it.id }
        assertThat(active).doesNotContain(TransferNotificationManager.NOTIFICATION_ID_SERVICE)
    }

    // -- helpers -----------------------------------------------------------

    private suspend fun awaitStatus(transferId: Long, expected: TransferStatus) {
        withTimeout(STATUS_TIMEOUT_MS) {
            while (true) {
                val row = dao.getById(transferId)
                if (row?.status == expected) return@withTimeout
                // Poll on a short interval — much simpler than racing a
                // Flow.collect across the pause/resume phase boundary.
                kotlinx.coroutines.delay(POLL_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val STATUS_TIMEOUT_MS = 10_000L
        const val STOP_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
