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

/**
 * Small colored dot used to indicate connectivity / online status throughout the
 * watch UI. Green = connected, gray = unknown, red = disconnected.
 */
@Composable
fun StatusIndicator(
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (connected) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
    Box(
        modifier = modifier
            .size(STATUS_DOT_SIZE_DP.dp)
            .clip(CircleShape)
            .background(color),
    )
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
