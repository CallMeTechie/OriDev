package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.component.OriDevTopBar

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel = hiltViewModel(),
    onNavigateToEditor: (filePath: String, isRemote: Boolean) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deletePane by remember { mutableStateOf(ActivePane.LEFT) }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isFolded = screenWidthDp < 600

    LaunchedEffect(isFolded) {
        viewModel.onEvent(FileManagerEvent.SetFoldState(isFolded))
    }

    // Show transfer snackbar
    LaunchedEffect(uiState.transferSnackbar) {
        uiState.transferSnackbar?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearTransferSnackbar()
        }
    }

    // Show errors via snackbar
    LaunchedEffect(uiState.leftPane.error, uiState.rightPane.error) {
        val leftError = uiState.leftPane.error
        val rightError = uiState.rightPane.error
        val error = leftError ?: rightError
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            if (leftError != null) {
                viewModel.onEvent(FileManagerEvent.ClearError(ActivePane.LEFT))
            }
            if (rightError != null) {
                viewModel.onEvent(FileManagerEvent.ClearError(ActivePane.RIGHT))
            }
        }
    }

    Scaffold(
        topBar = {
            OriDevTopBar(title = "File Manager")
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val activePane = uiState.activePane
            val paneState = if (activePane == ActivePane.LEFT) uiState.leftPane else uiState.rightPane
            val selectedCount = paneState.selectedFiles.size

            if (selectedCount > 0) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                deletePane = activePane
                                showDeleteConfirmation = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Ausgewählte Dateien löschen",
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Bookmark bar
            if (uiState.bookmarks.isNotEmpty()) {
                BookmarkBar(
                    bookmarks = uiState.bookmarks,
                    onBookmarkClick = { bookmark ->
                        val pane = uiState.activePane
                        viewModel.onEvent(FileManagerEvent.NavigateToPath(pane, bookmark.path))
                    },
                )
            }

            // Content area
            if (isFolded) {
                // Single pane with tab switching
                FoldedContent(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                )
            } else {
                // Dual pane layout
                UnfoldedContent(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    viewModel = viewModel,
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        val paneState = if (deletePane == ActivePane.LEFT) uiState.leftPane else uiState.rightPane
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Files") },
            text = {
                Text("Are you sure you want to delete ${paneState.selectedFiles.size} selected item(s)?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(FileManagerEvent.DeleteSelected(deletePane))
                        showDeleteConfirmation = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // File info bottom sheet
    uiState.showFileInfo?.let { file ->
        FileInfoSheet(
            file = file,
            onDismiss = { viewModel.onEvent(FileManagerEvent.ShowFileInfo(null)) },
        )
    }

    // File preview bottom sheet
    uiState.previewFile?.let { file ->
        FilePreviewSheet(
            file = file,
            content = uiState.previewContent,
            loading = uiState.previewLoading,
            errorMessage = uiState.previewError,
            isRemote = uiState.previewPane == ActivePane.RIGHT,
            onOpenInEditor = { path, isRemote ->
                viewModel.onEvent(FileManagerEvent.ClosePreview)
                onNavigateToEditor(path, isRemote)
            },
            onDismiss = { viewModel.onEvent(FileManagerEvent.ClosePreview) },
        )
    }
}

@Composable
private fun FoldedContent(
    uiState: FileManagerUiState,
    onEvent: (FileManagerEvent) -> Unit,
) {
    val selectedTabIndex = if (uiState.activePane == ActivePane.LEFT) 0 else 1

    TabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Tab(
            selected = selectedTabIndex == 0,
            onClick = { onEvent(FileManagerEvent.SetActivePane(ActivePane.LEFT)) },
            text = { Text("Local") },
        )
        Tab(
            selected = selectedTabIndex == 1,
            onClick = { onEvent(FileManagerEvent.SetActivePane(ActivePane.RIGHT)) },
            text = { Text("Remote") },
        )
    }

    val pane = uiState.activePane
    val paneState = if (pane == ActivePane.LEFT) uiState.leftPane else uiState.rightPane

    PaneContent(
        paneState = paneState,
        pane = pane,
        onEvent = onEvent,
    )
}

@Composable
private fun UnfoldedContent(
    uiState: FileManagerUiState,
    onEvent: (FileManagerEvent) -> Unit,
    viewModel: FileManagerViewModel,
) {
    DualPaneLayout(
        splitRatio = uiState.splitRatio,
        onSplitRatioChange = { onEvent(FileManagerEvent.UpdateSplitRatio(it)) },
        dragState = uiState.dragState,
        leftPane = {
            PaneContent(
                paneState = uiState.leftPane,
                pane = ActivePane.LEFT,
                onEvent = onEvent,
                viewModel = viewModel,
            )
        },
        rightPane = {
            PaneContent(
                paneState = uiState.rightPane,
                pane = ActivePane.RIGHT,
                onEvent = onEvent,
                viewModel = viewModel,
            )
        },
    )
}

@Composable
private fun PaneContent(
    paneState: PaneState,
    pane: ActivePane,
    onEvent: (FileManagerEvent) -> Unit,
    viewModel: FileManagerViewModel? = null,
) {
    FileListPane(
        paneState = paneState,
        pane = pane,
        onNavigateToPath = { path -> onEvent(FileManagerEvent.NavigateToPath(pane, path)) },
        onNavigateUp = { onEvent(FileManagerEvent.NavigateUp(pane)) },
        onToggleSelection = { path -> onEvent(FileManagerEvent.ToggleFileSelection(pane, path)) },
        onSelectAll = { onEvent(FileManagerEvent.SelectAllFiles(pane)) },
        onSetViewMode = { mode -> onEvent(FileManagerEvent.SetViewMode(pane, mode)) },
        onRefresh = { onEvent(FileManagerEvent.RefreshPane(pane)) },
        onCreateDirectory = {
            /* Create directory dialog -- handled by parent in future iteration */
        },
        onShowFileInfo = { file -> onEvent(FileManagerEvent.ShowFileInfo(file)) },
        onShowFilePreview = { file -> onEvent(FileManagerEvent.ShowFilePreview(pane, file)) },
        onShowContextMenu = { file -> onEvent(FileManagerEvent.ShowContextMenu(file)) },
        onRename = { /* Rename dialog -- handled by parent in future iteration */ },
        onDelete = { file ->
            onEvent(FileManagerEvent.ToggleFileSelection(pane, file.path))
            onEvent(FileManagerEvent.DeleteSelected(pane))
        },
        onChmod = { /* Chmod dialog -- handled by parent in future iteration */ },
        onDragStart = { filePath ->
            val selectedPaths = paneState.selectedFiles.toList().ifEmpty { listOf(filePath) }
            viewModel?.setDragState(
                DragState(isDragging = true, sourcePane = pane, draggedPaths = selectedPaths),
            )
        },
        onDragEnd = {
            viewModel?.setDragState(DragState())
        },
        onDrop = {
            viewModel?.let { vm ->
                val dragState = vm.uiState.value.dragState
                if (dragState.isDragging && dragState.sourcePane != null && dragState.sourcePane != pane) {
                    onEvent(FileManagerEvent.InitiateTransfer(dragState.draggedPaths, dragState.sourcePane))
                }
            }
        },
    )
}
