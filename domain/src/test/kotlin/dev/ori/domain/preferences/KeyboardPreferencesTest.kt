package dev.ori.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.domain.model.KeyboardMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * In-memory fake mirroring the fixture used in
 * `core-security`'s CrashReportingPreferencesTest — keeps tests hermetic
 * and exercises the real `DataStore.edit { … }` pathway without touching
 * the filesystem or the Android runtime.
 */
private class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.update { updated }
        return updated
    }
}

class KeyboardPreferencesTest {

    @Test
    fun keyboardModeFlow_emptyStore_emitsDefaultCustom() = runTest {
        val prefs = KeyboardPreferences(FakePreferencesDataStore())

        prefs.keyboardModeFlow.test {
            assertThat(awaitItem()).isEqualTo(KeyboardMode.CUSTOM)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun defaultMode_afterFreshInstall_isCustom() {
        // Sanity guard: do NOT change this without updating the Phase 14
        // plan + release notes. Existing users rely on CUSTOM on upgrade.
        assertThat(KeyboardPreferences.DEFAULT_MODE).isEqualTo(KeyboardMode.CUSTOM)
    }

    @Test
    fun setKeyboardMode_hybrid_roundTripsThroughFlow() = runTest {
        val prefs = KeyboardPreferences(FakePreferencesDataStore())

        prefs.setKeyboardMode(KeyboardMode.HYBRID)

        prefs.keyboardModeFlow.test {
            assertThat(awaitItem()).isEqualTo(KeyboardMode.HYBRID)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setKeyboardMode_systemOnly_roundTripsThroughFlow() = runTest {
        val prefs = KeyboardPreferences(FakePreferencesDataStore())

        prefs.setKeyboardMode(KeyboardMode.SYSTEM_ONLY)

        prefs.keyboardModeFlow.test {
            assertThat(awaitItem()).isEqualTo(KeyboardMode.SYSTEM_ONLY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setKeyboardMode_backToCustom_emitsCustom() = runTest {
        val prefs = KeyboardPreferences(FakePreferencesDataStore())

        prefs.setKeyboardMode(KeyboardMode.HYBRID)
        prefs.setKeyboardMode(KeyboardMode.CUSTOM)

        prefs.keyboardModeFlow.test {
            assertThat(awaitItem()).isEqualTo(KeyboardMode.CUSTOM)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun keyboardModeFlow_withPreExistingHybrid_emitsHybrid() = runTest {
        val seeded = mutablePreferencesOf().apply {
            set(stringPreferencesKey(KeyboardPreferences.KEY_MODE), KeyboardMode.HYBRID.name)
        }

        val prefs = KeyboardPreferences(FakePreferencesDataStore(seeded))

        prefs.keyboardModeFlow.test {
            assertThat(awaitItem()).isEqualTo(KeyboardMode.HYBRID)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun keyboardModeFlow_withUnknownStoredValue_fallsBackToDefault() = runTest {
        // Forward-compat guard: a future release might write a new enum
        // value ("VOICE_ONLY"), then the user rolls back to this build.
        // The old build must not crash — it must fall back to CUSTOM.
        val seeded = mutablePreferencesOf().apply {
            set(stringPreferencesKey(KeyboardPreferences.KEY_MODE), "VOICE_ONLY_FUTURE")
        }

        val prefs = KeyboardPreferences(FakePreferencesDataStore(seeded))

        prefs.keyboardModeFlow.test {
            assertThat(awaitItem()).isEqualTo(KeyboardPreferences.DEFAULT_MODE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun datastoreName_value_equalsOriKeyboard() {
        // Guard against accidental rename — the downstream Hilt provider
        // passes this to `context.preferencesDataStoreFile(...)` and
        // changing it silently would lose user preferences on upgrade.
        assertThat(KeyboardPreferences.DATASTORE_NAME).isEqualTo("ori_keyboard")
    }
}
