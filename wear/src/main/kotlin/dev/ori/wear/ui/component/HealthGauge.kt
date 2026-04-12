package dev.ori.wear.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator

/**
 * Stub gauge for server health (CPU/RAM/Disk). The Phase 8 publisher does not
 * supply these stats yet, so the gauge currently renders a placeholder ring at
 * 0 progress. Wired in for layout / future Proxmox integration.
 */
@Composable
fun HealthGauge(
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        progress = { 0f },
        modifier = modifier.size(GAUGE_SIZE_DP.dp),
    )
}

private const val GAUGE_SIZE_DP = 48
