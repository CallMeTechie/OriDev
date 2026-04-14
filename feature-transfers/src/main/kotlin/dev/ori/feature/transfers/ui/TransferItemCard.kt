package dev.ori.feature.transfers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ori.core.common.extension.toHumanReadableSize
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.components.OriProgressBar
import dev.ori.core.ui.components.OriStatusBadge
import dev.ori.core.ui.components.OriStatusBadgeIntent
import dev.ori.core.ui.icons.lucide.Download
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Pause
import dev.ori.core.ui.icons.lucide.Play
import dev.ori.core.ui.icons.lucide.RotateCcw
import dev.ori.core.ui.icons.lucide.Upload
import dev.ori.core.ui.icons.lucide.X
import dev.ori.core.ui.theme.Gray400
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.StatusDisconnected
import dev.ori.domain.model.TransferRequest

private const val FULL_PROGRESS = 100

@Composable
fun TransferItemCard(
    transfer: TransferRequest,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fileName = transfer.sourcePath.substringAfterLast('/')
    val progressFraction = if (transfer.totalBytes > 0) {
        (transfer.transferredBytes.toFloat() / transfer.totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressPercent = (progressFraction * FULL_PROGRESS).toInt()
    val statusLabel = when (transfer.status) {
        TransferStatus.ACTIVE -> "aktiv"
        TransferStatus.QUEUED -> "in Warteschlange"
        TransferStatus.PAUSED -> "pausiert"
        TransferStatus.COMPLETED -> "abgeschlossen"
        TransferStatus.FAILED -> "fehlgeschlagen"
    }
    val directionLabel = when (transfer.direction) {
        TransferDirection.UPLOAD -> "Upload"
        TransferDirection.DOWNLOAD -> "Download"
    }
    val cardDescription = "$directionLabel $fileName, $progressPercent Prozent, $statusLabel"

    // Phase 11 P2.4-polish — OriCard replaces M3 Card per transfer-queue.html
    // spec (flat, bordered, 14 dp radius).
    OriCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = cardDescription
                },
            ) {
                DirectionAndFileRow(transfer)
                Spacer(modifier = Modifier.height(8.dp))
                ProgressSection(transfer, progressFraction, progressPercent)
            }
            Spacer(modifier = Modifier.height(8.dp))
            StatusAndActionsRow(transfer, onPause, onResume, onCancel, onRetry)
            if (transfer.status == TransferStatus.FAILED && !transfer.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transfer.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusDisconnected,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DirectionAndFileRow(transfer: TransferRequest) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Phase 11 P2.4-polish — Lucide Upload/Download replace Material
        // CloudUpload/CloudDownload (mockup uses simple arrow-in-box glyphs).
        Icon(
            imageVector = when (transfer.direction) {
                TransferDirection.UPLOAD -> LucideIcons.Upload
                TransferDirection.DOWNLOAD -> LucideIcons.Download
            },
            contentDescription = null,
            tint = Indigo500,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transfer.sourcePath.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${transfer.sourcePath} \u2192 ${transfer.destinationPath}",
                style = MaterialTheme.typography.bodySmall,
                color = Gray400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressSection(
    transfer: TransferRequest,
    progressFraction: Float,
    progressPercent: Int,
) {
    // Phase 11 P2.4-polish — OriProgressBar replaces inline Box/background stack.
    OriProgressBar(progress = progressFraction)

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${transfer.transferredBytes.toHumanReadableSize()} / " +
                transfer.totalBytes.toHumanReadableSize(),
            style = MaterialTheme.typography.labelSmall,
            color = Gray400,
        )
        Text(
            text = "$progressPercent%",
            style = MaterialTheme.typography.labelSmall,
            color = Indigo500,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusAndActionsRow(
    transfer: TransferRequest,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Phase 11 P2.4-polish — OriStatusBadge replaces custom dot+text badge.
        val (label, intent) = when (transfer.status) {
            TransferStatus.ACTIVE -> "ACTIVE" to OriStatusBadgeIntent.Running
            TransferStatus.QUEUED -> "QUEUED" to OriStatusBadgeIntent.Queued
            TransferStatus.PAUSED -> "PAUSED" to OriStatusBadgeIntent.Paused
            TransferStatus.COMPLETED -> "COMPLETED" to OriStatusBadgeIntent.Completed
            TransferStatus.FAILED -> "FAILED" to OriStatusBadgeIntent.Failed
        }
        OriStatusBadge(label = label, intent = intent)
        ActionButtons(transfer, onPause, onResume, onCancel, onRetry)
    }
}

@Suppress("UnusedParameter")
@Composable
private fun ActionButtons(
    transfer: TransferRequest,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Row {
        when (transfer.status) {
            TransferStatus.ACTIVE -> {
                SmallActionButton(LucideIcons.Pause, "Übertragung pausieren", Indigo500, onPause)
                Spacer(modifier = Modifier.width(4.dp))
                SmallActionButton(
                    LucideIcons.X,
                    "Übertragung abbrechen",
                    StatusDisconnected,
                    onCancel,
                )
            }
            TransferStatus.PAUSED -> {
                SmallActionButton(
                    LucideIcons.Play,
                    "Übertragung fortsetzen",
                    Indigo500,
                    onResume,
                )
                Spacer(modifier = Modifier.width(4.dp))
                SmallActionButton(
                    LucideIcons.X,
                    "Übertragung abbrechen",
                    StatusDisconnected,
                    onCancel,
                )
            }
            TransferStatus.QUEUED -> {
                SmallActionButton(
                    LucideIcons.X,
                    "Übertragung abbrechen",
                    StatusDisconnected,
                    onCancel,
                )
            }
            TransferStatus.FAILED -> {
                SmallActionButton(
                    LucideIcons.RotateCcw,
                    "Übertragung erneut versuchen",
                    Indigo500,
                    onRetry,
                )
            }
            TransferStatus.COMPLETED -> {
                // No actions for completed transfers
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
