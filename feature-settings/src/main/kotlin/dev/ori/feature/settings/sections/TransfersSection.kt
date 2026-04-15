package dev.ori.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.components.OriIconButton
import dev.ori.core.ui.components.OriToggle
import dev.ori.core.ui.icons.lucide.ArrowLeftRight
import dev.ori.core.ui.icons.lucide.Clock
import dev.ori.core.ui.icons.lucide.Download
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Minus
import dev.ori.core.ui.icons.lucide.Plus
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
    onMaxRetryAttemptsChanged: (Int) -> Unit,
    onRetryBackoffSecondsChanged: (Int) -> Unit,
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
        // Phase 11 carry-over F — editable retry policy steppers.
        SettingsRow(
            icon = LucideIcons.RotateCcw,
            title = "Max. Wiederholungen",
            subtitle = "Anzahl automatischer Retries pro fehlgeschlagenem Transfer",
            trailing = {
                Stepper(
                    value = prefs.maxRetryAttempts,
                    range = MAX_RETRY_ATTEMPTS_RANGE,
                    label = "Max. Wiederholungen",
                    onChange = onMaxRetryAttemptsChanged,
                )
            },
        )
        SettingsRow(
            icon = LucideIcons.Clock,
            title = "Retry-Verzögerung",
            subtitle = "Initialer Abstand (Exponential Backoff)",
            trailing = {
                Stepper(
                    value = prefs.retryBackoffSeconds,
                    range = RETRY_BACKOFF_RANGE,
                    label = "Retry-Verzögerung",
                    suffix = "s",
                    onChange = onRetryBackoffSecondsChanged,
                )
            },
        )
    }
}

@Composable
private fun Stepper(
    value: Int,
    range: IntRange,
    label: String,
    onChange: (Int) -> Unit,
    suffix: String = "",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        OriIconButton(
            icon = LucideIcons.Minus,
            // Phase 11 P4.6-polish: per-stepper contentDescription so the two
            // retry steppers in this section are individually addressable for
            // the TransfersSectionTest Compose UI assertions and screen readers.
            contentDescription = "$label verringern",
            enabled = value > range.first,
            onClick = { onChange(value - 1) },
        )
        Text(
            text = "$value$suffix",
            color = Gray500,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OriIconButton(
            icon = LucideIcons.Plus,
            contentDescription = "$label erhöhen",
            enabled = value < range.last,
            onClick = { onChange(value + 1) },
        )
    }
}

private val MAX_RETRY_ATTEMPTS_RANGE = 0..10
private val RETRY_BACKOFF_RANGE = 1..120
