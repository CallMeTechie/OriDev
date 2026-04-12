package dev.ori.wear.ui.screens

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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.ROUTE_CONNECTIONS
import dev.ori.wear.ui.ROUTE_PANIC
import dev.ori.wear.ui.ROUTE_QUICK_COMMANDS
import dev.ori.wear.ui.ROUTE_SERVER_HEALTH
import dev.ori.wear.ui.ROUTE_TRANSFERS
import dev.ori.wear.ui.WearAppViewModel
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
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(ROUTE_CONNECTIONS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    label = { Text("Connections") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(ROUTE_TRANSFERS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    label = { Text("Transfers") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(ROUTE_QUICK_COMMANDS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    label = { Text("Commands") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(ROUTE_SERVER_HEALTH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    label = { Text("Health") },
                )
            }
            item {
                Button(
                    onClick = { navController.navigate(ROUTE_PANIC) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    label = { Text("PANIC") },
                )
            }
        }
    }
}
