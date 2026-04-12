package dev.ori.wear.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator

/**
 * Wraps a [CircularProgressIndicator] for transfer progress display.
 *
 * @param progress 0f..1f
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.size(RING_SIZE_DP.dp),
    )
}

private const val RING_SIZE_DP = 32
