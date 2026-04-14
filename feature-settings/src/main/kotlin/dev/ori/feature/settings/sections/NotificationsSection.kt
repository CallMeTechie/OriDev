package dev.ori.feature.settings.sections

import androidx.compose.runtime.Composable
import dev.ori.core.ui.components.OriToggle
import dev.ori.core.ui.icons.lucide.Bell
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Watch
import dev.ori.core.ui.icons.lucide.Zap
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.data.AppPreferencesSnapshot

@Composable
internal fun NotificationsSection(
    prefs: AppPreferencesSnapshot,
    onTransferDoneChanged: (Boolean) -> Unit,
    onConnectionChanged: (Boolean) -> Unit,
    onClaudeChanged: (Boolean) -> Unit,
    onWearChanged: (Boolean) -> Unit,
) {
    SettingsCard(sectionLabel = "Benachrichtigungen") {
        SettingsRow(
            icon = LucideIcons.Bell,
            title = "Transfer abgeschlossen",
            subtitle = "Push-Benachrichtigung nach jedem Upload/Download",
            trailing = {
                OriToggle(
                    checked = prefs.notifyTransferDone,
                    onCheckedChange = onTransferDoneChanged,
                )
            },
        )
        SettingsRow(
            icon = LucideIcons.Bell,
            title = "Verbindungsstatus",
            subtitle = "Benachrichtigung bei Connect/Disconnect",
            trailing = {
                OriToggle(
                    checked = prefs.notifyConnection,
                    onCheckedChange = onConnectionChanged,
                )
            },
        )
        SettingsRow(
            icon = LucideIcons.Zap,
            title = "Claude-Code-Antworten",
            subtitle = "Wenn Claude im Terminal eine Code-Antwort sendet",
            trailing = {
                OriToggle(
                    checked = prefs.notifyClaude,
                    onCheckedChange = onClaudeChanged,
                )
            },
        )
        SettingsRow(
            icon = LucideIcons.Watch,
            title = "Wear OS",
            subtitle = "Benachrichtigungen auch an die Uhr senden",
            trailing = {
                OriToggle(
                    checked = prefs.notifyWear,
                    onCheckedChange = onWearChanged,
                )
            },
        )
    }
}
