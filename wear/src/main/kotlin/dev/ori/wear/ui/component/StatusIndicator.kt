package dev.ori.wear.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ori.wear.ui.theme.OriDevWearColors

/**
 * Small coloured dot used to indicate connectivity / online status throughout
 * the Wear UI.
 *
 * Phase 11 P3.1 — switched from hardcoded Material palette (#4CAF50, #9E9E9E)
 * to the Ori:Dev wear palette in [OriDevWearColors] so the dot matches the
 * phone's Success / OnSurfaceVariant tokens and the mockup's bright green.
 *
 * - Connected: [OriDevWearColors.Success] (#10B981)
 * - Disconnected: [OriDevWearColors.OnSurfaceVariant] (#888)
 */
@Composable
fun StatusIndicator(
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (connected) {
        OriDevWearColors.Success
    } else {
        OriDevWearColors.OnSurfaceVariant
    }
    StatusIndicator(color = color, modifier = modifier)
}

@Composable
fun StatusIndicator(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(STATUS_DOT_SIZE_DP.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private const val STATUS_DOT_SIZE_DP = 8
