package dev.ori.feature.proxmox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.components.OriStatusBadge
import dev.ori.core.ui.components.OriStatusBadgeIntent
import dev.ori.core.ui.icons.lucide.CircleStop
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Play
import dev.ori.core.ui.icons.lucide.RotateCcw
import dev.ori.core.ui.icons.lucide.Trash2
import dev.ori.core.ui.theme.Gray100
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.Gray700
import dev.ori.core.ui.theme.Indigo600
import dev.ori.core.ui.theme.StatusConnected
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
    // Phase 11 P2.6-polish — OriCard replaces M3 Card.
    OriCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val statusLabel = when (vm.status) {
                ProxmoxVmStatus.RUNNING -> "läuft"
                ProxmoxVmStatus.STOPPED -> "gestoppt"
                ProxmoxVmStatus.PAUSED -> "pausiert"
                ProxmoxVmStatus.UNKNOWN -> "unbekannt"
            }
            val vmDescription = "VM ${vm.vmid} ${vm.name}, Status $statusLabel"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = vmDescription
                    },
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
                // Phase 11 P2.6-polish — OriStatusBadge replaces the ad-hoc
                // badge with darkened-alpha background. Mockup
                // `.badge-running/.badge-stopped/.badge-paused` map to
                // Running/Stopped/Paused intents (green/red/yellow).
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
                    // Phase 11 P2.6-polish — Lucide icons replace Material:
                    // PlayArrow → Play, Stop → CircleStop, Refresh → RotateCcw,
                    // Delete → Trash2.
                    when (vm.status) {
                        ProxmoxVmStatus.STOPPED -> {
                            IconButton(onClick = onStart) {
                                Icon(
                                    imageVector = LucideIcons.Play,
                                    contentDescription = "VM starten",
                                    tint = StatusConnected,
                                )
                            }
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = LucideIcons.Trash2,
                                    contentDescription = "VM löschen",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        ProxmoxVmStatus.RUNNING -> {
                            IconButton(onClick = onStop) {
                                Icon(
                                    imageVector = LucideIcons.CircleStop,
                                    contentDescription = "VM stoppen",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            IconButton(onClick = onRestart) {
                                Icon(
                                    imageVector = LucideIcons.RotateCcw,
                                    contentDescription = "VM neu starten",
                                    tint = Indigo600,
                                )
                            }
                        }
                        ProxmoxVmStatus.PAUSED -> {
                            IconButton(onClick = onStop) {
                                Icon(
                                    imageVector = LucideIcons.CircleStop,
                                    contentDescription = "VM stoppen",
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
    val (label, intent) = when (status) {
        ProxmoxVmStatus.RUNNING -> "RUNNING" to OriStatusBadgeIntent.Running
        ProxmoxVmStatus.STOPPED -> "STOPPED" to OriStatusBadgeIntent.Stopped
        ProxmoxVmStatus.PAUSED -> "PAUSED" to OriStatusBadgeIntent.Paused
        ProxmoxVmStatus.UNKNOWN -> "UNKNOWN" to OriStatusBadgeIntent.Stopped
    }
    OriStatusBadge(label = label, intent = intent)
}
