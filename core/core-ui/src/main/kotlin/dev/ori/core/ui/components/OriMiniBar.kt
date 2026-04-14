package dev.ori.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Fixed-size 60 × 4 dp progress bar used by Proxmox `VmCard` to show CPU and
 * RAM mini-indicators per VM. Wraps [OriProgressBar] with the exact dimensions
 * called out in `proxmox.html`.
 *
 * Cycle 1 review noted the mockup specifies these as a distinct visual element
 * (separate from the larger 6 dp NodeCard stat bars), so this is a separate
 * primitive rather than a parameter on [OriProgressBar].
 */
@Composable
public fun OriMiniBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    OriProgressBar(
        progress = progress,
        modifier = modifier.size(width = 60.dp, height = 4.dp),
        height = 4.dp,
    )
}
