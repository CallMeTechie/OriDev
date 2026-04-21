package dev.ori.feature.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ori.core.ui.icons.lucide.Code
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Moon
import dev.ori.core.ui.icons.lucide.Star
import dev.ori.core.ui.icons.lucide.Sun
import dev.ori.core.ui.theme.Gray500
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsOptionPickerDialog
import dev.ori.feature.settings.components.SettingsPickerOption
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.data.AppPreferencesSnapshot

/**
 * Appearance section — Phase 11 P1.2.
 *
 * **Light-only:** das `feedback_design_light` Memory legt fest, dass es
 * keinen Dark-Mode gibt. Die Theme-Row zeigt deshalb nur "Hell" als
 * Read-Only-Wert und führt nicht zu einem Picker.
 *
 * **Akzentfarbe** bleibt auf Indigo gepinnt, solange Premium nicht
 * freigeschaltet ist — die Row führt keinen Picker, sie verweist nur
 * auf Premium.
 */
@Composable
internal fun AppearanceSection(
    prefs: AppPreferencesSnapshot,
    onFontSizeChanged: (Int) -> Unit = {},
    onTerminalFontChanged: (String) -> Unit = {},
) {
    var showFontSizePicker by remember { mutableStateOf(false) }
    var showTerminalFontPicker by remember { mutableStateOf(false) }

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
            onClick = { showFontSizePicker = true },
            trailing = { Text(text = "${prefs.fontSize} sp", color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Moon,
            title = "Terminal-Schrift",
            subtitle = prefs.terminalFont,
            onClick = { showTerminalFontPicker = true },
            trailing = { Text(text = prefs.terminalFont, color = Gray500) },
        )
    }

    if (showFontSizePicker) {
        SettingsOptionPickerDialog(
            title = "Schriftgröße",
            options = FONT_SIZE_OPTIONS,
            selected = prefs.fontSize,
            onDismiss = { showFontSizePicker = false },
            onSelect = onFontSizeChanged,
        )
    }

    if (showTerminalFontPicker) {
        SettingsOptionPickerDialog(
            title = "Terminal-Schrift",
            options = TERMINAL_FONT_OPTIONS,
            selected = prefs.terminalFont,
            onDismiss = { showTerminalFontPicker = false },
            onSelect = onTerminalFontChanged,
        )
    }
}

private val FONT_SIZE_OPTIONS = listOf(
    SettingsPickerOption(value = 10, label = "10 sp", description = "Kompakt"),
    SettingsPickerOption(value = 12, label = "12 sp"),
    SettingsPickerOption(value = 14, label = "14 sp", description = "Standard"),
    SettingsPickerOption(value = 16, label = "16 sp"),
    SettingsPickerOption(value = 18, label = "18 sp"),
    SettingsPickerOption(value = 20, label = "20 sp", description = "Komfortlesbar"),
    SettingsPickerOption(value = 24, label = "24 sp", description = "Groß"),
)

private val TERMINAL_FONT_OPTIONS = listOf(
    SettingsPickerOption(
        value = "JetBrains Mono",
        label = "JetBrains Mono",
        description = "Standard — Ligaturen unterstützt",
    ),
    SettingsPickerOption(
        value = "Fira Code",
        label = "Fira Code",
        description = "Ligaturen für Programming-Symbole",
    ),
    SettingsPickerOption(
        value = "Source Code Pro",
        label = "Source Code Pro",
        description = "Adobe, klassisch",
    ),
    SettingsPickerOption(
        value = "Noto Sans Mono",
        label = "Noto Sans Mono",
        description = "Google, breite Unicode-Abdeckung",
    ),
    SettingsPickerOption(
        value = "Roboto Mono",
        label = "Roboto Mono",
        description = "Material-konform",
    ),
)
