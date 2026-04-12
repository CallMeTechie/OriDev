package dev.ori.core.security.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

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

class CrashReportingPreferencesTest {

    @Test
    fun enabled_emitsFalseByDefault() = runTest {
        val prefs = CrashReportingPreferences(FakePreferencesDataStore())

        prefs.enabled.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setEnabled_true_persistsAndEmits() = runTest {
        val prefs = CrashReportingPreferences(FakePreferencesDataStore())

        prefs.setEnabled(true)

        prefs.enabled.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setEnabled_toggleBackToFalse_emitsFalse() = runTest {
        val prefs = CrashReportingPreferences(FakePreferencesDataStore())

        prefs.setEnabled(true)
        prefs.setEnabled(false)

        prefs.enabled.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun defaultEnabled_constant_isFalseForGdprCompliance() {
        // Sanity guard: do NOT change this without updating the privacy policy.
        @Suppress("KotlinConstantConditions")
        assertThat(CrashReportingPreferences.DEFAULT_ENABLED).isFalse()
    }

    @Test
    fun enabled_withPreExistingTruePreference_emitsTrue() = runTest {
        val seeded = mutablePreferencesOf().apply {
            set(
                androidx.datastore.preferences.core.booleanPreferencesKey("enabled"),
                true,
            )
        }
        val prefs = CrashReportingPreferences(FakePreferencesDataStore(seeded))

        prefs.enabled.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
