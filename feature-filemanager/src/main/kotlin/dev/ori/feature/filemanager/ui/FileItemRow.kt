package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ori.core.common.extension.toHumanReadableSize
import dev.ori.core.ui.theme.Indigo50
import dev.ori.domain.model.FileItem
import dev.ori.domain.model.GitStatus

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrop: () -> Unit = {},
) {
    val backgroundColor = if (isSelected) Indigo50 else Color.Transparent
    val typeLabel = if (file.isDirectory) "Ordner" else "Datei"
    val sizeLabel = if (file.isDirectory) "" else ", ${file.size.toHumanReadableSize()}"
    val rowDescription = "${file.name}, $typeLabel$sizeLabel"
    val selectionState = if (isSelected) "ausgewählt" else "nicht ausgewählt"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                stateDescription = selectionState
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = {
                        onDrop()
                        onDragEnd()
                    },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, _ -> change.consume() },
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = fileIcon(file),
            contentDescription = null,
            tint = if (file.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!file.isDirectory) {
                    Text(
                        text = file.size.toHumanReadableSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                file.permissions?.let { perms ->
                    Text(
                        text = perms,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        file.gitStatus?.let { status ->
            Spacer(modifier = Modifier.width(8.dp))
            GitStatusDot(status = status)
        }
    }
}

@Composable
private fun GitStatusDot(
    status: GitStatus,
    modifier: Modifier = Modifier,
) {
    val color = when (status) {
        GitStatus.STAGED -> Color(0xFF10B981)
        GitStatus.MODIFIED -> Color(0xFFF59E0B)
        GitStatus.UNTRACKED -> Color(0xFF9CA3AF)
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun fileIcon(file: FileItem): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "kt", "java", "py", "js", "ts", "c", "cpp", "h", "rs", "go", "rb", "swift",
        "sh", "bash", "zsh", "html", "css", "xml", "json", "yaml", "yml", "toml",
        -> Icons.Default.Code

        "md", "txt", "doc", "docx", "pdf", "rtf", "odt",
        -> Icons.Default.Description

        "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico",
        -> Icons.Default.Image

        "conf", "cfg", "ini", "properties", "env", "gradle",
        -> Icons.Default.Settings

        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
