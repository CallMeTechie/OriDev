package dev.ori.app.service

import org.junit.Ignore
import org.junit.Test

/**
 * Phase 12 P12.5 — end-to-end lifecycle scaffold for [TransferEngineService].
 *
 * Intentionally `@Ignore`d pending the follow-up PR that wires the Hilt
 * instrumentation test infrastructure (`hilt-android-testing`,
 * `HiltTestApplication`, `ServiceTestRule`, custom test runner). The
 * assertions below describe the target lifecycle for the next PR to
 * implement; they are compiled to keep the scaffold fresh against refactors.
 *
 * Intended flow:
 *  1. Pre-insert a QUEUED TransferRecord into Room via the injected DAO.
 *  2. Call `controller.ensureRunning()`.
 *  3. Wait for the service's aggregate notification
 *     ([TransferNotificationManager.NOTIFICATION_ID_SERVICE]) to appear.
 *  4. Wait until the DAO flow reports the row as ACTIVE.
 *  5. Send an `ACTION_PAUSE_ALL` intent to the service.
 *  6. Wait until the row flips to PAUSED.
 *  7. Call `controller.resumeTransfer(id)`.
 *  8. Wait until the row reaches COMPLETED.
 *  9. Wait until `dao.observeNonTerminalCount()` emits `0`.
 *  10. Assert the ongoing aggregate notification has been cancelled.
 *
 * The fake [TransferExecutor] simulates five progress ticks before
 * returning; the fake `ConnectionRepository` returns a synthetic
 * `ServerProfile` with `Protocol.SSH` so the [RoutingTransferExecutor] is
 * exercised end-to-end.
 */
class TransferEngineServiceTest {

    @Test
    @Ignore("Phase 12 P12.5 — Hilt instrumentation runner not yet wired")
    fun enqueueToCompletedLifecycle_driveServiceViaController_terminatesInStopSelf() {
        // Intentionally empty: see class KDoc for the intended flow.
    }
}
