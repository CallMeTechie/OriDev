package dev.ori.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.components.OriPillButton
import dev.ori.core.ui.components.OriPillButtonVariant
import dev.ori.core.ui.components.OriToggle
import dev.ori.core.ui.icons.lucide.Bell
import dev.ori.core.ui.icons.lucide.Keyboard
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Smartphone
import dev.ori.core.ui.icons.lucide.Terminal
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.Gray700
import dev.ori.core.ui.theme.Gray900
import dev.ori.domain.model.KeyboardMode
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsOptionPickerDialog
import dev.ori.feature.settings.components.SettingsPickerOption
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.data.AppPreferencesSnapshot

@Composable
internal fun TerminalSection(
    prefs: AppPreferencesSnapshot,
    keyboardMode: KeyboardMode,
    onHardwareKeyboardChanged: (Boolean) -> Unit,
    onKeyboardToolbarChanged: (Boolean) -> Unit,
    onKeyboardModeChanged: (KeyboardMode) -> Unit,
    onDefaultShellChanged: (String) -> Unit = {},
    onScrollbackChanged: (Int) -> Unit = {},
    onBellModeChanged: (String) -> Unit = {},
) {
    // Phase 14 Task 14.6 — the picker and the IME-warning dialog are
    // two separate overlays that the section owns. The picker is shown
    // when the user taps the row; the warning fires afterwards if the
    // chosen mode routes keystrokes through the system IME (HYBRID /
    // SYSTEM_ONLY). Pending mode survives the intermediate state so a
    // Cancel on the warning doesn't silently drop the selection into
    // persistence.
    var showPicker by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<KeyboardMode?>(null) }
    var showShellPicker by remember { mutableStateOf(false) }
    var showScrollbackPicker by remember { mutableStateOf(false) }
    var showBellPicker by remember { mutableStateOf(false) }

    SettingsCard(sectionLabel = "Terminal") {
        SettingsRow(
            icon = LucideIcons.Terminal,
            title = "Standard-Shell",
            subtitle = prefs.defaultShell,
            onClick = { showShellPicker = true },
            trailing = { Text(text = prefs.defaultShell, color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Terminal,
            title = "Scrollback-Zeilen",
            subtitle = "${prefs.scrollback} Zeilen",
            onClick = { showScrollbackPicker = true },
            trailing = { Text(text = "${prefs.scrollback}", color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Bell,
            title = "Bell-Modus",
            subtitle = bellModeDescription(prefs.bellMode),
            onClick = { showBellPicker = true },
            trailing = { Text(text = bellModeLabel(prefs.bellMode), color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Keyboard,
            title = "Tastatur-Stil",
            subtitle = keyboardMode.subtitle(),
            onClick = { showPicker = true },
            trailing = { Text(text = keyboardMode.shortLabel(), color = Gray500) },
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

    if (showPicker) {
        KeyboardModePickerDialog(
            selected = keyboardMode,
            onDismiss = { showPicker = false },
            onSelect = { picked ->
                showPicker = false
                if (picked == KeyboardMode.CUSTOM) {
                    // Safe default — skip the warning and persist immediately.
                    onKeyboardModeChanged(picked)
                } else {
                    // Defer persistence until the user confirms the
                    // dictionary-learning warning.
                    pendingMode = picked
                }
            },
        )
    }

    pendingMode?.let { mode ->
        SystemImeWarningDialog(
            onConfirm = {
                onKeyboardModeChanged(mode)
                pendingMode = null
            },
            onDismiss = {
                // Decline → no DataStore write, leave the persisted mode
                // untouched. The user is shown the warning *before* anything
                // is committed, so cancelling here MUST be a no-op rather
                // than a destructive downgrade. (A user on HYBRID who taps
                // SYSTEM_ONLY then back-presses the warning would otherwise
                // be silently demoted to CUSTOM.)
                pendingMode = null
            },
        )
    }

    if (showShellPicker) {
        SettingsOptionPickerDialog(
            title = "Standard-Shell",
            options = SHELL_OPTIONS,
            selected = prefs.defaultShell,
            onDismiss = { showShellPicker = false },
            onSelect = onDefaultShellChanged,
        )
    }

    if (showScrollbackPicker) {
        SettingsOptionPickerDialog(
            title = "Scrollback-Zeilen",
            options = SCROLLBACK_OPTIONS,
            selected = prefs.scrollback,
            onDismiss = { showScrollbackPicker = false },
            onSelect = onScrollbackChanged,
        )
    }

    if (showBellPicker) {
        SettingsOptionPickerDialog(
            title = "Bell-Modus",
            options = BELL_MODE_OPTIONS,
            selected = prefs.bellMode,
            onDismiss = { showBellPicker = false },
            onSelect = onBellModeChanged,
        )
    }
}

private val SHELL_OPTIONS = listOf(
    SettingsPickerOption(
        value = "/bin/bash",
        label = "/bin/bash",
        description = "GNU Bash — Standard auf Linux",
    ),
    SettingsPickerOption(
        value = "/bin/zsh",
        label = "/bin/zsh",
        description = "Z Shell mit Oh-My-Zsh-Kompatibilität",
    ),
    SettingsPickerOption(
        value = "/bin/sh",
        label = "/bin/sh",
        description = "POSIX Shell (minimaler, breit verfügbar)",
    ),
    SettingsPickerOption(
        value = "/bin/dash",
        label = "/bin/dash",
        description = "Schlanke Debian-Shell",
    ),
    SettingsPickerOption(
        value = "/bin/fish",
        label = "/bin/fish",
        description = "Friendly Interactive Shell",
    ),
)

private val SCROLLBACK_OPTIONS = listOf(
    SettingsPickerOption(value = 1_000, label = "1.000", description = "Minimal — für Geräte mit wenig RAM"),
    SettingsPickerOption(value = 5_000, label = "5.000"),
    SettingsPickerOption(value = 10_000, label = "10.000", description = "Standard"),
    SettingsPickerOption(value = 25_000, label = "25.000"),
    SettingsPickerOption(value = 50_000, label = "50.000", description = "Sehr lang — höherer Speicherverbrauch"),
)

private val BELL_MODE_OPTIONS = listOf(
    SettingsPickerOption(value = "visible", label = "Visuell", description = "Kurzes Aufleuchten des Terminals"),
    SettingsPickerOption(value = "audible", label = "Akustisch", description = "System-Beep-Signal abspielen"),
    SettingsPickerOption(value = "none", label = "Aus", description = "Terminal-Bell komplett ignorieren"),
)

private fun bellModeLabel(value: String): String = when (value) {
    "audible" -> "Akustisch"
    "none" -> "Aus"
    else -> "Visuell"
}

private fun bellModeDescription(value: String): String = when (value) {
    "audible" -> "Akustisches Beep-Signal"
    "none" -> "Terminal-Bell ignorieren"
    else -> "Visuelle Benachrichtigung bei Terminal-Bell"
}

@Composable
private fun KeyboardModePickerDialog(
    selected: KeyboardMode,
    onDismiss: () -> Unit,
    onSelect: (KeyboardMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = "Tastatur-Stil",
                style = MaterialTheme.typography.titleLarge,
                color = Gray900,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KeyboardMode.entries.forEach { mode ->
                    KeyboardModeRow(
                        mode = mode,
                        selected = mode == selected,
                        onSelect = { onSelect(mode) },
                    )
                }
            }
        },
        confirmButton = {
            OriPillButton(
                label = "Abbrechen",
                onClick = onDismiss,
                variant = OriPillButtonVariant.Default,
            )
        },
    )
}

@Composable
private fun KeyboardModeRow(
    mode: KeyboardMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = mode.pickerTitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = Gray900,
            )
            Text(
                text = mode.pickerDescription(),
                style = MaterialTheme.typography.labelMedium,
                color = Gray500,
            )
        }
    }
}

@Composable
private fun SystemImeWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = "System-Tastatur verwenden?",
                style = MaterialTheme.typography.titleLarge,
                color = Gray900,
            )
        },
        text = {
            Text(
                text = "Deine System-Tastatur (Gboard, SwiftKey, \u2026) kann eingegebenen " +
                    "Text lernen und in der Cloud synchronisieren. Vermeide Passw\u00F6rter mit " +
                    "der System-Tastatur oder nutze einen dedizierten \u201EPrivater Modus\u201C " +
                    "in deinem IME. Die integrierte Tastatur teilt niemals deine Eingaben.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray700,
            )
        },
        confirmButton = {
            OriPillButton(
                label = "Fortfahren",
                onClick = onConfirm,
                variant = OriPillButtonVariant.Primary,
            )
        },
        dismissButton = {
            OriPillButton(
                label = "Abbrechen",
                onClick = onDismiss,
                variant = OriPillButtonVariant.Default,
            )
        },
    )
}

