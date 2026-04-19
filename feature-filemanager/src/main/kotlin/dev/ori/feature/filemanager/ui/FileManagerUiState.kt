package dev.ori.feature.filemanager.ui

import dev.ori.domain.model.Bookmark
import dev.ori.domain.model.FileItem
import dev.ori.domain.model.GrantedTree

enum class ViewMode {
    LIST,
    GRID,
}

enum class ActivePane {
    LEFT,
    RIGHT,
}

data class PaneState(
    val currentPath: String = "",
    val files: List<FileItem> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pathStack: List<String> = emptyList(),
    val isRemote: Boolean = false,
    val serverName: String? = null,
    val viewMode: ViewMode = ViewMode.LIST,
)

data class FileManagerUiState(
    val leftPane: PaneState = PaneState(),
    val rightPane: PaneState = PaneState(isRemote = true),
    val isFolded: Boolean = true,
    val activePane: ActivePane = ActivePane.LEFT,
    val bookmarks: List<Bookmark> = emptyList(),
    // Phase 15 Task 15.6 — SAF granted trees. The local (LEFT) pane shows
    // a "Choose a folder" CTA when this list is empty so the user is
    // never left staring at a blank listing wondering why nothing works.
    val grantedTrees: List<GrantedTree> = emptyList(),
    val splitRatio: Float = 0.5f,
    val showFileInfo: FileItem? = null,
    val contextMenuFile: FileItem? = null,
    val dragState: DragState = DragState(),
    val transferSnackbar: String? = null,
    val previewFile: FileItem? = null,
    val previewPane: ActivePane = ActivePane.LEFT,
    val previewContent: String? = null,
    val previewLoading: Boolean = false,
    val previewError: String? = null,
)

sealed class FileManagerEvent {
    data class NavigateToPath(val pane: ActivePane, val path: String) : FileManagerEvent()
    data class NavigateUp(val pane: ActivePane) : FileManagerEvent()
    data class ToggleFileSelection(val pane: ActivePane, val filePath: String) : FileManagerEvent()
    data class SelectAllFiles(val pane: ActivePane) : FileManagerEvent()
    data class ClearSelection(val pane: ActivePane) : FileManagerEvent()
    data class DeleteSelected(val pane: ActivePane) : FileManagerEvent()
    data class RenameFile(val pane: ActivePane, val oldPath: String, val newPath: String) : FileManagerEvent()
    data class CreateDirectory(val pane: ActivePane, val path: String) : FileManagerEvent()
    data class Chmod(val pane: ActivePane, val path: String, val permissions: String) : FileManagerEvent()
    data class ShowFileInfo(val file: FileItem?) : FileManagerEvent()
    data class ShowContextMenu(val file: FileItem?) : FileManagerEvent()
    data class SetViewMode(val pane: ActivePane, val mode: ViewMode) : FileManagerEvent()
    data class SetActivePane(val pane: ActivePane) : FileManagerEvent()
    data class UpdateSplitRatio(val ratio: Float) : FileManagerEvent()
    data class SetFoldState(val isFolded: Boolean) : FileManagerEvent()
    data class RefreshPane(val pane: ActivePane) : FileManagerEvent()
    data class ClearError(val pane: ActivePane) : FileManagerEvent()
    data class AddBookmark(val bookmark: Bookmark) : FileManagerEvent()
    data class RemoveBookmark(val bookmark: Bookmark) : FileManagerEvent()
    data class InitiateTransfer(val sourcePaths: List<String>, val sourcePane: ActivePane) : FileManagerEvent()
    data class ShowFilePreview(val pane: ActivePane, val file: FileItem) : FileManagerEvent()
    data object ClosePreview : FileManagerEvent()

    // Phase 15 Task 15.6 — SAF integration.
    /** Persist the tree URI returned by the system folder picker. */
    data class GrantTree(val uri: String) : FileManagerEvent()

    /** Release a persisted SAF grant (invoked from Settings → Storage Access). */
    data class RevokeTree(val uri: String) : FileManagerEvent()
}

data class DragState(
    val isDragging: Boolean = false,
    val sourcePane: ActivePane? = null,
    val draggedPaths: List<String> = emptyList(),
)
