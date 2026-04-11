package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.component.StatusDot
import dev.ori.domain.model.FileItem

@Composable
fun FileListPane(
    paneState: PaneState,
    pane: ActivePane,
    onNavigateToPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onCreateDirectory: () -> Unit,
    onShowFileInfo: (FileItem) -> Unit,
    onShowContextMenu: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onChmod: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: (String) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrop: () -> Unit = {},
) {
    var contextMenuFile by remember { mutableStateOf<FileItem?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (paneState.isRemote) {
                StatusDot(isConnected = true)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (paneState.isRemote) {
                    paneState.serverName ?: "Remote"
                } else {
                    "Local"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = "Select all",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    val newMode = if (paneState.viewMode == ViewMode.LIST) {
                        ViewMode.GRID
                    } else {
                        ViewMode.LIST
                    }
                    onSetViewMode(newMode)
                },
            ) {
                Icon(
                    imageVector = if (paneState.viewMode == ViewMode.LIST) {
                        Icons.Default.GridView
                    } else {
                        Icons.AutoMirrored.Filled.ViewList
                    },
                    contentDescription = "Toggle view mode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCreateDirectory) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = "New folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Breadcrumb bar
        BreadcrumbBar(
            path = paneState.currentPath,
            onSegmentClick = onNavigateToPath,
        )

        // File list
        if (paneState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                dev.ori.core.ui.component.LoadingIndicator()
            }
        } else if (paneState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = paneState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Parent directory entry
                if (paneState.currentPath != "/") {
                    item(key = "parent_dir") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateUp() }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "..",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                items(
                    items = paneState.files,
                    key = { it.path },
                ) { file ->
                    Box {
                        FileItemRow(
                            file = file,
                            isSelected = file.path in paneState.selectedFiles,
                            onClick = {
                                if (file.isDirectory) {
                                    onNavigateToPath(file.path)
                                } else {
                                    onToggleSelection(file.path)
                                }
                            },
                            onLongClick = {
                                contextMenuFile = file
                                onShowContextMenu(file)
                            },
                            onToggleSelection = { onToggleSelection(file.path) },
                            onDragStart = { onDragStart(file.path) },
                            onDragEnd = onDragEnd,
                            onDrop = onDrop,
                        )

                        if (contextMenuFile == file) {
                            FileContextMenu(
                                file = file,
                                expanded = true,
                                onDismiss = { contextMenuFile = null },
                                onInfo = { onShowFileInfo(file) },
                                onRename = { onRename(file) },
                                onDelete = { onDelete(file) },
                                onChmod = { onChmod(file) },
                                onTransfer = { /* Transfer -- deferred to Phase 5 */ },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    path: String,
    onSegmentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = path.split("/").filter { it.isNotEmpty() }
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "/",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onSegmentClick("/") },
        )

        segments.forEachIndexed { index, segment ->
            Text(
                text = segment,
                style = MaterialTheme.typography.bodySmall,
                color = if (index == segments.lastIndex) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    val fullPath = "/" + segments.take(index + 1).joinToString("/")
                    onSegmentClick(fullPath)
                },
            )
            if (index < segments.lastIndex) {
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
}
