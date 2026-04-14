package dev.ori.feature.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.ChevronLeft
import dev.ori.core.ui.icons.lucide.File
import dev.ori.core.ui.icons.lucide.Folder
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.domain.model.FileItem
import kotlinx.coroutines.launch

/**
 * Phase 11 P4.3 — "Open file" bottom sheet for the Code Editor.
 *
 * Shows a two-segment header to toggle between Local and Remote file systems,
 * a monospaced breadcrumb of the current path, and a scrollable list of the
 * directory entries. Tapping a directory re-navigates the sheet; tapping a
 * file fires [onOpenFile] with the full path and the current remote flag.
 *
 * Deliberately light on chrome — no search, no multi-select — because its
 * job is "pick one file to open", not "compete with the full File Manager".
 * The File Manager is still the right place for bulk operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteFilePickerSheet(
    picker: PickerState,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onSetRemote: (Boolean) -> Unit,
    onOpenFile: (path: String, isRemote: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Open File",
                style = MaterialTheme.typography.titleMedium,
            )

            // Local / Remote source toggle.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !picker.isRemote,
                    onClick = { onSetRemote(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Local")
                }
                SegmentedButton(
                    selected = picker.isRemote,
                    onClick = { onSetRemote(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Remote")
                }
            }

            // Breadcrumb + up button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val canGoUp = picker.currentPath != "/" && picker.currentPath.isNotEmpty()
                Icon(
                    imageVector = LucideIcons.ChevronLeft,
                    contentDescription = "Übergeordneter Ordner",
                    tint = if (canGoUp) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .then(
                            if (canGoUp) {
                                Modifier.clickable { onNavigate(parentOf(picker.currentPath)) }
                            } else {
                                Modifier
                            },
                        ),
                )
                Text(
                    text = picker.currentPath.ifEmpty { "/" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 420.dp),
            ) {
                when {
                    picker.isLoading -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    picker.error != null -> {
                        Text(
                            text = "Error: ${picker.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    picker.entries.isEmpty() -> {
                        Text(
                            text = "(empty)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    else -> {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(picker.entries, key = { it.path }) { entry ->
                                PickerRow(
                                    entry = entry,
                                    onClick = {
                                        if (entry.isDirectory) {
                                            onNavigate(entry.path)
                                        } else {
                                            scope.launch {
                                                onOpenFile(entry.path, picker.isRemote)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PickerRow(entry: FileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (entry.isDirectory) LucideIcons.Folder else LucideIcons.File,
            contentDescription = null,
            tint = if (entry.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun parentOf(path: String): String {
    if (path == "/" || path.isEmpty()) return "/"
    val trimmed = path.trimEnd('/')
    val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
    return if (parent.isEmpty()) "/" else parent
}
