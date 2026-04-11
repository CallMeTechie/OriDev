package dev.ori.feature.filemanager.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.ori.domain.model.FileItem

@Composable
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
            onClick = { onInfo(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Info, null) },
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = { onRename(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
        )
        DropdownMenuItem(
            text = { Text("Permissions") },
            onClick = { onChmod(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
        )
        DropdownMenuItem(
            text = { Text("Transfer") },
            onClick = { onTransfer(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = { onDelete(); onDismiss() },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}
