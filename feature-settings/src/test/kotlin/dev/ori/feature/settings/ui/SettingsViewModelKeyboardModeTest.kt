package dev.ori.feature.settings.ui

import android.app.Application
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.security.preferences.CrashReportingPreferences
import dev.ori.domain.model.GrantedTree
import dev.ori.domain.model.KeyboardMode
import dev.ori.domain.preferences.KeyboardPreferences
import dev.ori.domain.repository.StorageAccessRepository
import dev.ori.domain.usecase.CheckPremiumUseCase
import dev.ori.feature.settings.data.AppPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Phase 14 Task 14.6 — covers the [SettingsViewModel.setKeyboardMode] +
 * [SettingsViewModel.state] round-trip the picker UI relies on.
 *
 * Mocks the four upstream collaborators so the test only exercises the
 * ViewModel's flow plumbing, not the DataStore-backed preference
 * classes (those have their own dedicated tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelKeyboardModeTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val keyboardModeFlow = MutableStateFlow(KeyboardMode.CUSTOM)
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        keyboardModeFlow.value = KeyboardMode.CUSTOM

        val keyboardPreferences = mockk<KeyboardPreferences>(relaxed = true) {
            every { keyboardModeFlow } returns this@SettingsViewModelKeyboardModeTest.keyboardModeFlow
            coEvery { setKeyboardMode(any()) } answers {
                this@SettingsViewModelKeyboardModeTest.keyboardModeFlow.value = firstArg()
            }
        }
        val crashReporting = mockk<CrashReportingPreferences>(relaxed = true) {
            every { enabled } returns flowOf(false)
        }
        val appPreferences = mockk<AppPreferences>(relaxed = true) {
            every { all } returns flowOf(mockk<dev.ori.feature.settings.data.AppPreferencesSnapshot>(relaxed = true))
        }
        val checkPremiumUseCase = mockk<CheckPremiumUseCase>(relaxed = true)
        every { checkPremiumUseCase() } returns flowOf(false)
        val storageAccessRepository = mockk<StorageAccessRepository>(relaxed = true) {
            every { grantedTrees } returns flowOf(emptyList<GrantedTree>())
        }

        viewModel = SettingsViewModel(
            application = mockk<Application>(relaxed = true),
            crashReportingPreferences = crashReporting,
            appPreferences = appPreferences,
            keyboardPreferences = keyboardPreferences,
            storageAccessRepository = storageAccessRepository,
            checkPremiumUseCase = checkPremiumUseCase,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_initialEmission_reflectsCustomDefault() = runTest(testDispatcher) {
        viewModel.state.test {
            assertThat(awaitItem().keyboardMode).isEqualTo(KeyboardMode.CUSTOM)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setKeyboardMode_hybrid_persistsAndUpdatesState() = runTest(testDispatcher) {
        viewModel.state.test {
            // Drain the initial CUSTOM emission so we observe the transition.
            awaitItem()

            viewModel.setKeyboardMode(KeyboardMode.HYBRID)

            assertThat(awaitItem().keyboardMode).isEqualTo(KeyboardMode.HYBRID)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setKeyboardMode_systemOnly_persistsAndUpdatesState() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem()

            viewModel.setKeyboardMode(KeyboardMode.SYSTEM_ONLY)

            assertThat(awaitItem().keyboardMode).isEqualTo(KeyboardMode.SYSTEM_ONLY)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
