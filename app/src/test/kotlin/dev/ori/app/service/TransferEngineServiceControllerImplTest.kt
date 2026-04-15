package dev.ori.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.ori.core.common.model.TransferStatus
import dev.ori.data.dao.TransferRecordDao
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Phase 12 P12.5 catch-up — unit tests for
 * [TransferEngineServiceControllerImpl].
 *
 * We stub `Context.getPackageName()` so real `Intent(Context, Class)`
 * construction works under the JVM Android stubs and mock
 * `ContextCompat.startForegroundService` (static). Because `android.jar`
 * is a stub with `returnDefaultValues = true`, we cannot inspect
 * `Intent.action` / `Intent.component` after the fact — those getters
 * return null regardless of what was set. We therefore only verify that
 * the right sink (startService / startForegroundService) was invoked
 * with an Intent, plus the DB/dispatcher side effects. The full
 * `ACTION_PAUSE_ALL` / component round-trip is covered by the
 * instrumentation test, not here. No Robolectric needed.
 */
class TransferEngineServiceControllerImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val dao = mockk<TransferRecordDao>(relaxed = true)
    private val dispatcher = mockk<TransferDispatcher>(relaxed = true)
    private lateinit var controller: TransferEngineServiceControllerImpl

    @BeforeEach
    fun setup() {
        // Intent(Context, Class) delegates to ComponentName(Context, Class)
        // which calls context.getPackageName(); give it a valid value so
        // constructors don't NPE on the Android JVM stub.
        every { context.packageName } returns "dev.ori.test"
        mockkStatic(ContextCompat::class)
        every { ContextCompat.startForegroundService(any(), any()) } just Runs

        controller = TransferEngineServiceControllerImpl(context, dao, dispatcher)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun ensureRunning_startsForegroundService() {
        val intentSlot = slot<Intent>()
        every {
            ContextCompat.startForegroundService(eq(context), capture(intentSlot))
        } just Runs

        controller.ensureRunning()

        verify(exactly = 1) {
            ContextCompat.startForegroundService(eq(context), any<Intent>())
        }
        assert(intentSlot.isCaptured)
        // Plain startService must NOT have been used for the ensureRunning
        // path — ensureRunning is always foreground.
        verify(exactly = 0) { context.startService(any<Intent>()) }
    }

    @Test
    fun pauseTransfer_cancelsWorkerDirectly() = runTest {
        controller.pauseTransfer(42L)

        verify(exactly = 1) { dispatcher.cancelWorker(42L) }
        // No Intent, no DB write — pauseTransfer is a pure delegation.
        coVerify(exactly = 0) { dao.updateStatus(any(), any(), any(), any()) }
    }

    @Test
    fun cancelTransfer_cancelsWorkerAndMarksCancelled() = runTest {
        controller.cancelTransfer(42L)

        verify(exactly = 1) { dispatcher.cancelWorker(42L) }
        coVerify(exactly = 1) {
            dao.updateStatus(42L, TransferStatus.CANCELLED, null, any())
        }
    }

    @Test
    fun pauseAll_dispatchesIntentViaStartService() = runTest {
        val intentSlot = slot<Intent>()
        every { context.startService(capture(intentSlot)) } returns null

        controller.pauseAll()

        verify(exactly = 1) { context.startService(any<Intent>()) }
        assert(intentSlot.isCaptured)
        // pauseAll is a plain (non-foreground) signal — ContextCompat's
        // foreground path must NOT be used.
        verify(exactly = 0) {
            ContextCompat.startForegroundService(any(), any<Intent>())
        }
    }
}
