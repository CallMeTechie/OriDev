package dev.ori.feature.connections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.ChevronRight
import dev.ori.core.ui.icons.lucide.CircleAlert
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.X
import dev.ori.data.session.FailedResume

/**
 * Task 15 — "N Wiederverbindungen fehlgeschlagen" banner rendered at the
 * top of [ConnectionListScreen] whenever the
 * [dev.ori.data.session.FailedResumeRegistry] has entries.
 *
 * Per-entry rows carry a small alert icon, the profile name, a
 * one-line reason, and a right-arrow "Öffnen" button that routes to the
 * existing detail-sheet flow (same callback as a row tap on the list).
 * The header carries a dismiss icon that clears the registry via
 * [ConnectionListViewModel.dismissFailedResume].
 *
 * The banner uses [androidx.compose.material3.Card] with the
 * errorContainer / onErrorContainer pair because this surface announces
 * a failure — unlike the neutral [ReconnectBanner] which is a helpful
 * after-kill recovery affordance. Individual rows stay on the error
 * palette so the colour signal stays consistent.
 *
 * Icons are sourced exclusively from
 * [dev.ori.core.ui.icons.lucide.LucideIcons] per the semgrep hard-gate
 * `oridev-no-material-icons-in-features` — Material Icons must never be
 * imported from a feature module.
 */
@Composable
fun FailedResumeBanner(
    failed: List<FailedResume>,
    onOpenProfile: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FailedResumeHeader(count = failed.size, onDismiss = onDismiss)
            failed.forEach { entry ->
                FailedResumeRow(
                    entry = entry,
                    onOpenProfile = onOpenProfile,
                )
            }
        }
    }
}

/**
 * Header row — count-aware title + top-right dismiss icon button. Split
 * out so `FailedResumeBanner` stays under detekt's LongMethod cap and
 * the visual hierarchy (header → rows) reads at a glance.
 */
@Composable
private fun FailedResumeHeader(
    count: Int,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count Wiederverbindungen fehlgeschlagen",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(BANNER_DISMISS_SIZE),
        ) {
            Icon(
                imageVector = LucideIcons.X,
                contentDescription = "Fehlermeldungen schließen",
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * Single `FailedResume` row — icon + name + reason + "Öffnen" button.
 * Extracted from [FailedResumeBanner] so the Column body stays flat
 * and the row layout is independently previewable.
 */
@Composable
private fun FailedResumeRow(
    entry: FailedResume,
    onOpenProfile: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LucideIcons.CircleAlert,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(ROW_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(ROW_ICON_GAP))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.profileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 1,
            )
            Text(
                text = entry.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 1,
            )
        }
        TextButton(onClick = { onOpenProfile(entry.profileId) }) {
            Text(
                text = "Öffnen",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Icon(
                imageVector = LucideIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(ROW_ICON_SIZE),
            )
        }
    }
}

private val BANNER_DISMISS_SIZE = 32.dp
private val ROW_ICON_SIZE = 18.dp
private val ROW_ICON_GAP = 12.dp
