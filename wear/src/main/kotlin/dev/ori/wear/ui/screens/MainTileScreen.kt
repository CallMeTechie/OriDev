package dev.ori.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.ROUTE_CONNECTIONS
import dev.ori.wear.ui.ROUTE_PANIC
import dev.ori.wear.ui.ROUTE_QUICK_COMMANDS
import dev.ori.wear.ui.ROUTE_SERVER_HEALTH
import dev.ori.wear.ui.ROUTE_TRANSFERS
import dev.ori.wear.ui.WearAppViewModel
import dev.ori.wear.ui.component.OriWearCard
import dev.ori.wear.ui.component.StatusIndicator

@Composable
fun MainTileScreen(
    navController: NavHostController,
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val phoneReachable by viewModel.phoneReachable.collectAsStateWithLifecycle()

    ScreenScaffold {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = "Ori:Dev",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusIndicator(connected = phoneReachable)
                    Text(
                        text = "${connections.size} connected",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Text(
                    text = "${transfers.size} transfers",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Phase 11 P3.2 (T2c) — nav entries are OriWearCard rows rather
            // than Wear M3 FilledTonalButton, matching the `.s1-card` rows in
            // Mockups/watch.html. Accent ring calls out destructive actions.
            item { NavCard(label = "Connections", onClick = { navController.navigate(ROUTE_CONNECTIONS) }) }
            item { NavCard(label = "Transfers", onClick = { navController.navigate(ROUTE_TRANSFERS) }) }
            item { NavCard(label = "Commands", onClick = { navController.navigate(ROUTE_QUICK_COMMANDS) }) }
            item { NavCard(label = "Health", onClick = { navController.navigate(ROUTE_SERVER_HEALTH) }) }
            item {
                OriWearCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(ROUTE_PANIC) },
                    accentBorder = true,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "PANIC",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavCard(label: String, onClick: () -> Unit) {
    OriWearCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
