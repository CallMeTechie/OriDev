package dev.ori.feature.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ori.core.ui.icons.lucide.Clipboard
import dev.ori.core.ui.icons.lucide.Clock
import dev.ori.core.ui.icons.lucide.FingerprintPattern
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Gray500
import dev.ori.feature.settings.components.PremiumBadge
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsOptionPickerDialog
import dev.ori.feature.settings.components.SettingsPickerOption
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.data.AppPreferencesSnapshot

@Composable
internal fun SecuritySection(
    prefs: AppPreferencesSnapshot,
    onBiometricUnlockChanged: (Boolean) -> Unit,
    onAutoLockTimeoutChanged: (Int) -> Unit = {},
    onClipboardClearSecondsChanged: (Int) -> Unit = {},
) {
    var showAutoLockPicker by remember { mutableStateOf(false) }
    var showClipboardPicker by remember { mutableStateOf(false) }

    SettingsCard(sectionLabel = "Sicherheit") {
        SettingsRow(
            icon = LucideIcons.FingerprintPattern,
            title = "Biometrie-Entsperren",
            subtitle = "Premium — App mit Fingerabdruck/Face entsperren",
            trailing = {
                PremiumBadge()
            },
        )
        SettingsRow(
            icon = LucideIcons.Clock,
            title = "Auto-Lock",
            subtitle = autoLockSubtitle(prefs.autoLockTimeoutMinutes),
            onClick = { showAutoLockPicker = true },
            trailing = { Text(text = autoLockLabel(prefs.autoLockTimeoutMinutes), color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Clipboard,
            title = "Zwischenablage automatisch leeren",
            subtitle = clipboardClearSubtitle(prefs.clipboardClearSeconds),
            onClick = { showClipboardPicker = true },
            trailing = { Text(text = clipboardClearLabel(prefs.clipboardClearSeconds), color = Gray500) },
        )
    }
    @Suppress("UnusedExpression")
    onBiometricUnlockChanged

    if (showAutoLockPicker) {
        SettingsOptionPickerDialog(
            title = "Auto-Lock",
            options = AUTO_LOCK_OPTIONS,
            selected = prefs.autoLockTimeoutMinutes,
            onDismiss = { showAutoLockPicker = false },
            onSelect = onAutoLockTimeoutChanged,
        )
    }

    if (showClipboardPicker) {
        SettingsOptionPickerDialog(
            title = "Zwischenablage automatisch leeren",
            options = CLIPBOARD_CLEAR_OPTIONS,
            selected = prefs.clipboardClearSeconds,
            onDismiss = { showClipboardPicker = false },
            onSelect = onClipboardClearSecondsChanged,
        )
    }
}

private val AUTO_LOCK_OPTIONS = listOf(
    SettingsPickerOption(value = 0, label = "Aus", description = "Auto-Lock deaktivieren"),
    SettingsPickerOption(value = 1, label = "1 Minute"),
    SettingsPickerOption(value = 5, label = "5 Minuten", description = "Standard"),
    SettingsPickerOption(value = 15, label = "15 Minuten"),
    SettingsPickerOption(value = 30, label = "30 Minuten"),
    SettingsPickerOption(value = 60, label = "1 Stunde"),
)

private val CLIPBOARD_CLEAR_OPTIONS = listOf(
    SettingsPickerOption(value = 0, label = "Aus", description = "Kein automatisches Leeren"),
    SettingsPickerOption(value = 15, label = "15 Sekunden"),
    SettingsPickerOption(value = 30, label = "30 Sekunden", description = "Standard"),
    SettingsPickerOption(value = 60, label = "1 Minute"),
    SettingsPickerOption(value = 120, label = "2 Minuten"),
    SettingsPickerOption(value = 300, label = "5 Minuten"),
)

private fun autoLockLabel(minutes: Int): String = when (minutes) {
    0 -> "Aus"
    1 -> "1 min"
    60 -> "1 h"
    else -> "$minutes min"
}

private fun autoLockSubtitle(minutes: Int): String = when (minutes) {
    0 -> "Auto-Lock deaktiviert"
    else -> "Nach $minutes Minuten Inaktivität"
}

private fun clipboardClearLabel(seconds: Int): String = when (seconds) {
    0 -> "Aus"
    60 -> "1 min"
    120 -> "2 min"
    300 -> "5 min"
    else -> "$seconds s"
}

private fun clipboardClearSubtitle(seconds: Int): String = when (seconds) {
    0 -> "Zwischenablage bleibt bis manuellem Leeren"
    else -> "Nach $seconds Sekunden"
}
