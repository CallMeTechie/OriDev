package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.Copy
import dev.ori.core.ui.icons.lucide.Info
import dev.ori.core.ui.icons.lucide.Lock
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Pencil
import dev.ori.core.ui.icons.lucide.Trash2
import dev.ori.domain.model.FileItem

/**
 * Bottom action bar for the file-manager multi-selection state.
 *
 * Before this existed the only action exposed for a selection was the
 * delete button, so a user who had selected a handful of files was left
 * wondering whether any other file operations existed at all. The
 * per-item long-press `FileContextMenu` does support Rename/Chmod/Info
 * individually, but the multi-select UI never surfaced those paths.
 *
 * Visibility rules:
 * - Copy (to the other pane) is available for any non-empty selection.
 * - Rename / Chmod / Info only make sense for exactly one selected item
 *   (their backing events take a single [FileItem]), so those buttons
 *   are only rendered when `selectedCount == 1`.
 * - Delete is always present and is visually distinct as the destructive
 *   terminal action.
 */
@Composable
internal fun MultiSelectToolbar(
    selectedCount: Int,
    singleSelectedFile: FileItem?,
    onCopyToOtherPane: () -> Unit,
    onRenameSingle: (FileItem) -> Unit,
    onChmodSingle: (FileItem) -> Unit,
    onInfoSingle: (FileItem) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "$selectedCount ausgewählt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )

            IconButton(onClick = onCopyToOtherPane) {
                Icon(
                    imageVector = LucideIcons.Copy,
                    contentDescription = "In andere Ansicht kopieren",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (singleSelectedFile != null) {
                IconButton(onClick = { onRenameSingle(singleSelectedFile) }) {
                    Icon(
                        imageVector = LucideIcons.Pencil,
                        contentDescription = "Umbenennen",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { onChmodSingle(singleSelectedFile) }) {
                    Icon(
                        imageVector = LucideIcons.Lock,
                        contentDescription = "Berechtigungen ändern",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { onInfoSingle(singleSelectedFile) }) {
                    Icon(
                        imageVector = LucideIcons.Info,
                        contentDescription = "Details anzeigen",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = LucideIcons.Trash2,
                    contentDescription = "Ausgewählte Dateien löschen",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
