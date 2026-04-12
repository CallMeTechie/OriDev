package dev.ori.feature.transfers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ori.core.common.extension.toHumanReadableSize
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Gray400
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.StatusConnected
import dev.ori.core.ui.theme.StatusDisconnected
import dev.ori.core.ui.theme.StatusWarning
import dev.ori.domain.model.TransferRequest

private val ProgressTrackColor = Gray200
private val ProgressBarColor = Indigo500
private const val PROGRESS_BAR_HEIGHT = 6
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
    val progressPercent = if (transfer.totalBytes > 0) {
        ((transfer.transferredBytes.toFloat() / transfer.totalBytes) * FULL_PROGRESS).toInt()
    } else {
        0
    }
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = cardDescription
                },
            ) {
                DirectionAndFileRow(transfer)
                Spacer(modifier = Modifier.height(8.dp))
                ProgressSection(transfer)
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
        Icon(
            imageVector = when (transfer.direction) {
                TransferDirection.UPLOAD -> Icons.Default.CloudUpload
                TransferDirection.DOWNLOAD -> Icons.Default.CloudDownload
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
private fun ProgressSection(transfer: TransferRequest) {
    val progress = if (transfer.totalBytes > 0) {
        (transfer.transferredBytes.toFloat() / transfer.totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percentage = (progress * FULL_PROGRESS).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROGRESS_BAR_HEIGHT.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(ProgressTrackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(PROGRESS_BAR_HEIGHT.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ProgressBarColor),
        )
    }

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
            text = "$percentage%",
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
        StatusBadge(transfer.status)
        ActionButtons(transfer, onPause, onResume, onCancel, onRetry)
    }
}

@Composable
private fun StatusBadge(status: TransferStatus) {
    val (text, color) = when (status) {
        TransferStatus.ACTIVE -> "Active" to Indigo500
        TransferStatus.QUEUED -> "Queued" to Gray400
        TransferStatus.PAUSED -> "Paused" to StatusWarning
        TransferStatus.COMPLETED -> "Completed" to StatusConnected
        TransferStatus.FAILED -> "Failed" to StatusDisconnected
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
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
                SmallActionButton(Icons.Default.Pause, "Übertragung pausieren", Indigo500, onPause)
                Spacer(modifier = Modifier.width(4.dp))
                SmallActionButton(
                    Icons.Default.Close,
                    "Übertragung abbrechen",
                    StatusDisconnected,
                    onCancel,
                )
            }
            TransferStatus.PAUSED -> {
                SmallActionButton(
                    Icons.Default.PlayArrow,
                    "Übertragung fortsetzen",
                    Indigo500,
                    onResume,
                )
                Spacer(modifier = Modifier.width(4.dp))
                SmallActionButton(
                    Icons.Default.Close,
                    "Übertragung abbrechen",
                    StatusDisconnected,
                    onCancel,
                )
            }
            TransferStatus.QUEUED -> {
                SmallActionButton(
                    Icons.Default.Close,
                    "Übertragung abbrechen",
                    StatusDisconnected,
                    onCancel,
                )
            }
            TransferStatus.FAILED -> {
                SmallActionButton(
                    Icons.Default.Refresh,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
