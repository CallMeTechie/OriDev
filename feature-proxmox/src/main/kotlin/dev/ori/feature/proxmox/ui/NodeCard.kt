package dev.ori.feature.proxmox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.component.StatusDot
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.Indigo600
import dev.ori.domain.model.ProxmoxNode

private const val NODE_CARD_WIDTH_DP = 220
private const val BYTES_PER_MB = 1024L * 1024L

@Composable
fun NodeCard(
    node: ProxmoxNode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) {
        BorderStroke(2.dp, Indigo600)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Card(
        modifier = modifier
            .width(NODE_CARD_WIDTH_DP.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = border,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDot(isConnected = node.isOnline)
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "${node.host}:${node.port}",
                style = MaterialTheme.typography.bodySmall,
                color = Gray500,
            )
            val cpu = node.cpuUsage
            if (cpu != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "CPU ${(cpu * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray500,
                )
                LinearProgressIndicator(
                    progress = { cpu.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Indigo600,
                )
            }
            val memUsed = node.memUsedBytes
            val memTotal = node.memTotalBytes
            if (memUsed != null && memTotal != null && memTotal > 0L) {
                val frac = memUsed.toFloat() / memTotal.toFloat()
                Text(
                    text = "RAM ${memUsed / BYTES_PER_MB} / ${memTotal / BYTES_PER_MB} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray500,
                )
                LinearProgressIndicator(
                    progress = { frac.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Indigo600,
                )
            }
        }
    }
}
