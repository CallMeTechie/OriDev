package dev.ori.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.GreenText
import dev.ori.core.ui.theme.YellowText

/**
 * Bottom status bar used as a fallback for the git diff gutter.
 *
 * See `docs/superpowers/specs/2026-04-11-sora-gutter-spike.md` for why the
 * gutter was rejected in favor of this summary.
 *
 * Phase 11 P2.2-polish — replaced hardcoded hex colours (#047857 / #B45309)
 * with [GreenText] and [YellowText] theme tokens so the status bar matches
 * the mockup palette and reacts to future theme changes.
 */
@Composable
fun GitDiffStatusBar(
    summary: GitDiffSummary?,
    modifier: Modifier = Modifier,
) {
    if (summary == null || (summary.added == 0 && summary.modified == 0)) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (summary.added > 0) {
            Text(
                text = "+${summary.added} added",
                color = GreenText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (summary.modified > 0) {
            Text(
                text = "~${summary.modified} modified",
                color = YellowText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
