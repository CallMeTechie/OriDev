package dev.ori.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [AppPreferences] backed by an on-disk DataStore in a
 * JUnit5 [TempDir]. Covers the Phase 11 P4.6 retry-policy round-trips
 * that landed without test coverage, plus a sanity check that the
 * aggregated `all` snapshot reflects individual setters and matches the
 * default snapshot used by `SettingsState.DEFAULT_PREFERENCES`.
 *
 * The DataStore is instantiated with an [UnconfinedTestDispatcher]-backed
 * [CoroutineScope] that is cancelled in [tearDown] so the underlying file
 * handle is released before JUnit cleans up the temp directory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: AppPreferences

    @BeforeEach
    fun setUp() {
        dataStoreScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        prefs = AppPreferences(dataStore)
    }

    @AfterEach
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun maxRetryAttempts_defaultIs3() = runTest {
        assertThat(prefs.maxRetryAttempts.first()).isEqualTo(3)
    }

    @Test
    fun maxRetryAttempts_roundTrip() = runTest {
        prefs.setMaxRetryAttempts(7)
        assertThat(prefs.maxRetryAttempts.first()).isEqualTo(7)
    }

    @Test
    fun retryBackoffSeconds_defaultIs10() = runTest {
        assertThat(prefs.retryBackoffSeconds.first()).isEqualTo(10)
    }

    @Test
    fun retryBackoffSeconds_roundTrip() = runTest {
        prefs.setRetryBackoffSeconds(45)
        assertThat(prefs.retryBackoffSeconds.first()).isEqualTo(45)
    }

    @Test
    fun maxParallelTransfers_defaultIs3_andRoundTrips() = runTest {
        assertThat(prefs.maxParallelTransfers.first()).isEqualTo(3)
        prefs.setMaxParallelTransfers(5)
        assertThat(prefs.maxParallelTransfers.first()).isEqualTo(5)
    }

    @Test
    fun autoResume_defaultTrue_andToggle() = runTest {
        assertThat(prefs.autoResume.first()).isTrue()
        prefs.setAutoResume(false)
        assertThat(prefs.autoResume.first()).isFalse()
        prefs.setAutoResume(true)
        assertThat(prefs.autoResume.first()).isTrue()
    }

    @Test
    fun all_snapshotReflectsIndividualSetters() = runTest {
        prefs.setTheme("dark")
        prefs.setMaxParallelTransfers(5)
        prefs.setMaxRetryAttempts(2)
        prefs.setRetryBackoffSeconds(20)

        val snapshot = prefs.all.first()

        assertThat(snapshot.theme).isEqualTo("dark")
        assertThat(snapshot.maxParallelTransfers).isEqualTo(5)
        assertThat(snapshot.maxRetryAttempts).isEqualTo(2)
        assertThat(snapshot.retryBackoffSeconds).isEqualTo(20)
        // Unset keys still report defaults.
        assertThat(snapshot.accent).isEqualTo("indigo")
        assertThat(snapshot.fontSize).isEqualTo(14)
        assertThat(snapshot.autoResume).isTrue()
        assertThat(snapshot.overwriteMode).isEqualTo("ask")
    }

    @Test
    fun all_snapshotInitialDefault_matchesSettingsStateDefault() = runTest {
        // SettingsState.DEFAULT_PREFERENCES is private to the ui package, so
        // we duplicate the expected snapshot here. If either side drifts, this
        // test will fail and force a re-sync — the redundancy is intentional.
        val expected = AppPreferencesSnapshot(
            theme = "system",
            accent = "indigo",
            fontSize = 14,
            terminalFont = "JetBrains Mono",
            defaultShell = "/bin/bash",
            scrollback = 10_000,
            bellMode = "visible",
            hardwareKeyboard = false,
            keyboardToolbar = true,
            maxParallelTransfers = 3,
            autoResume = true,
            overwriteMode = "ask",
            maxRetryAttempts = 3,
            retryBackoffSeconds = 10,
            biometricUnlock = false,
            autoLockTimeoutMinutes = 5,
            clipboardClearSeconds = 30,
            notifyTransferDone = true,
            notifyConnection = true,
            notifyClaude = false,
            notifyWear = true,
        )

        assertThat(prefs.all.first()).isEqualTo(expected)
    }
}
