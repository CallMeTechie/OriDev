package dev.ori.wear.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.ROUTE_COMMAND_OUTPUT
import dev.ori.wear.ui.WearAppViewModel

@Composable
fun QuickCommandsScreen(
    navController: NavHostController,
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val snippets by viewModel.snippets.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val activeConnections = connections.filter { it.status == "CONNECTED" }

    ScreenScaffold {
        if (snippets.isEmpty() || activeConnections.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (activeConnections.isEmpty()) {
                        "No active connection"
                    } else {
                        "No quick commands"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (activeConnections.isEmpty()) {
                        "Connect from phone first."
                    } else {
                        "Mark snippets as quick commands."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            return@ScreenScaffold
        }
        val target = activeConnections.first()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(snippets) { snippet ->
                FilledTonalButton(
                    onClick = {
                        viewModel.sendCommand(target.profileId, snippet.command)
                        navController.navigate(ROUTE_COMMAND_OUTPUT)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    label = { Text(snippet.name) },
                )
            }
        }
    }
}
