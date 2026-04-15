package dev.ori.app.service

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.ori.feature.settings.data.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 12 Tier 3 T3c — Robolectric coverage for [TransferNotificationManager].
 *
 * PR #80 covered the pure-Kotlin [TransferNotificationText] string helpers,
 * but the bulk of [TransferNotificationManager] (channel creation,
 * NotificationCompat.Builder chains, PendingIntent wiring and posting to
 * [NotificationManager]) remained untested for lack of a Robolectric setup.
 *
 * These tests run as JVM unit tests on the Robolectric sandboxed Android
 * runtime (SDK 33 — the "POST_NOTIFICATIONS required" path that exercises
 * [NotificationManager.getActiveNotifications]).
 */
@RunWith(RobolectricTestRunner::class)
// SDK 34 matches the app module's minSdk. SDK 33+ exercises the
// POST_NOTIFICATIONS path and NotificationManager.getActiveNotifications().
// We pin `application = Application::class` so Robolectric does NOT stand
// up the real `OriDevApplication`, which pulls in the Hilt graph +
// KeyStoreManager (AndroidKeyStore is unavailable on JVM Robolectric).
@Config(sdk = [34], application = Application::class)
class TransferNotificationManagerRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private lateinit var manager: TransferNotificationManager

    @Before
    fun setUp() {
        // Clear any notifications left over from a prior test case so
        // assertNotNull/count queries only see what *this* test posted.
        notificationManager.cancelAll()
        manager = TransferNotificationManager(context, FakeAppPreferences(notifyTransferDone = true))
    }

    @Test
    fun init_createsNotificationChannel() {
        // The manager is constructed in @Before; channel creation happens
        // in its init block.
        val channel = notificationManager.getNotificationChannel(
            TransferNotificationManager.CHANNEL_ID,
        )
        assertThat(channel).isNotNull()
        assertThat(channel!!.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun buildAggregateNotification_singleUpload_setsTitleAndDeterminateProgress() {
        val notification = manager.buildAggregateNotification(
            activeCount = 1,
            transferredBytes = 500_000L,
            totalBytes = 1_000_000L,
        )
        val extras = notification.extras

        assertThat(extras.getString(NotificationCompat.EXTRA_TITLE))
            .isEqualTo("Ori:Dev transfers")
        assertThat(extras.getString(NotificationCompat.EXTRA_TEXT))
            .isEqualTo("1 active transfer")
        // Determinate progress bar populated when totalBytes > 0.
        assertThat(extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX)).isEqualTo(100)
        assertThat(extras.getInt(NotificationCompat.EXTRA_PROGRESS)).isEqualTo(50)
        assertThat(extras.getBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE)).isFalse()
        // Both Pause all / Cancel all actions should be attached.
        assertThat(notification.actions).isNotNull()
        assertThat(notification.actions.size).isEqualTo(2)
        assertThat(notification.actions[0].title.toString()).isEqualTo("Pause all")
        assertThat(notification.actions[1].title.toString()).isEqualTo("Cancel all")
    }

    @Test
    fun buildAggregateNotification_unknownTotal_setsIndeterminateProgress() {
        val notification = manager.buildAggregateNotification(
            activeCount = 0,
            transferredBytes = 0L,
            totalBytes = 0L,
        )
        val extras = notification.extras

        assertThat(extras.getString(NotificationCompat.EXTRA_TEXT))
            .isEqualTo("Preparing transfers")
        assertThat(extras.getBoolean(NotificationCompat.EXTRA_PROGRESS_INDETERMINATE)).isTrue()
    }

    @Test
    fun postCompletionNotification_whenPrefsEnabled_postsToManager() = kotlinx.coroutines.runBlocking {
        manager.postCompletionNotification(
            transferId = 42L,
            fileName = "test.txt",
            sizeBytes = 1024L,
            isUpload = true,
        )
        val expectedId = TransferNotificationManager.NOTIFICATION_ID_DONE_BASE + 42
        val active = notificationManager.activeNotifications
        val match = active.firstOrNull { it.id == expectedId }
        assertThat(match).isNotNull()
        val extras = match!!.notification.extras
        assertThat(extras.getString(NotificationCompat.EXTRA_TITLE))
            .isEqualTo("Uploaded test.txt")
        assertThat(extras.getString(NotificationCompat.EXTRA_TEXT)).isEqualTo("1.0 KiB")
    }

    @Test
    fun postCompletionNotification_whenPrefsDisabled_doesNotPost() = kotlinx.coroutines.runBlocking {
        val disabled = TransferNotificationManager(
            context,
            FakeAppPreferences(notifyTransferDone = false),
        )
        disabled.postCompletionNotification(
            transferId = 99L,
            fileName = "a.txt",
            sizeBytes = 1024L,
            isUpload = true,
        )
        val expectedId = TransferNotificationManager.NOTIFICATION_ID_DONE_BASE + 99
        val active = notificationManager.activeNotifications
        assertThat(active.any { it.id == expectedId }).isFalse()
    }

    @Test
    fun postFailureNotification_postsWithRetryAction() {
        manager.postFailureNotification(
            transferId = 7L,
            fileName = "x.bin",
            error = "Connection reset",
        )
        val expectedId = TransferNotificationManager.NOTIFICATION_ID_FAIL_BASE + 7
        val active = notificationManager.activeNotifications
        val match = active.firstOrNull { it.id == expectedId }
        assertThat(match).isNotNull()

        val notification = match!!.notification
        val extras = notification.extras
        assertThat(extras.getString(NotificationCompat.EXTRA_TITLE))
            .isEqualTo("Transfer failed: x.bin")
        assertThat(extras.getString(NotificationCompat.EXTRA_TEXT))
            .isEqualTo("Connection reset")

        assertThat(notification.actions).isNotNull()
        assertThat(notification.actions.size).isEqualTo(1)
        assertThat(notification.actions[0].title.toString()).contains("Retry")
        // PendingIntent targets the transfer engine service.
        assertThat(notification.actions[0].actionIntent).isNotNull()
    }

    @Test
    fun cancelAggregate_clearsForegroundNotification() {
        val n = manager.buildAggregateNotification(
            activeCount = 2,
            transferredBytes = 1000L,
            totalBytes = 2000L,
        )
        manager.postAggregate(n)
        assertThat(
            notificationManager.activeNotifications.any {
                it.id == TransferNotificationManager.NOTIFICATION_ID_SERVICE
            },
        ).isTrue()

        manager.cancelAggregate()

        assertThat(
            notificationManager.activeNotifications.any {
                it.id == TransferNotificationManager.NOTIFICATION_ID_SERVICE
            },
        ).isFalse()
    }
}

/**
 * Test fake for [AppPreferences] wired via a stub [DataStore] that emits
 * a single [Preferences] snapshot with `notify_transfer_done` set to the
 * constructor value. We do not override any `val` on [AppPreferences] —
 * those properties are not `open` — so instead we feed the real reads
 * through the constant [DataStore]. This keeps the production class
 * surface untouched.
 */
private class FakeAppPreferences(
    notifyTransferDone: Boolean,
) : AppPreferences(ConstantDataStore(notifyTransferDone))

private class ConstantDataStore(
    notifyTransferDone: Boolean,
) : DataStore<Preferences> {
    private val snapshot: Preferences = mutablePreferencesOf().apply {
        set(booleanPreferencesKey("notify_transfer_done"), notifyTransferDone)
    }

    override val data: Flow<Preferences> = flowOf(snapshot)

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences = transform(snapshot)
}
