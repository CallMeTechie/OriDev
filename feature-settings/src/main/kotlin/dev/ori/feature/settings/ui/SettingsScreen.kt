package dev.ori.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.components.OriTopBarDefaults
import dev.ori.feature.settings.sections.AboutSection
import dev.ori.feature.settings.sections.AccountPremiumSection
import dev.ori.feature.settings.sections.AppearanceSection
import dev.ori.feature.settings.sections.NotificationsSection
import dev.ori.feature.settings.sections.SecuritySection
import dev.ori.feature.settings.sections.TerminalSection
import dev.ori.feature.settings.sections.TransfersSection

/**
 * Settings entry composable — Phase 11 P1.2 expansion to 7 sections.
 *
 * **Sections** (top → bottom, per `settings.html` mockup order):
 * 1. Konto & Premium
 * 2. Darstellung (Light-only — kein Dark-Mode per `feedback_design_light`)
 * 3. Terminal
 * 4. Übertragungen
 * 5. Sicherheit
 * 6. Benachrichtigungen
 * 7. Über die App (inkl. Crash-Reporting + Lizenzen-Placeholder)
 *
 * **Layout:** `LazyColumn`-style scrolling `Column` with 16 dp content padding
 * and 24 dp gap between sections. Content is centered with `widthIn(max = 720.dp)`
 * so on the unfolded Pixel Fold inner display the settings don't stretch
 * to fill the whole 2208 dp width — they sit in a comfortable column.
 */
@Composable
public fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onCrashReportingChanged = viewModel::setCrashReportingEnabled,
        onHardwareKeyboardChanged = viewModel::setHardwareKeyboard,
        onKeyboardToolbarChanged = viewModel::setKeyboardToolbar,
        onAutoResumeChanged = viewModel::setAutoResume,
        onBiometricUnlockChanged = viewModel::setBiometricUnlock,
        onTransferDoneNotificationChanged = viewModel::setNotifyTransferDone,
        onConnectionNotificationChanged = viewModel::setNotifyConnection,
        onClaudeNotificationChanged = viewModel::setNotifyClaude,
        onWearNotificationChanged = viewModel::setNotifyWear,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    onCrashReportingChanged: (Boolean) -> Unit,
    onHardwareKeyboardChanged: (Boolean) -> Unit = {},
    onKeyboardToolbarChanged: (Boolean) -> Unit = {},
    onAutoResumeChanged: (Boolean) -> Unit = {},
    onBiometricUnlockChanged: (Boolean) -> Unit = {},
    onTransferDoneNotificationChanged: (Boolean) -> Unit = {},
    onConnectionNotificationChanged: (Boolean) -> Unit = {},
    onClaudeNotificationChanged: (Boolean) -> Unit = {},
    onWearNotificationChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            OriTopBar(
                title = "Einstellungen",
                height = OriTopBarDefaults.Height,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .testTag("settings_content")
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AccountPremiumSection()
                AppearanceSection(prefs = state.preferences)
                TerminalSection(
                    prefs = state.preferences,
                    onHardwareKeyboardChanged = onHardwareKeyboardChanged,
                    onKeyboardToolbarChanged = onKeyboardToolbarChanged,
                )
                TransfersSection(
                    prefs = state.preferences,
                    onAutoResumeChanged = onAutoResumeChanged,
                )
                SecuritySection(
                    prefs = state.preferences,
                    onBiometricUnlockChanged = onBiometricUnlockChanged,
                )
                NotificationsSection(
                    prefs = state.preferences,
                    onTransferDoneChanged = onTransferDoneNotificationChanged,
                    onConnectionChanged = onConnectionNotificationChanged,
                    onClaudeChanged = onClaudeNotificationChanged,
                    onWearChanged = onWearNotificationChanged,
                )
                AboutSection(
                    versionName = state.versionName,
                    crashReportingEnabled = state.crashReportingEnabled,
                    onCrashReportingChanged = onCrashReportingChanged,
                )
            }
        }
    }
}

private val MAX_CONTENT_WIDTH = 720.dp

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    Surface {
        SettingsContent(
            state = SettingsState(versionName = "0.7.0"),
            onCrashReportingChanged = {},
            modifier = Modifier.padding(PaddingValues(0.dp)),
        )
    }
}
