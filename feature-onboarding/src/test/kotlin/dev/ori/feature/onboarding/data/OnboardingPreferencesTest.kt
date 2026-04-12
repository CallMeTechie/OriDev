package dev.ori.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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

class OnboardingPreferencesTest {

    @Test
    fun completed_emitsFalseByDefault() = runTest {
        val prefs = OnboardingPreferences(FakePreferencesDataStore())

        prefs.completed.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun markCompleted_setsFlagToTrue() = runTest {
        val prefs = OnboardingPreferences(FakePreferencesDataStore())

        prefs.markCompleted()

        prefs.completed.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun completed_rereadAfterMarkCompleted_staysTrue() = runTest {
        val prefs = OnboardingPreferences(FakePreferencesDataStore())

        prefs.markCompleted()

        // First read.
        prefs.completed.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        // Second read from a new collector — still true.
        prefs.completed.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