private fun KeyboardMode.shortLabel(): String = when (this) {
    KeyboardMode.CUSTOM -> "Integriert"
    KeyboardMode.HYBRID -> "System + Shortcuts"
    KeyboardMode.SYSTEM_ONLY -> "Nur System"
}

private fun KeyboardMode.subtitle(): String = when (this) {
    KeyboardMode.CUSTOM -> "Integrierte Tastatur – empfohlen für Passwörter"
    KeyboardMode.HYBRID -> "System-Tastatur mit Shortcut-Leiste"
    KeyboardMode.SYSTEM_ONLY -> "Nur System-Tastatur"
}

private fun KeyboardMode.pickerTitle(): String = when (this) {
    KeyboardMode.CUSTOM -> "Integrierte Tastatur"
    KeyboardMode.HYBRID -> "System-Tastatur mit Shortcut-Leiste"
    KeyboardMode.SYSTEM_ONLY -> "Nur System-Tastatur"
}

private fun KeyboardMode.pickerDescription(): String = when (this) {
    KeyboardMode.CUSTOM -> "Empfohlen für Passwörter – keine IME-Wörterbuch-Aufzeichnung"
    KeyboardMode.HYBRID -> "Gboard/SwiftKey plus Esc/Tab/Ctrl/Alt/Pfeile oben"
    KeyboardMode.SYSTEM_ONLY -> "Reine System-Tastatur ohne Shortcut-Leiste"
}
