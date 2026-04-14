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
import dev.ori.wear.ui.component.ProgressRing

@Composable
fun TransferMonitorScreen(
    @Suppress("UnusedParameter") navController: NavHostController,
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()

    ScreenScaffold {
        if (transfers.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No transfers", style = MaterialTheme.typography.titleSmall)
            }
            return@ScreenScaffold
        }
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(transfers) { t ->
                val name = t.sourcePath.substringAfterLast('/').ifEmpty { t.sourcePath }
                val progress = if (t.totalBytes > 0) {
                    t.transferredBytes.toFloat() / t.totalBytes.toFloat()
                } else {
                    0f
                }
                // Phase 11 P3.2 — OriWearCard replaces Wear M3 Card.
                // Active transfers (status == "ACTIVE") get the accent ring
                // to match the .s3-card.running mockup styling.
                OriWearCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentBorder = t.status == "ACTIVE",
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProgressRing(progress = progress)
                        Column {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(t.status, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
