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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.component.StatusDot
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.components.OriProgressBar
import dev.ori.core.ui.theme.Gray200
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
    // Phase 11 P2.6-polish — OriCard replaces M3 Card (flat, bordered, 14 dp
    // radius). Selection state is conveyed via a 2 dp Indigo600 border per the
    // proxmox.html `.node-card.selected` mockup styling.
    val border = if (selected) {
        BorderStroke(2.dp, Indigo600)
    } else {
        BorderStroke(1.dp, Gray200)
    }
    val statusText = if (node.isOnline) "online" else "offline"
    val cpuText = node.cpuUsage?.let { ", CPU ${(it * 100).toInt()} Prozent" }.orEmpty()
    val memText = run {
        val used = node.memUsedBytes
        val total = node.memTotalBytes
        if (used != null && total != null && total > 0L) {
            ", RAM ${used / BYTES_PER_MB} von ${total / BYTES_PER_MB} Megabyte"
        } else {
            ""
        }
    }
    val nodeDescription = "Proxmox-Node ${node.name}, ${node.host}, $statusText$cpuText$memText"
    OriCard(
        modifier = modifier
            .width(NODE_CARD_WIDTH_DP.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = nodeDescription
                role = Role.Button
                this.selected = selected
            },
        onClick = onClick,
        border = border,
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
                // Phase 11 P2.6-polish — OriProgressBar replaces M3
                // LinearProgressIndicator (fixed 4 dp track, animated fill).
                OriProgressBar(progress = cpu.toFloat(), height = 6.dp)
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
                OriProgressBar(progress = frac, height = 6.dp)
            }
        }
    }
}
