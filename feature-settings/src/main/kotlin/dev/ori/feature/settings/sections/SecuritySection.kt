package dev.ori.feature.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ori.core.ui.icons.lucide.Clipboard
import dev.ori.core.ui.icons.lucide.Clock
import dev.ori.core.ui.icons.lucide.FingerprintPattern
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Gray500
import dev.ori.feature.settings.components.PremiumBadge
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.data.AppPreferencesSnapshot

@Composable
internal fun SecuritySection(
    prefs: AppPreferencesSnapshot,
    onBiometricUnlockChanged: (Boolean) -> Unit,
) {
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
            subtitle = "Nach ${prefs.autoLockTimeoutMinutes} Minuten Inaktivität",
            trailing = { Text(text = "${prefs.autoLockTimeoutMinutes} min", color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Clipboard,
            title = "Zwischenablage automatisch leeren",
            subtitle = "Nach ${prefs.clipboardClearSeconds} Sekunden",
            trailing = { Text(text = "${prefs.clipboardClearSeconds} s", color = Gray500) },
        )
    }
    @Suppress("UnusedExpression")
    onBiometricUnlockChanged
}
