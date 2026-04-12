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
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.WearAppViewModel
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
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusIndicator(connected = conn.status == "CONNECTED")
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
