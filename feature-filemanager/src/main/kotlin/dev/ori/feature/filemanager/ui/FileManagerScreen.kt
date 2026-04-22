package dev.ori.feature.filemanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriServiceIndicator
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.icons.lucide.FolderOpen
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.domain.model.AdSlot
import dev.ori.domain.model.FileItem
import dev.ori.feature.premium.ui.AdSlotHost

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel = hiltViewModel(),
    onNavigateToEditor: (filePath: String, isRemote: Boolean) -> Unit = { _, _ -> },
    onNavigateToConnections: () -> Unit = {},
) {
    // PR 2 — the phantom `filemanager/{profileId}` route is gone; entry via
    // registry focus + navigateToTopLevel means the ViewModel's
    // SavedStateHandle.get<Long>("profileId") returns null and
    // bindRemoteSession() short-circuits. Profile is surfaced through the
    // RemoteFileSystemSession that the Connections sheet will set up when it
    // calls sessionRegistry.connect(...) in a later chunk.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deletePane by remember { mutableStateOf(ActivePane.LEFT) }

    // Phase 11 P4.1 — dialog state for Rename / Chmod / Mkdir. Each var
    // carries the target pane alongside the target file (or null for mkdir,
    // which targets the pane's current directory).
    var renameTarget by remember {
        mutableStateOf<Pair<ActivePane, FileItem>?>(null)
    }
    var chmodTarget by remember {
        mutableStateOf<Pair<ActivePane, FileItem>?>(null)
    }
    var mkdirTarget by remember {
        mutableStateOf<ActivePane?>(null)
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isFolded = screenWidthDp < 600

    LaunchedEffect(isFolded) {
        viewModel.onEvent(FileManagerEvent.SetFoldState(isFolded))
    }

    // Phase 15 Task 15.6 — SAF launcher. `OpenDocumentTree` returns null
    // when the user backs out without picking, so the event is only
    // dispatched for a non-null URI.
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onEvent(FileManagerEvent.GrantTree(treeUri.toString()))
        }
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

    // Phase 11 carry-over #E — total files selected across both panes is the
    // best signal of "service activity" until the transfer engine exposes a
    // real in-flight count for this screen.
    val activeCount = uiState.leftPane.selectedFiles.size +
        uiState.rightPane.selectedFiles.size

    Scaffold(
        topBar = {
            // Phase 11 P2.5 — replaces deprecated OriDevTopBar with 60 dp
            // OriTopBar per file-manager.html mockup spec.
            OriTopBar(
                title = "File Manager",
                height = 60.dp,
                indicator = if (activeCount > 0) {
                    { OriServiceIndicator(count = activeCount, label = "ausgewählt") }
                } else {
                    null
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val activePane = uiState.activePane
            val paneState = if (activePane == ActivePane.LEFT) uiState.leftPane else uiState.rightPane
            val selectedCount = paneState.selectedFiles.size

            if (selectedCount > 0) {
                val singleFile = if (selectedCount == 1) {
                    paneState.files.firstOrNull { it.path == paneState.selectedFiles.first() }
                } else {
                    null
                }
                MultiSelectToolbar(
                    selectedCount = selectedCount,
                    singleSelectedFile = singleFile,
                    onCopyToOtherPane = {
                        viewModel.onEvent(
                            FileManagerEvent.InitiateTransfer(
                                sourcePaths = paneState.selectedFiles.toList(),
                                sourcePane = activePane,
                            ),
                        )
                    },
                    onRenameSingle = { file -> renameTarget = activePane to file },
                    onChmodSingle = { file -> chmodTarget = activePane to file },
                    onInfoSingle = { file ->
                        viewModel.onEvent(FileManagerEvent.ShowFileInfo(file))
                    },
                    onDelete = {
                        deletePane = activePane
                        showDeleteConfirmation = true
                    },
                )
            } else {
                AdSlotHost(
                    slot = AdSlot.FILE_MANAGER_STICKY,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            val dialogCallbacks = FileOpCallbacks(
                onShowRename = { pane, file -> renameTarget = pane to file },
                onShowChmod = { pane, file -> chmodTarget = pane to file },
                onShowMkdir = { pane -> mkdirTarget = pane },
            )
            // PR 3 — remote pane surfaces (header dropdown, empty state
            // CTA) collect registry state here once and pass it down so
            // both the folded and unfolded layouts see the same values.
            val openSessions by viewModel.openSessions.collectAsStateWithLifecycle()
            val focusedSessionId by viewModel.focusedSessionId.collectAsStateWithLifecycle()

            if (isFolded) {
                // Single pane with tab switching
                FoldedContent(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    dialogCallbacks = dialogCallbacks,
                    onPickFolder = { safLauncher.launch(null) },
                    openSessions = openSessions,
                    focusedSessionId = focusedSessionId,
                    onFocusSession = viewModel::focusSession,
                    onNavigateToConnections = onNavigateToConnections,
                )
            } else {
                // Dual pane layout
                UnfoldedContent(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    viewModel = viewModel,
                    dialogCallbacks = dialogCallbacks,
                    onPickFolder = { safLauncher.launch(null) },
                    openSessions = openSessions,
                    focusedSessionId = focusedSessionId,
                    onFocusSession = viewModel::focusSession,
                    onNavigateToConnections = onNavigateToConnections,
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

    // Phase 11 P4.1 — Rename / Chmod / Mkdir dialogs backed by dialog state.
    renameTarget?.let { (pane, file) ->
        RenameDialog(
            file = file,
            onConfirm = { newPath ->
                viewModel.onEvent(FileManagerEvent.RenameFile(pane, file.path, newPath))
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
    chmodTarget?.let { (pane, file) ->
        ChmodDialog(
            file = file,
            onConfirm = { perms ->
                viewModel.onEvent(FileManagerEvent.Chmod(pane, file.path, perms))
                chmodTarget = null
            },
            onDismiss = { chmodTarget = null },
        )
    }
    mkdirTarget?.let { pane ->
        val paneState = if (pane == ActivePane.LEFT) uiState.leftPane else uiState.rightPane
        MkdirDialog(
            parentPath = paneState.currentPath,
            onConfirm = { newPath ->
                viewModel.onEvent(FileManagerEvent.CreateDirectory(pane, newPath))
                mkdirTarget = null
            },
            onDismiss = { mkdirTarget = null },
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

/**
 * Phase 11 P4.1 — bundle of "open this dialog" callbacks so we can pass
 * one parameter through the content tree instead of three.
 */
private class FileOpCallbacks(
    val onShowRename: (ActivePane, FileItem) -> Unit,
    val onShowChmod: (ActivePane, FileItem) -> Unit,
    val onShowMkdir: (ActivePane) -> Unit,
)

@Composable
private fun FoldedContent(
    uiState: FileManagerUiState,
    onEvent: (FileManagerEvent) -> Unit,
    dialogCallbacks: FileOpCallbacks,
    onPickFolder: () -> Unit,
    openSessions: List<dev.ori.domain.model.Session>,
    focusedSessionId: String?,
    onFocusSession: (String) -> Unit,
    onNavigateToConnections: () -> Unit,
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

    when {
        pane == ActivePane.LEFT && uiState.grantedTrees.isEmpty() ->
            StorageAccessEmptyState(onPickFolder = onPickFolder)

        pane == ActivePane.RIGHT -> RemotePaneWithHeader(
            paneState = paneState,
            openSessions = openSessions,
            focusedSessionId = focusedSessionId,
            onFocusSession = onFocusSession,
            onNavigateToConnections = onNavigateToConnections,
            onEvent = onEvent,
            dialogCallbacks = dialogCallbacks,
        )

        else -> PaneContent(
            paneState = paneState,
            pane = pane,
            onEvent = onEvent,
            dialogCallbacks = dialogCallbacks,
        )
    }
}

@Composable
private fun UnfoldedContent(
    uiState: FileManagerUiState,
    onEvent: (FileManagerEvent) -> Unit,
    viewModel: FileManagerViewModel,
    dialogCallbacks: FileOpCallbacks,
    onPickFolder: () -> Unit,
    openSessions: List<dev.ori.domain.model.Session>,
    focusedSessionId: String?,
    onFocusSession: (String) -> Unit,
    onNavigateToConnections: () -> Unit,
) {
    DualPaneLayout(
        splitRatio = uiState.splitRatio,
        onSplitRatioChange = { onEvent(FileManagerEvent.UpdateSplitRatio(it)) },
        dragState = uiState.dragState,
        leftPane = {
            if (uiState.grantedTrees.isEmpty()) {
                StorageAccessEmptyState(onPickFolder = onPickFolder)
            } else {
                PaneContent(
                    paneState = uiState.leftPane,
                    pane = ActivePane.LEFT,
                    onEvent = onEvent,
                    viewModel = viewModel,
                    dialogCallbacks = dialogCallbacks,
                )
            }
        },
        rightPane = {
            RemotePaneWithHeader(
                paneState = uiState.rightPane,
                openSessions = openSessions,
                focusedSessionId = focusedSessionId,
                onFocusSession = onFocusSession,
                onNavigateToConnections = onNavigateToConnections,
                onEvent = onEvent,
                dialogCallbacks = dialogCallbacks,
                viewModel = viewModel,
            )
        },
    )
}

/**
 * PR 3 — composes the [RemotePaneHeader] dropdown on top of either a
 * [RemoteEmptyState] (when no sessions are open) or the regular
 * [PaneContent] for the RIGHT pane. Pulled into its own composable so
 * both folded and unfolded layouts get identical behaviour without
 * duplicating the header + empty-state split.
 */
@Composable
private fun RemotePaneWithHeader(
    paneState: PaneState,
    openSessions: List<dev.ori.domain.model.Session>,
    focusedSessionId: String?,
    onFocusSession: (String) -> Unit,
    onNavigateToConnections: () -> Unit,
    onEvent: (FileManagerEvent) -> Unit,
    dialogCallbacks: FileOpCallbacks,
    viewModel: FileManagerViewModel? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RemotePaneHeader(
            openSessions = openSessions,
            focusedSessionId = focusedSessionId,
            onFocusSession = onFocusSession,
        )
        if (openSessions.isEmpty()) {
            RemoteEmptyState(onNavigateToConnections = onNavigateToConnections)
        } else {
            PaneContent(
                paneState = paneState,
                pane = ActivePane.RIGHT,
                onEvent = onEvent,
                viewModel = viewModel,
                dialogCallbacks = dialogCallbacks,
            )
        }
    }
}

/**
 * PR 3 — placeholder for the right pane when no SSH session is focused
 * (fresh launch, user disconnected the last session, etc.). The CTA
 * sends the user to Connections where they can open a server.
 */
@Composable
private fun RemoteEmptyState(onNavigateToConnections: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Keine aktive Verbindung",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Öffne einen Server in der Connections-Liste.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNavigateToConnections) {
            Text("Zu Connections")
        }
    }
}

/**
 * Phase 15 Task 15.6 — first-time / revoked-grant empty state for the
 * local (LEFT) pane. Prompts the user to pick a folder with the system
 * SAF picker. Without this, a fresh install (or one where the user
 * revoked all grants in System Settings) would just show an empty list
 * and leave the user wondering what went wrong.
 */
@Composable
private fun StorageAccessEmptyState(onPickFolder: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = LucideIcons.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Keinen Ordner freigegeben",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Ori:Dev braucht deine Erlaubnis für einen Ordner, " +
                    "bevor lokale Dateien angezeigt werden können.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onPickFolder) {
                Text("Ordner auswählen")
            }
        }
    }
}

@Composable
private fun PaneContent(
    paneState: PaneState,
    pane: ActivePane,
    onEvent: (FileManagerEvent) -> Unit,
    dialogCallbacks: FileOpCallbacks,
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
        onCreateDirectory = { dialogCallbacks.onShowMkdir(pane) },
        onShowFileInfo = { file -> onEvent(FileManagerEvent.ShowFileInfo(file)) },
        onShowFilePreview = { file -> onEvent(FileManagerEvent.ShowFilePreview(pane, file)) },
        onShowContextMenu = { file -> onEvent(FileManagerEvent.ShowContextMenu(file)) },
        onRename = { file -> dialogCallbacks.onShowRename(pane, file) },
        onDelete = { file ->
            onEvent(FileManagerEvent.ToggleFileSelection(pane, file.path))
            onEvent(FileManagerEvent.DeleteSelected(pane))
        },
        onChmod = { file -> dialogCallbacks.onShowChmod(pane, file) },
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
