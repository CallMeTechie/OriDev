package dev.ori.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.OriExtraShapes

/**
 * Linear progress bar primitive. Track is [Gray200], fill is [Indigo500].
 * Shape is the pill from [OriExtraShapes.progress] so even short progress
 * values look round on both ends.
 *
 * Heights:
 * - 5 dp — transfer queue item progress (TransferItemCard)
 * - 6 dp — node/VM stat bars in proxmox (NodeCard, VmCard)
 * - 4 dp — proxmox VM mini-bars (used by [OriMiniBar])
 *
 * Replaces `androidx.compose.material3.LinearProgressIndicator` (which has a
 * fixed 4 dp track and an animated fill that doesn't match the mockup's flat
 * static rendering).
 *
 * @param progress 0f..1f. Out-of-range values are clamped.
 */
@Composable
public fun OriProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(OriExtraShapes.progress)
            .background(Gray200),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = clamped)
                .fillMaxHeight()
                .background(Indigo500),
        )
    }
}
