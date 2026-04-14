package dev.ori.feature.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ori.core.ui.components.OriToggle
import dev.ori.core.ui.icons.lucide.Bell
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Smartphone
import dev.ori.core.ui.icons.lucide.Terminal
import dev.ori.core.ui.theme.Gray500
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.data.AppPreferencesSnapshot

@Composable
internal fun TerminalSection(
    prefs: AppPreferencesSnapshot,
    onHardwareKeyboardChanged: (Boolean) -> Unit,
    onKeyboardToolbarChanged: (Boolean) -> Unit,
) {
    SettingsCard(sectionLabel = "Terminal") {
        SettingsRow(
            icon = LucideIcons.Terminal,
            title = "Standard-Shell",
            subtitle = prefs.defaultShell,
            trailing = { Text(text = prefs.defaultShell, color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Terminal,
            title = "Scrollback-Zeilen",
            subtitle = "${prefs.scrollback} Zeilen",
            trailing = { Text(text = "${prefs.scrollback}", color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Bell,
            title = "Bell-Modus",
            subtitle = "Visuelle Benachrichtigung bei Terminal-Bell",
            trailing = { Text(text = prefs.bellMode, color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Smartphone,
            title = "Hardware-Tastatur",
            subtitle = "Externe Tastaturen aktivieren spezielle Hotkeys",
            trailing = {
                OriToggle(
                    checked = prefs.hardwareKeyboard,
                    onCheckedChange = onHardwareKeyboardChanged,
                )
            },
        )
        SettingsRow(
            icon = LucideIcons.Smartphone,
            title = "Tastatur-Toolbar",
            subtitle = "Tab/Esc/Pfeile als Schnellzugriff über der Tastatur",
            trailing = {
                OriToggle(
                    checked = prefs.keyboardToolbar,
                    onCheckedChange = onKeyboardToolbarChanged,
                )
            },
        )
    }
}
