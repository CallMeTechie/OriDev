package dev.ori.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.WearAppViewModel
import dev.ori.wear.ui.component.OriWearCard
import dev.ori.wear.ui.component.StatusIndicator

@Composable
fun ServerHealthScreen(
    @Suppress("UnusedParameter") navController: NavHostController,
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val active = connections.filter { it.status == "CONNECTED" }

    ScreenScaffold {
        if (active.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No active servers", style = MaterialTheme.typography.titleSmall)
            }
            return@ScreenScaffold
        }
        val now = System.currentTimeMillis()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(active) { conn ->
                // Phase 11 P3.2 — OriWearCard replaces Wear M3 Card. Active
                // servers always get the accent ring (the list is already
                // filtered by `status == "CONNECTED"`).
                OriWearCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentBorder = true,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusIndicator(connected = true)
                        Column {
                            Text(conn.serverName, style = MaterialTheme.typography.titleSmall)
                            Text(conn.host, style = MaterialTheme.typography.bodySmall)
                            val sinceMs = conn.connectedSinceMillis
                            if (sinceMs != null) {
                                Text(
                                    text = formatDuration(now - sinceMs),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "just now"
    val totalSeconds = durationMs / MS_PER_SECOND
    val totalMinutes = totalSeconds / SECONDS_PER_MINUTE
    if (totalMinutes < 1L) return "${totalSeconds}s ago"
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ago"
}
