package dev.ori.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.Copy
import dev.ori.core.ui.icons.lucide.ExternalLink
import dev.ori.core.ui.icons.lucide.LucideIcons
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeBlocksSheet(
    blocks: List<DetectedCodeBlock>,
    onCopy: (String) -> Unit,
    onOpenInEditor: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Detected code blocks",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (blocks.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }

            if (blocks.isEmpty()) {
                Text(
                    text = "No code blocks detected yet. Run commands that output ``` fenced code blocks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = blocks, key = { it.id }) { block ->
                        CodeBlockRow(
                            block = block,
                            onCopy = { onCopy(block.id) },
                            onOpenInEditor = { onOpenInEditor(block.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockRow(
    block: DetectedCodeBlock,
    onCopy: () -> Unit,
    onOpenInEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = block.language ?: "text",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = relativeTime(block.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val previewLines = block.content.lines().take(PREVIEW_LINE_COUNT)
        Text(
            text = previewLines.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (block.content.lines().size > PREVIEW_LINE_COUNT) {
            Text(
                text = "… ${block.content.lines().size - PREVIEW_LINE_COUNT} more lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onCopy) {
                Icon(LucideIcons.Copy, contentDescription = "Copy")
            }
            IconButton(onClick = onOpenInEditor) {
                Icon(LucideIcons.ExternalLink, contentDescription = "Open in Editor")
            }
        }
    }
}

private const val PREVIEW_LINE_COUNT = 5

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    return when {
        seconds < SECONDS_THRESHOLD -> "just now"
        minutes < 1 -> "${seconds}s ago"
        hours < 1 -> "${minutes}m ago"
        else -> "${hours}h ago"
    }
}

private const val SECONDS_THRESHOLD = 5L
