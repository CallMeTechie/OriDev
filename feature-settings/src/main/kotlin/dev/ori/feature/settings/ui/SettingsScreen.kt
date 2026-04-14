package dev.ori.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.components.OriSectionLabel
import dev.ori.core.ui.components.OriToggle
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.components.OriTopBarDefaults

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onCrashReportingChanged = viewModel::setCrashReportingEnabled,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    onCrashReportingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Phase 11 PR 4a — replaces Material 3 TopAppBar (64 dp default,
            // way too tall for the mockup) with the 56 dp OriTopBar.
            OriTopBar(
                title = "Einstellungen",
                height = OriTopBarDefaults.Height,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_content")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrivacySection(
                crashReportingEnabled = state.crashReportingEnabled,
                onCrashReportingChanged = onCrashReportingChanged,
            )
            HorizontalDivider()
            InfoSection(versionName = state.versionName)
        }
    }
}

@Composable
private fun PrivacySection(
    crashReportingEnabled: Boolean,
    onCrashReportingChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OriSectionLabel(
            text = "Datenschutz",
            modifier = Modifier.semantics {
                contentDescription = "Abschnitt Datenschutz"
                heading()
            },
        )
        OriCard(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "Anonyme Absturzberichte senden, ${if (crashReportingEnabled) "aktiviert" else "deaktiviert"}"
                    role = Role.Switch
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Anonyme Absturzberichte senden",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Hilft uns, Bugs zu finden. Keine persönlichen Daten. " +
                            "Wirkt nach dem nächsten Absturz.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OriToggle(
                    checked = crashReportingEnabled,
                    onCheckedChange = onCrashReportingChanged,
                    modifier = Modifier.semantics {
                        contentDescription = "Schalter Anonyme Absturzberichte"
                    },
                )
            }
        }
    }
}

@Composable
private fun InfoSection(versionName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OriSectionLabel(
            text = "Info",
            modifier = Modifier.semantics {
                contentDescription = "Abschnitt Info"
                heading()
            },
        )
        OriCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics {
                        contentDescription = "App-Version $versionName"
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = versionName.ifEmpty { "—" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    Surface {
        SettingsContent(
            state = SettingsState(
                crashReportingEnabled = false,
                versionName = "0.1.0",
            ),
            onCrashReportingChanged = {},
            modifier = Modifier.padding(PaddingValues(0.dp)),
        )
    }
}
