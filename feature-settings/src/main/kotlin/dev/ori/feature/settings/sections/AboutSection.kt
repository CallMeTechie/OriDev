package dev.ori.feature.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ori.core.ui.components.OriToggle
import dev.ori.core.ui.icons.lucide.CircleAlert
import dev.ori.core.ui.icons.lucide.Info
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Gray500
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow

/**
 * About section — Phase 11 P1.2.
 *
 * Includes:
 * - Version label (read from PackageManager via SettingsViewModel)
 * - Crash-reporting opt-in toggle (continues to read CrashReportingPreferences)
 * - Datenschutzerklärung / Nutzungsbedingungen / Lizenzen rows (placeholders;
 *   the aboutlibraries plugin integration that auto-generates the licenses
 *   screen is deferred to a follow-up PR — Phase 11 P1.2 just renders the
 *   row without an onClick handler).
 */
@Composable
internal fun AboutSection(
    versionName: String,
    crashReportingEnabled: Boolean,
    onCrashReportingChanged: (Boolean) -> Unit,
) {
    SettingsCard(sectionLabel = "Über die App") {
        SettingsRow(
            icon = LucideIcons.Info,
            title = "Version",
            subtitle = versionName.ifEmpty { "—" },
            trailing = { Text(text = versionName.ifEmpty { "—" }, color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.CircleAlert,
            title = "Anonyme Absturzberichte senden",
            subtitle = "Hilft uns, Bugs zu finden. Keine persönlichen Daten.",
            trailing = {
                OriToggle(
                    checked = crashReportingEnabled,
                    onCheckedChange = onCrashReportingChanged,
                )
            },
        )
        SettingsRow(
            icon = LucideIcons.Info,
            title = "Datenschutzerklärung",
            subtitle = "Wird auf der Webseite geöffnet",
        )
        SettingsRow(
            icon = LucideIcons.Info,
            title = "Nutzungsbedingungen",
            subtitle = "Wird auf der Webseite geöffnet",
        )
        SettingsRow(
            icon = LucideIcons.Info,
            title = "Open-Source-Lizenzen",
            subtitle = "Inter, JetBrains Mono, Roboto Flex, Lucide, sshj, Sora-Editor, …",
        )
    }
}
