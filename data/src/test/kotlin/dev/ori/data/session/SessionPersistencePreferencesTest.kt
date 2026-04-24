package dev.ori.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import dev.ori.core.security.crash.NonFatalErrorLogger
import dev.ori.domain.model.TabMemo
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * JVM-only round-trip tests for [SessionPersistencePreferences] backed by
 * a pure-JVM okio DataStore. Uses `@TempDir` so JUnit cleans up the
 * backing preferences file after each test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPersistencePreferencesTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: SessionPersistencePreferences

    @BeforeEach
    fun setUp() {
        val file = File(tempDir, "session_${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
            produceFile = { file },
        )
        prefs = SessionPersistencePreferences(dataStore)
    }

    @Test
    fun `profileIds round-trip`() = runTest {
        prefs.setProfileIds(setOf(1L, 2L, 3L))
        assertThat(prefs.profileIds.first()).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `tabMemos round-trip`() = runTest {
        val memos = listOf(TabMemo(1L, 3, 0), TabMemo(2L, 1, 0))
        prefs.setTabMemos(memos)
        assertThat(prefs.tabMemos.first()).containsExactlyElementsIn(memos).inOrder()
    }

    @Test
    fun `focusedProfileId round-trip including null`() = runTest {
        prefs.setFocusedProfileId(42L)
        assertThat(prefs.focusedProfileId.first()).isEqualTo(42L)
        prefs.setFocusedProfileId(null)
        assertThat(prefs.focusedProfileId.first()).isNull()
    }

    @Test
    fun `remotePaths round-trip`() = runTest {
        prefs.setRemotePath(1L, "/tmp")
        prefs.setRemotePath(2L, "/var/log")
        assertThat(prefs.remotePaths.first()).containsExactly(1L, "/tmp", 2L, "/var/log")
    }

    @Test
    fun `lastTopLevelRoute default is connections`() = runTest {
        assertThat(prefs.lastTopLevelRoute.first()).isEqualTo("connections")
    }

    @Test
    fun `lastTopLevelRoute round-trip`() = runTest {
        prefs.setLastTopLevelRoute("terminal")
        assertThat(prefs.lastTopLevelRoute.first()).isEqualTo("terminal")
    }

    @Test
    fun `clearResumeSubset wipes four keys but preserves lastTopLevelRoute`() = runTest {
        prefs.setProfileIds(setOf(1L))
        prefs.setTabMemos(listOf(TabMemo(1L, 2, 0)))
        prefs.setFocusedProfileId(1L)
        prefs.setRemotePath(1L, "/tmp")
        prefs.setLastTopLevelRoute("filemanager")
        prefs.clearResumeSubset()
        assertThat(prefs.profileIds.first()).isEmpty()
        assertThat(prefs.tabMemos.first()).isEmpty()
        assertThat(prefs.focusedProfileId.first()).isNull()
        assertThat(prefs.remotePaths.first()).isEmpty()
        assertThat(prefs.lastTopLevelRoute.first()).isEqualTo("filemanager")
    }

    @Test
    fun `leftPaneTabId round-trip including null`() = runTest {
        prefs.setLeftPaneTabId("tab-42")
        assertThat(prefs.leftPaneTabId.first()).isEqualTo("tab-42")
        prefs.setLeftPaneTabId(null)
        assertThat(prefs.leftPaneTabId.first()).isNull()
    }

    @Test
    fun `rightPaneTabId round-trip including null`() = runTest {
        prefs.setRightPaneTabId("tab-7")
        assertThat(prefs.rightPaneTabId.first()).isEqualTo("tab-7")
        prefs.setRightPaneTabId(null)
        assertThat(prefs.rightPaneTabId.first()).isNull()
    }

    @Test
    fun `activePaneIndex default is 0 and round-trips`() = runTest {
        assertThat(prefs.activePaneIndex.first()).isEqualTo(0)
        prefs.setActivePaneIndex(1)
        assertThat(prefs.activePaneIndex.first()).isEqualTo(1)
    }

    @Test
    fun `clearResumeSubset wipes pane keys but preserves lastTopLevelRoute`() = runTest {
        prefs.setLeftPaneTabId("L")
        prefs.setRightPaneTabId("R")
        prefs.setActivePaneIndex(1)
        prefs.setLastTopLevelRoute("terminal")
        prefs.clearResumeSubset()
        assertThat(prefs.leftPaneTabId.first()).isNull()
        assertThat(prefs.rightPaneTabId.first()).isNull()
        assertThat(prefs.activePaneIndex.first()).isEqualTo(0)
        assertThat(prefs.lastTopLevelRoute.first()).isEqualTo("terminal")
    }

    @Test
    fun `corrupt tabMemos blob returns empty list and logs`() = runTest {
        mockkObject(NonFatalErrorLogger)
        // `NonFatalErrorLogger` is an Android-aware object — its `log`
        // body calls `android.util.Log.w` when the app Context hasn't
        // been installed, which throws "not mocked" under a pure-JVM
        // unit test. Stub it to a no-op so the production call-site
        // runs without tripping the framework gap.
        every {
            NonFatalErrorLogger.log(
                category = any(),
                throwable = any(),
                contextNote = any(),
            )
        } returns Unit
        try {
            dataStore.edit { it[stringPreferencesKey("tab_memos")] = "not-json" }
            assertThat(prefs.tabMemos.first()).isEmpty()
            verify(exactly = 1) {
                NonFatalErrorLogger.log(
                    category = "persist-corrupt",
                    throwable = any(),
                    contextNote = any(),
                )
            }
        } finally {
            unmockkObject(NonFatalErrorLogger)
        }
    }

    @Test
    fun `corrupt remotePaths blob returns empty map and logs`() = runTest {
        mockkObject(NonFatalErrorLogger)
        every {
            NonFatalErrorLogger.log(category = any(), throwable = any(), contextNote = any())
        } returns Unit
        try {
            dataStore.edit { it[stringPreferencesKey("remote_paths")] = "not-json" }
            assertThat(prefs.remotePaths.first()).isEmpty()
            verify(exactly = 1) {
                NonFatalErrorLogger.log(
                    category = "persist-corrupt",
                    throwable = any(),
                    contextNote = any(),
                )
            }
        } finally {
            unmockkObject(NonFatalErrorLogger)
        }
    }

    @Test
    fun `malformed focusedProfileId returns null and logs`() = runTest {
        mockkObject(NonFatalErrorLogger)
        every {
            NonFatalErrorLogger.log(category = any(), throwable = any(), contextNote = any())
        } returns Unit
        try {
            dataStore.edit { it[stringPreferencesKey("focused_profile_id")] = "not-a-number" }
            assertThat(prefs.focusedProfileId.first()).isNull()
            verify(exactly = 1) {
                NonFatalErrorLogger.log(
                    category = "persist-corrupt",
                    throwable = any(),
                    contextNote = any(),
                )
            }
        } finally {
            unmockkObject(NonFatalErrorLogger)
        }
    }
}
