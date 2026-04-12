package dev.ori.feature.proxmox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray100
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.Gray700
import dev.ori.core.ui.theme.Indigo600
import dev.ori.core.ui.theme.StatusConnected
import dev.ori.core.ui.theme.StatusWarning
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.model.ProxmoxVmStatus

private const val BYTES_PER_MB = 1024L * 1024L

@Suppress("LongMethod")
@Composable
fun VmCard(
    vm: ProxmoxVm,
    actionInProgress: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = vm.vmid.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray700,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Gray100)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = vm.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                VmStatusBadge(status = vm.status)
            }
            if (vm.status == ProxmoxVmStatus.RUNNING) {
                val cpu = vm.cpuUsage
                val memUsed = vm.memUsedBytes
                val memTotal = vm.memTotalBytes
                val parts = buildList {
                    if (cpu != null) add("CPU ${(cpu * 100).toInt()}%")
                    if (memUsed != null && memTotal != null && memTotal > 0L) {
                        add("RAM ${memUsed / BYTES_PER_MB} / ${memTotal / BYTES_PER_MB} MB")
                    }
                }
                if (parts.isNotEmpty()) {
                    Text(
                        text = parts.joinToString(" | "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray500,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Indigo600,
                    )
                } else {
                    when (vm.status) {
                        ProxmoxVmStatus.STOPPED -> {
                            IconButton(onClick = onStart) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Start",
                                    tint = StatusConnected,
                                )
                            }
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        ProxmoxVmStatus.RUNNING -> {
                            IconButton(onClick = onStop) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            IconButton(onClick = onRestart) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Restart",
                                    tint = Indigo600,
                                )
                            }
                        }
                        ProxmoxVmStatus.PAUSED -> {
                            IconButton(onClick = onStop) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        ProxmoxVmStatus.UNKNOWN -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun VmStatusBadge(status: ProxmoxVmStatus) {
    val (label, color) = when (status) {
        ProxmoxVmStatus.RUNNING -> "RUNNING" to StatusConnected
        ProxmoxVmStatus.STOPPED -> "STOPPED" to Gray500
        ProxmoxVmStatus.PAUSED -> "PAUSED" to StatusWarning
        ProxmoxVmStatus.UNKNOWN -> "UNKNOWN" to Gray500
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = darken(color),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun darken(color: Color): Color = Color(
    red = color.red * 0.8f,
    green = color.green * 0.8f,
    blue = color.blue * 0.8f,
    alpha = 1f,
)
