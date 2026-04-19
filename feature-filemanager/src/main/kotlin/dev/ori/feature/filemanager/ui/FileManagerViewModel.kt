package dev.ori.feature.filemanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.result.getAppError
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.repository.LocalFileSystem
import dev.ori.domain.repository.RemoteFileSystem
import dev.ori.domain.repository.StorageAccessRepository
import dev.ori.domain.usecase.ChmodUseCase
import dev.ori.domain.usecase.CreateDirectoryUseCase
import dev.ori.domain.usecase.DeleteFileUseCase
import dev.ori.domain.usecase.EnqueueTransferUseCase
import dev.ori.domain.usecase.GetBookmarksUseCase
import dev.ori.domain.usecase.ListFilesUseCase
import dev.ori.domain.usecase.RenameFileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val MAX_PATH_STACK_SIZE = 50

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    @LocalFileSystem private val localRepository: FileSystemRepository,
    @RemoteFileSystem private val remoteRepository: FileSystemRepository,
    private val listFilesUseCase: ListFilesUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val chmodUseCase: ChmodUseCase,
    private val getBookmarksUseCase: GetBookmarksUseCase,
    private val enqueueTransferUseCase: EnqueueTransferUseCase,
    private val storageAccessRepository: StorageAccessRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        // Phase 15 Task 15.6 — no more Environment.getExternalStorageDirectory.
        // The local pane stays empty until the user has granted at least
        // one SAF tree; the UI shows a "Choose a folder" CTA in that
        // case. The first granted tree auto-opens so existing muscle
        // memory ("File Manager → immediately see files") still applies
        // once setup is done.
        observeGrantedTrees()
        loadBookmarks()
    }

    private fun observeGrantedTrees() {
        viewModelScope.launch {
            storageAccessRepository.grantedTrees.collect { trees ->
                val wasEmpty = _uiState.value.grantedTrees.isEmpty()
                _uiState.update { it.copy(grantedTrees = trees) }

                val leftPath = _uiState.value.leftPane.currentPath
                val firstTreeUri = trees.firstOrNull()?.uri
                when {
                    // First grant after empty state — auto-open the tree.
                    wasEmpty && trees.isNotEmpty() && firstTreeUri != null && leftPath.isEmpty() -> {
                        navigateToPath(ActivePane.LEFT, firstTreeUri)
                    }
                    // All grants removed — clear the left pane.
                    trees.isEmpty() && leftPath.isNotEmpty() -> {
                        updatePaneState(ActivePane.LEFT) {
                            PaneState(currentPath = "", files = emptyList())
                        }
                    }
                    // Granted tree the user is currently viewing was revoked
                    // externally — clear the pane so we don't keep showing
                    // stale listings.
                    leftPath.isNotEmpty() && trees.none { leftPath.startsWith(it.uri) } -> {
                        updatePaneState(ActivePane.LEFT) {
                            PaneState(
                                currentPath = "",
                                files = emptyList(),
                                error = "This folder is no longer accessible. Pick another folder or re-grant.",
                            )
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: FileManagerEvent) {
        when (event) {
            is FileManagerEvent.NavigateToPath -> navigateToPath(event.pane, event.path)
            is FileManagerEvent.NavigateUp -> navigateUp(event.pane)
            is FileManagerEvent.ToggleFileSelection -> toggleFileSelection(event.pane, event.filePath)
            is FileManagerEvent.SelectAllFiles -> selectAllFiles(event.pane)
            is FileManagerEvent.ClearSelection -> clearSelection(event.pane)
            is FileManagerEvent.DeleteSelected -> deleteSelected(event.pane)
            is FileManagerEvent.RenameFile -> renameFile(event.pane, event.oldPath, event.newPath)
            is FileManagerEvent.CreateDirectory -> createDirectory(event.pane, event.path)
            is FileManagerEvent.Chmod -> chmod(event.pane, event.path, event.permissions)
            is FileManagerEvent.ShowFileInfo -> _uiState.update { it.copy(showFileInfo = event.file) }
            is FileManagerEvent.ShowContextMenu -> _uiState.update { it.copy(contextMenuFile = event.file) }
            is FileManagerEvent.SetViewMode -> setViewMode(event.pane, event.mode)
            is FileManagerEvent.SetActivePane -> _uiState.update { it.copy(activePane = event.pane) }
            is FileManagerEvent.UpdateSplitRatio -> updateSplitRatio(event.ratio)
            is FileManagerEvent.SetFoldState -> _uiState.update { it.copy(isFolded = event.isFolded) }
            is FileManagerEvent.RefreshPane -> refreshPane(event.pane)
            is FileManagerEvent.ClearError -> clearError(event.pane)
            is FileManagerEvent.AddBookmark -> { /* Bookmark persistence -- deferred */ }
            is FileManagerEvent.RemoveBookmark -> { /* Bookmark persistence -- deferred */ }
            is FileManagerEvent.InitiateTransfer -> initiateTransfer(event.sourcePaths, event.sourcePane)
            is FileManagerEvent.ShowFilePreview -> showFilePreview(event.pane, event.file)
            is FileManagerEvent.ClosePreview -> _uiState.update {
                it.copy(
                    previewFile = null,
                    previewContent = null,
                    previewLoading = false,
                    previewError = null,
                )
            }
            is FileManagerEvent.GrantTree -> grantTree(event.uri)
            is FileManagerEvent.RevokeTree -> revokeTree(event.uri)
        }
    }

    private fun grantTree(uri: String) {
        viewModelScope.launch {
            storageAccessRepository.grant(uri)
        }
    }

    private fun revokeTree(uri: String) {
        viewModelScope.launch {
            storageAccessRepository.revoke(uri)
        }
    }

    private fun showFilePreview(pane: ActivePane, file: dev.ori.domain.model.FileItem) {
        _uiState.update {
            it.copy(
                previewFile = file,
                previewPane = pane,
                previewContent = null,
                previewLoading = true,
                previewError = null,
            )
        }
        viewModelScope.launch {
            val result = runCatching { getRepository(pane).getFileContent(file.path) }
            result.fold(
                onSuccess = { bytes ->
                    val capped = if (bytes.size > MAX_PREVIEW_BYTES) bytes.copyOf(MAX_PREVIEW_BYTES) else bytes
                    _uiState.update {
                        it.copy(
                            previewLoading = false,
                            previewContent = capped.toString(Charsets.UTF_8),
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            previewLoading = false,
                            previewError = err.message ?: "Failed to load preview",
                        )
                    }
                },
            )
        }
    }

    companion object {
        private const val MAX_PREVIEW_BYTES = 100_000
    }

    private fun getRepository(pane: ActivePane): FileSystemRepository =
        when (pane) {
            ActivePane.LEFT -> localRepository
            ActivePane.RIGHT -> remoteRepository
        }

    private fun navigateToPath(pane: ActivePane, path: String) {
        if (path.isEmpty()) return
        viewModelScope.launch {
            updatePaneState(pane) { it.copy(isLoading = true, error = null) }

            val result = listFilesUseCase(getRepository(pane), path)

            result.fold(
                onSuccess = { files ->
                    updatePaneState(pane) { current ->
                        val newStack = (current.pathStack + path).takeLast(MAX_PATH_STACK_SIZE)
                        current.copy(
                            currentPath = path,
                            files = files,
                            selectedFiles = emptySet(),
                            isLoading = false,
                            pathStack = newStack,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    val appError = result.getAppError()
                    updatePaneState(pane) {
                        it.copy(
                            isLoading = false,
                            error = appError?.message ?: error.message ?: "Failed to list files",
                        )
                    }
                },
            )
        }
    }

    private fun navigateUp(pane: ActivePane) {
        val currentPath = getPaneState(pane).currentPath
        if (pane == ActivePane.LEFT) {
            // SAF has no parent traversal above the granted tree root.
            // Pop the stack instead; if we're already at a tree root,
            // no-op.
            val stack = getPaneState(pane).pathStack
            if (stack.size < 2) return
            val previousPath = stack[stack.lastIndex - 1]
            updatePaneState(pane) { current ->
                current.copy(pathStack = current.pathStack.dropLast(2))
            }
            navigateToPath(pane, previousPath)
            return
        }
        val parentPath = File(currentPath).parent ?: "/"
        if (parentPath != currentPath) {
            updatePaneState(pane) { current ->
                current.copy(pathStack = current.pathStack.dropLast(1))
            }
            navigateToPath(pane, parentPath)
        }
    }

    private fun toggleFileSelection(pane: ActivePane, filePath: String) {
        updatePaneState(pane) { current ->
            val newSelection = if (filePath in current.selectedFiles) {
                current.selectedFiles - filePath
            } else {
                current.selectedFiles + filePath
            }
            current.copy(selectedFiles = newSelection)
        }
    }

    private fun selectAllFiles(pane: ActivePane) {
        updatePaneState(pane) { current ->
            current.copy(selectedFiles = current.files.map { it.path }.toSet())
        }
    }

    private fun clearSelection(pane: ActivePane) {
        updatePaneState(pane) { current ->
            current.copy(selectedFiles = emptySet())
        }
    }

    private fun deleteSelected(pane: ActivePane) {
        val selected = getPaneState(pane).selectedFiles
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val repository = getRepository(pane)
            var failureCount = 0

            for (filePath in selected) {
                val result = deleteFileUseCase(repository, filePath)
                if (result.isFailure) {
                    failureCount++
                }
            }

            if (failureCount > 0) {
                updatePaneState(pane) {
                    it.copy(error = "Failed to delete $failureCount of ${selected.size} files")
                }
            }

            refreshPane(pane)
        }
    }

    private fun renameFile(pane: ActivePane, oldPath: String, newPath: String) {
        viewModelScope.launch {
            val result = renameFileUseCase(getRepository(pane), oldPath, newPath)
            result.getAppError()?.let { error ->
                updatePaneState(pane) { it.copy(error = error.message) }
            }
            refreshPane(pane)
        }
    }

    private fun createDirectory(pane: ActivePane, path: String) {
        viewModelScope.launch {
            val result = createDirectoryUseCase(getRepository(pane), path)
            result.getAppError()?.let { error ->
                updatePaneState(pane) { it.copy(error = error.message) }
            }
            refreshPane(pane)
        }
    }

    private fun chmod(pane: ActivePane, path: String, permissions: String) {
        viewModelScope.launch {
            val result = chmodUseCase(getRepository(pane), path, permissions)
            result.getAppError()?.let { error ->
                updatePaneState(pane) { it.copy(error = error.message) }
            }
            refreshPane(pane)
        }
    }

    private fun setViewMode(pane: ActivePane, mode: ViewMode) {
        updatePaneState(pane) { it.copy(viewMode = mode) }
    }

    private fun updateSplitRatio(ratio: Float) {
        _uiState.update { it.copy(splitRatio = ratio.coerceIn(0.2f, 0.8f)) }
    }

    private fun refreshPane(pane: ActivePane) {
        val currentPath = getPaneState(pane).currentPath
        if (currentPath.isNotEmpty()) {
            navigateToPath(pane, currentPath)
        }
    }

    private fun clearError(pane: ActivePane) {
        updatePaneState(pane) { it.copy(error = null) }
    }

    private fun initiateTransfer(sourcePaths: List<String>, sourcePane: ActivePane) {
        if (sourcePaths.isEmpty()) return

        val direction = when (sourcePane) {
            ActivePane.LEFT -> TransferDirection.UPLOAD
            ActivePane.RIGHT -> TransferDirection.DOWNLOAD
        }

        val destinationPath = when (sourcePane) {
            ActivePane.LEFT -> _uiState.value.rightPane.currentPath
            ActivePane.RIGHT -> _uiState.value.leftPane.currentPath
        }

        viewModelScope.launch {
            for (path in sourcePaths) {
                val fileName = path.substringAfterLast('/')
                val destFullPath = "$destinationPath/$fileName"
                val request = TransferRequest(
                    serverProfileId = 1L,
                    sourcePath = path,
                    destinationPath = destFullPath,
                    direction = direction,
                )
                enqueueTransferUseCase(request)
            }

            val count = sourcePaths.size
            _uiState.update {
                it.copy(
                    transferSnackbar = "$count transfer${if (count > 1) "s" else ""} queued",
                    dragState = DragState(),
                )
            }
        }
    }

    fun setDragState(dragState: DragState) {
        _uiState.update { it.copy(dragState = dragState) }
    }

    fun clearTransferSnackbar() {
        _uiState.update { it.copy(transferSnackbar = null) }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            getBookmarksUseCase(serverId = null)
                .catch { /* Silently ignore bookmark loading errors */ }
                .collect { bookmarks ->
                    _uiState.update { it.copy(bookmarks = bookmarks) }
                }
        }
    }

    private fun getPaneState(pane: ActivePane): PaneState =
        when (pane) {
            ActivePane.LEFT -> _uiState.value.leftPane
            ActivePane.RIGHT -> _uiState.value.rightPane
        }

    private fun updatePaneState(pane: ActivePane, transform: (PaneState) -> PaneState) {
        _uiState.update { state ->
            when (pane) {
                ActivePane.LEFT -> state.copy(leftPane = transform(state.leftPane))
                ActivePane.RIGHT -> state.copy(rightPane = transform(state.rightPane))
            }
        }
    }
}
