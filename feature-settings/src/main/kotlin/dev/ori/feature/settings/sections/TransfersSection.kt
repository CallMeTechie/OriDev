package dev.ori.feature.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ori.core.ui.components.OriToggle
import dev.ori.core.ui.icons.lucide.ArrowLeftRight
import dev.ori.core.ui.icons.lucide.Clock
import dev.ori.core.ui.icons.lucide.Download
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.RotateCcw
import dev.ori.core.ui.theme.Gray500
import dev.ori.feature.settings.components.PremiumBadge
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.data.AppPreferencesSnapshot

@Composable
internal fun TransfersSection(
    prefs: AppPreferencesSnapshot,
    onAutoResumeChanged: (Boolean) -> Unit,
) {
    SettingsCard(sectionLabel = "Übertragungen") {
        SettingsRow(
            icon = LucideIcons.ArrowLeftRight,
            title = "Parallele Transfers",
            subtitle = "Free: max. 1 — Premium: max. 8",
            trailing = {
                PremiumBadge()
            },
        )
        SettingsRow(
            icon = LucideIcons.RotateCcw,
            title = "Auto-Resume",
            subtitle = "Unterbrochene Transfers automatisch fortsetzen",
            trailing = {
                OriToggle(
                    checked = prefs.autoResume,
                    onCheckedChange = onAutoResumeChanged,
                )
            },
        )
        SettingsRow(
            icon = LucideIcons.Download,
            title = "Überschreiben-Modus",
            subtitle = "Verhalten bei Datei-Konflikt",
            trailing = { Text(text = prefs.overwriteMode, color = Gray500) },
        )
        // Phase 11 P4.6 — retry policy rows. Display-only for now; in-place
        // editing (slider / stepper) ships with a future settings iteration.
        SettingsRow(
            icon = LucideIcons.RotateCcw,
            title = "Max. Wiederholungen",
            subtitle = "Anzahl automatischer Retries pro fehlgeschlagenem Transfer",
            trailing = { Text(text = prefs.maxRetryAttempts.toString(), color = Gray500) },
        )
        SettingsRow(
            icon = LucideIcons.Clock,
            title = "Retry-Verzögerung",
            subtitle = "Initialer Abstand (Exponential Backoff)",
            trailing = { Text(text = "${prefs.retryBackoffSeconds}s", color = Gray500) },
        )
    }
}
