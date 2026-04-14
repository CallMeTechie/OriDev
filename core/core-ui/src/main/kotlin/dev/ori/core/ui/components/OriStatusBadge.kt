@file:Suppress("MatchingDeclarationName")

package dev.ori.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.GreenBg
import dev.ori.core.ui.theme.GreenText
import dev.ori.core.ui.theme.IndigoBadgeBg
import dev.ori.core.ui.theme.IndigoBadgeText
import dev.ori.core.ui.theme.OriExtraShapes
import dev.ori.core.ui.theme.OriTypography
import dev.ori.core.ui.theme.RedBg
import dev.ori.core.ui.theme.RedText
import dev.ori.core.ui.theme.SkyBg
import dev.ori.core.ui.theme.SkyText
import dev.ori.core.ui.theme.YellowBg
import dev.ori.core.ui.theme.YellowText

/**
 * Badge intent — drives the colour pair. Maps directly to the
 * `transfer-queue.html` / `proxmox.html` / `connection-manager.html` mockup
 * badge palette.
 */
@Immutable
public enum class OriStatusBadgeIntent {
    Upload, Download, Queued, Running, Completed, Failed, Stopped, Paused, Sftp, Ssh, Ftp, Proxmox,
}

/**
 * Pill-shaped status badge — 11 sp / 600 weight / 3 × 10 dp padding. Colour
 * pair is selected from [OriStatusBadgeIntent]; every pair is a literal token
 * from the mockup palette in `core-ui/theme/Color.kt`.
 *
 * Replaces v0's ad-hoc per-screen badge implementations (each used a slightly
 * different rounding / padding / colour) with one canonical primitive.
 */
@Composable
public fun OriStatusBadge(
    label: String,
    intent: OriStatusBadgeIntent,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) = colorsFor(intent)
    Box(
        modifier = modifier
            .clip(OriExtraShapes.badge)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OriTypography.overline,
            color = textColor,
        )
    }
}

private fun colorsFor(intent: OriStatusBadgeIntent): Pair<Color, Color> = when (intent) {
    OriStatusBadgeIntent.Upload -> IndigoBadgeBg to IndigoBadgeText
    OriStatusBadgeIntent.Download -> SkyBg to SkyText
    OriStatusBadgeIntent.Queued -> YellowBg to YellowText
    OriStatusBadgeIntent.Running -> GreenBg to GreenText
    OriStatusBadgeIntent.Completed -> GreenBg to GreenText
    OriStatusBadgeIntent.Failed -> RedBg to RedText
    OriStatusBadgeIntent.Stopped -> RedBg to RedText
    OriStatusBadgeIntent.Paused -> YellowBg to YellowText
    OriStatusBadgeIntent.Sftp -> IndigoBadgeBg to IndigoBadgeText
    OriStatusBadgeIntent.Ssh -> YellowBg to YellowText
    OriStatusBadgeIntent.Ftp -> SkyBg to SkyText
    OriStatusBadgeIntent.Proxmox -> RedBg to RedText
}
