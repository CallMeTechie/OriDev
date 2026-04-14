package dev.ori.feature.filemanager.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.ori.core.ui.icons.lucide.ArrowLeftRight
import dev.ori.core.ui.icons.lucide.Info
import dev.ori.core.ui.icons.lucide.Lock
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.PenLine
import dev.ori.core.ui.icons.lucide.Trash2
import dev.ori.domain.model.FileItem

// Phase 11 P2.5-polish — Lucide icons replace the entire Material icon set
// used by this menu (Info, DriveFileRenameOutline, Lock, SwapHoriz, Delete)
// per the forbidden-imports policy.
@Composable
@Suppress("UnusedParameter")
fun FileContextMenu(
    file: FileItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChmod: () -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text("Info") },
            onClick = {
                onInfo()
                onDismiss()
            },
            leadingIcon = { Icon(LucideIcons.Info, null) },
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = {
                onRename()
                onDismiss()
            },
            leadingIcon = { Icon(LucideIcons.PenLine, null) },
        )
        DropdownMenuItem(
            text = { Text("Permissions") },
            onClick = {
                onChmod()
                onDismiss()
            },
            leadingIcon = { Icon(LucideIcons.Lock, null) },
        )
        DropdownMenuItem(
            text = { Text("Transfer") },
            onClick = {
                onTransfer()
                onDismiss()
            },
            leadingIcon = { Icon(LucideIcons.ArrowLeftRight, null) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDelete()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    LucideIcons.Trash2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}
