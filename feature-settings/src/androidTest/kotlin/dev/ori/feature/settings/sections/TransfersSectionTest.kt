package dev.ori.feature.settings.sections

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.ori.core.ui.theme.OriDevTheme
import dev.ori.feature.settings.data.AppPreferencesSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [TransfersSection]'s P4.6-polish Stepper helper.
 *
 * Asserts:
 * - Stepper renders with the current value + suffix.
 * - +/- buttons fire onChange with `value ± 1`.
 * - Buttons clamp at the configured range bounds (disabled at min/max).
 *
 * The Stepper helper is `private`, but its caller [TransfersSection] is
 * `internal` and exercises both retry steppers, so we drive the tests
 * through a [TransfersSection] composable with stateful prefs.
 */
@RunWith(AndroidJUnit4::class)
class TransfersSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun snapshot(
        maxRetryAttempts: Int = 3,
        retryBackoffSeconds: Int = 10,
    ): AppPreferencesSnapshot = AppPreferencesSnapshot(
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
        maxRetryAttempts = maxRetryAttempts,
        retryBackoffSeconds = retryBackoffSeconds,
        biometricUnlock = false,
        autoLockTimeoutMinutes = 5,
        clipboardClearSeconds = 30,
        notifyTransferDone = true,
        notifyConnection = true,
        notifyClaude = false,
        notifyWear = true,
    )

    @Test
    fun stepper_rendersValueAndSuffix() {
        composeRule.setContent {
            OriDevTheme {
                TransfersSection(
                    prefs = snapshot(maxRetryAttempts = 4, retryBackoffSeconds = 15),
                    onAutoResumeChanged = {},
                    onMaxRetryAttemptsChanged = {},
                    onRetryBackoffSecondsChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("4").assertExists()
        composeRule.onNodeWithText("15s").assertExists()
    }

    @Test
    fun stepper_plusButton_incrementsValue() {
        var captured = -1
        composeRule.setContent {
            var current by remember { mutableStateOf(3) }
            OriDevTheme {
                TransfersSection(
                    prefs = snapshot(maxRetryAttempts = current),
                    onAutoResumeChanged = {},
                    onMaxRetryAttemptsChanged = {
                        captured = it
                        current = it
                    },
                    onRetryBackoffSecondsChanged = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Max. Wiederholungen erhöhen")
            .performClick()

        assertThat(captured).isEqualTo(4)
    }

    @Test
    fun stepper_minusButton_decrementsValue() {
        var captured = -1
        composeRule.setContent {
            var current by remember { mutableStateOf(3) }
            OriDevTheme {
                TransfersSection(
                    prefs = snapshot(maxRetryAttempts = current),
                    onAutoResumeChanged = {},
                    onMaxRetryAttemptsChanged = {
                        captured = it
                        current = it
                    },
                    onRetryBackoffSecondsChanged = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Max. Wiederholungen verringern")
            .performClick()

        assertThat(captured).isEqualTo(2)
    }

    @Test
    fun stepper_atUpperBound_plusButtonDisabled() {
        // MAX_RETRY_ATTEMPTS_RANGE = 0..10 → clamp at 10.
        composeRule.setContent {
            OriDevTheme {
                TransfersSection(
                    prefs = snapshot(maxRetryAttempts = 10),
                    onAutoResumeChanged = {},
                    onMaxRetryAttemptsChanged = {},
                    onRetryBackoffSecondsChanged = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Max. Wiederholungen erhöhen")
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Max. Wiederholungen verringern")
            .assertIsEnabled()
    }

    @Test
    fun stepper_atLowerBound_minusButtonDisabled() {
        // MAX_RETRY_ATTEMPTS_RANGE = 0..10 → clamp at 0.
        composeRule.setContent {
            OriDevTheme {
                TransfersSection(
                    prefs = snapshot(maxRetryAttempts = 0),
                    onAutoResumeChanged = {},
                    onMaxRetryAttemptsChanged = {},
                    onRetryBackoffSecondsChanged = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Max. Wiederholungen verringern")
            .assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Max. Wiederholungen erhöhen")
            .assertIsEnabled()
    }
}
