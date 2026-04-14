package dev.ori.feature.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ori.core.ui.icons.lucide.Code
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Moon
import dev.ori.core.ui.icons.lucide.Star
import dev.ori.core.ui.icons.lucide.Sun
import dev.ori.core.ui.theme.Gray500
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.data.AppPreferencesSnapshot

/**
 * Appearance section — Phase 11 P1.2.
 *
 * **Light-only:** das `feedback_design_light` Memory legt fest, dass es
 * keinen Dark-Mode gibt. Die Theme-Row zeigt deshalb nur "Hell" als
 * Read-Only-Wert und führt nicht zu einem Picker.
 */
@Composable
internal fun AppearanceSection(
    prefs: AppPreferencesSnapshot,
) {
    SettingsCard(sectionLabel = "Darstellung") {
        SettingsRow(
            icon = LucideIcons.Sun,
            title = "Theme",
            subtitle = "Hell (Light-only — kein Dark-Mode in Phase 11)",
            trailing = { Text(text = "Hell", color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Star,
            title = "Akzentfarbe",
            subtitle = "Indigo (Phase 11 — Custom-Farben kommen mit Premium)",
            trailing = { Text(text = prefs.accent.replaceFirstChar { it.uppercase() }, color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Code,
            title = "Schriftgröße",
            subtitle = "Aktuell ${prefs.fontSize} sp",
            trailing = { Text(text = "${prefs.fontSize} sp", color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Moon,
            title = "Terminal-Schrift",
            subtitle = prefs.terminalFont,
            trailing = { Text(text = prefs.terminalFont, color = Gray500) },
        )
    }
}
