package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.component.StatusDot
import dev.ori.core.ui.icons.lucide.CheckSquare
import dev.ori.core.ui.icons.lucide.File
import dev.ori.core.ui.icons.lucide.FileCode
import dev.ori.core.ui.icons.lucide.FileText
import dev.ori.core.ui.icons.lucide.Folder
import dev.ori.core.ui.icons.lucide.FolderPlus
import dev.ori.core.ui.icons.lucide.Grid2x2
import dev.ori.core.ui.icons.lucide.Image
import dev.ori.core.ui.icons.lucide.List
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.RefreshCw
import dev.ori.core.ui.icons.lucide.Settings
import dev.ori.domain.model.FileItem
import androidx.compose.foundation.lazy.grid.items as gridItems

@Suppress("LongParameterList")
@Composable
fun FileListPane(
    paneState: PaneState,
    @Suppress("UnusedParameter") pane: ActivePane,
    onNavigateToPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onRefresh: () -> Unit,
    onCreateDirectory: () -> Unit,
    onShowFileInfo: (FileItem) -> Unit,
    onShowFilePreview: (FileItem) -> Unit,
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
                modifier = Modifier.semantics { heading() },
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
                    LucideIcons.CheckSquare,
                    contentDescription = "Alle auswählen",
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
                // Phase 11 P2.5-polish — Lucide Grid2x2 / List replace
                // Material GridView / ViewList.
                Icon(
                    imageVector = if (paneState.viewMode == ViewMode.LIST) {
                        LucideIcons.Grid2x2
                    } else {
                        LucideIcons.List
                    },
                    contentDescription = if (paneState.viewMode == ViewMode.LIST) {
                        "Zu Rasteransicht wechseln"
                    } else {
                        "Zu Listenansicht wechseln"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCreateDirectory) {
                Icon(
                    LucideIcons.FolderPlus,
                    contentDescription = "Neuen Ordner anlegen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh) {
                // Phase 11 P2.5-polish — Lucide RefreshCw replaces Material Refresh.
                Icon(
                    LucideIcons.RefreshCw,
                    contentDescription = "Aktualisieren",
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
            when (paneState.viewMode) {
                ViewMode.LIST -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Parent directory entry
                    if (paneState.currentPath != "/") {
                        item(key = "parent_dir") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateUp() }
                                    .semantics {
                                        contentDescription = "Eine Ebene nach oben"
                                    }
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
                                        onShowFilePreview(file)
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

                // PR 3 bugfix — the Kachel/List toggle previously mutated
                // `viewMode` but the renderer always used LazyColumn, so
                // the user saw the icon flip with no visual change.
                // LazyVerticalGrid renders a real tile layout now.
                ViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(items = paneState.files, key = { it.path }) { file ->
                        FileItemCell(
                            file = file,
                            isSelected = file.path in paneState.selectedFiles,
                            onClick = {
                                if (file.isDirectory) {
                                    onNavigateToPath(file.path)
                                } else {
                                    onShowFilePreview(file)
                                }
                            },
                            onLongClick = {
                                contextMenuFile = file
                                onShowContextMenu(file)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * PR 3 — tile used by [ViewMode.GRID]. Mirrors the icon-choice logic
 * from [FileItemRow] so folders, code files, text files, images, and
 * config files all get the correct Lucide glyph instead of a generic
 * "File" icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemCell(
    file: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = gridFileIcon(file),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (file.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun gridFileIcon(file: FileItem): ImageVector {
    if (file.isDirectory) return LucideIcons.Folder
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "kt", "java", "py", "js", "ts", "c", "cpp", "h", "rs", "go", "rb", "swift",
        "sh", "bash", "zsh", "html", "css", "xml", "json", "yaml", "yml", "toml",
        -> LucideIcons.FileCode

        "md", "txt", "doc", "docx", "pdf", "rtf", "odt",
        -> LucideIcons.FileText

        "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico",
        -> LucideIcons.Image

        "conf", "cfg", "ini", "properties", "env", "gradle",
        -> LucideIcons.Settings

        else -> LucideIcons.File
    }
}

@Composable
private fun BreadcrumbBar(
    path: String,
    onSegmentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    // Phase 15 Task 15.6 follow-up: SAF tree URIs (content://…/tree/…)
    // MUST NOT be split on '/' — the "//" in "content://" collapses and
    // the percent-encoded tree/document IDs get shredded, producing the
    // garbled URI "/content:/com.android.externalstorage.documents/tree"
    // the user saw in the listfiles error report. SAF has no useful
    // per-segment navigation above the granted tree root anyway
    // (DocumentsContract does not expose cross-tree parents), so render
    // a single non-clickable label with the decoded document name and
    // let [navigateUp]'s pathStack pop handle "go back".
    if (path.startsWith("content://")) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = safDisplayLabel(path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        return
    }

    val segments = path.split("/").filter { it.isNotEmpty() }

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

/**
 * Extracts a user-visible label from a SAF tree or document URI.
 *
 * - Tree URI: `content://…/tree/primary%3ADocuments` → `"Documents"`
 * - Document URI: `content://…/tree/…/document/primary%3ADocuments%2Fsub%2Fnested`
 *   → `"nested"`
 *
 * Falls back to the raw URI on any parse failure — the UI stays
 * alive, worst case the user sees the opaque content:// string.
 */
internal fun safDisplayLabel(contentUri: String): String {
    val afterDocument = contentUri.substringAfterLast("/document/", missingDelimiterValue = "")
    val afterTree = contentUri.substringAfterLast("/tree/", missingDelimiterValue = "")
    val raw = afterDocument.ifEmpty { afterTree }.ifEmpty { return contentUri }
    val decoded = java.net.URLDecoder.decode(raw, Charsets.UTF_8.name())
    // DocumentsContract IDs look like "primary:Documents/sub/nested".
    // Strip the authority prefix and return the last path segment.
    val afterColon = decoded.substringAfter(':', missingDelimiterValue = decoded)
    return afterColon.substringAfterLast('/', missingDelimiterValue = afterColon).ifEmpty { afterColon }
}
