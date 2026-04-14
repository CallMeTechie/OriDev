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
fun ConnectionListScreen(
    @Suppress("UnusedParameter") navController: NavHostController,
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()

    ScreenScaffold {
        if (connections.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No connections", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Connect from phone first.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            return@ScreenScaffold
        }
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(connections) { conn ->
                // Phase 11 P3.2 — OriWearCard replaces Wear M3 Card. Active
                // connections get the accent border ring per watch.html
                // .s2-card.active styling.
                val isActive = conn.status == "CONNECTED"
                OriWearCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentBorder = isActive,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusIndicator(connected = isActive)
                        Column {
                            Text(conn.serverName, style = MaterialTheme.typography.titleSmall)
                            Text(conn.host, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
