package dev.ori.feature.filemanager.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.domain.model.FileItem
import dev.ori.domain.model.GrantedTree
import dev.ori.domain.repository.BookmarkRepository
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.repository.StorageAccessRepository
import dev.ori.domain.usecase.ChmodUseCase
import dev.ori.domain.usecase.CreateDirectoryUseCase
import dev.ori.domain.usecase.DeleteFileUseCase
import dev.ori.domain.usecase.EnqueueTransferUseCase
import dev.ori.domain.usecase.GetBookmarksUseCase
import dev.ori.domain.usecase.ListFilesUseCase
import dev.ori.domain.usecase.RenameFileUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Phase 15 Task 15.6 — the ViewModel no longer navigates to
 * `Environment.getExternalStorageDirectory()` on init. The local pane
 * stays empty until [StorageAccessRepository] emits at least one
 * granted tree. Tests reflect that new contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val localRepository: FileSystemRepository = mockk(relaxed = true)
    private val remoteRepository: FileSystemRepository = mockk(relaxed = true)
    private val bookmarkRepository: BookmarkRepository = mockk(relaxed = true)
    private val storageAccessRepository: StorageAccessRepository = mockk(relaxed = true)
    private val grantedTreesFlow = MutableStateFlow<List<GrantedTree>>(emptyList())

    // Use real use cases with mocked repositories to avoid MockK Result<T> issues
    private val listFilesUseCase = ListFilesUseCase()
    private val deleteFileUseCase = DeleteFileUseCase()
    private val renameFileUseCase = RenameFileUseCase()
    private val createDirectoryUseCase = CreateDirectoryUseCase()
    private val chmodUseCase = ChmodUseCase()
    private val getBookmarksUseCase = GetBookmarksUseCase(bookmarkRepository)
    private val enqueueTransferUseCase: EnqueueTransferUseCase = mockk(relaxed = true)

    private val sampleTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
    private val sampleTree = GrantedTree(
        uri = sampleTreeUri,
        displayName = "Documents",
        documentId = "primary:Documents",
    )

    private val sampleFiles = listOf(
        FileItem(name = "Reports", path = "$sampleTreeUri/document/a", isDirectory = true, size = 0),
        FileItem(name = "photos.jpg", path = "$sampleTreeUri/document/b", isDirectory = false, size = 1024),
        FileItem(name = "notes.txt", path = "$sampleTreeUri/document/c", isDirectory = false, size = 256),
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { bookmarkRepository.getBookmarksForServer(any()) } returns flowOf(emptyList())
        every { storageAccessRepository.grantedTrees } returns grantedTreesFlow
        coEvery { localRepository.listFiles(any()) } returns sampleFiles
        coEvery { remoteRepository.listFiles(any()) } returns emptyList()
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): FileManagerViewModel =
        FileManagerViewModel(
            localRepository = localRepository,
            remoteRepository = remoteRepository,
            listFilesUseCase = listFilesUseCase,
            deleteFileUseCase = deleteFileUseCase,
            renameFileUseCase = renameFileUseCase,
            createDirectoryUseCase = createDirectoryUseCase,
            chmodUseCase = chmodUseCase,
            getBookmarksUseCase = getBookmarksUseCase,
            enqueueTransferUseCase = enqueueTransferUseCase,
            storageAccessRepository = storageAccessRepository,
        )

    @Test
    fun init_withoutGrantedTrees_leavesLeftPaneEmpty() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.grantedTrees).isEmpty()
            assertThat(state.leftPane.currentPath).isEmpty()
            assertThat(state.leftPane.files).isEmpty()
        }
    }

    @Test
    fun grantedTreeEmission_autoOpensFirstTree() = runTest {
        val viewModel = createViewModel()

        grantedTreesFlow.value = listOf(sampleTree)

        val state = viewModel.uiState.value
        assertThat(state.grantedTrees).containsExactly(sampleTree)
        assertThat(state.leftPane.currentPath).isEqualTo(sampleTreeUri)
        assertThat(state.leftPane.files).hasSize(sampleFiles.size)
    }

    @Test
    fun grantedTreesCleared_resetsLeftPane() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        // Sanity — first emission opens the tree
        assertThat(viewModel.uiState.value.leftPane.currentPath).isEqualTo(sampleTreeUri)

        grantedTreesFlow.value = emptyList()

        val state = viewModel.uiState.value
        assertThat(state.grantedTrees).isEmpty()
        assertThat(state.leftPane.currentPath).isEmpty()
        assertThat(state.leftPane.files).isEmpty()
    }

    @Test
    fun externalRevocation_showsReGrantError() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        // The current path is inside the tree; simulate the user revoking
        // a DIFFERENT tree from System Settings while keeping this one…
        // actually simulate the opposite: our tree is no longer in the
        // granted list and current path no longer starts with any grant.
        grantedTreesFlow.value = listOf(
            GrantedTree(
                uri = "content://com.android.externalstorage.documents/tree/primary%3AOther",
                displayName = "Other",
                documentId = "primary:Other",
            ),
        )

        val state = viewModel.uiState.value
        // Pane is cleared with the "re-grant or remove" inline error.
        assertThat(state.leftPane.currentPath).isEmpty()
        assertThat(state.leftPane.error).isNotNull()
        assertThat(state.leftPane.error).contains("no longer accessible")
    }

    @Test
    fun grantTree_event_delegatesToRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(FileManagerEvent.GrantTree(sampleTreeUri))

        coVerify { storageAccessRepository.grant(sampleTreeUri) }
    }

    @Test
    fun revokeTree_event_delegatesToRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(FileManagerEvent.RevokeTree(sampleTreeUri))

        coVerify { storageAccessRepository.revoke(sampleTreeUri) }
    }

    @Test
    fun navigateToPath_updatesFilesAndPath() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        val childUri = "$sampleTreeUri/document/subdir"
        val childFiles = listOf(
            FileItem(name = "report.pdf", path = "$childUri/report.pdf", isDirectory = false, size = 2048),
        )
        coEvery { localRepository.listFiles(childUri) } returns childFiles

        viewModel.onEvent(FileManagerEvent.NavigateToPath(ActivePane.LEFT, childUri))

        val state = viewModel.uiState.value
        assertThat(state.leftPane.currentPath).isEqualTo(childUri)
        assertThat(state.leftPane.files).isEqualTo(childFiles)
    }

    @Test
    fun navigateUp_fromChild_popsPathStack() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        // After init, pathStack = [sampleTreeUri]. Navigate into a child.
        val childUri = "$sampleTreeUri/document/child"
        coEvery { localRepository.listFiles(childUri) } returns emptyList()
        viewModel.onEvent(FileManagerEvent.NavigateToPath(ActivePane.LEFT, childUri))
        assertThat(viewModel.uiState.value.leftPane.currentPath).isEqualTo(childUri)

        viewModel.onEvent(FileManagerEvent.NavigateUp(ActivePane.LEFT))

        val state = viewModel.uiState.value
        assertThat(state.leftPane.currentPath).isEqualTo(sampleTreeUri)
    }

    @Test
    fun navigateUp_atTreeRoot_isNoOp() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        val before = viewModel.uiState.value.leftPane.currentPath

        viewModel.onEvent(FileManagerEvent.NavigateUp(ActivePane.LEFT))

        assertThat(viewModel.uiState.value.leftPane.currentPath).isEqualTo(before)
    }

    @Test
    fun toggleSelection_addsAndRemoves() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        val filePath = sampleFiles[1].path

        viewModel.onEvent(FileManagerEvent.ToggleFileSelection(ActivePane.LEFT, filePath))
        assertThat(viewModel.uiState.value.leftPane.selectedFiles).contains(filePath)

        viewModel.onEvent(FileManagerEvent.ToggleFileSelection(ActivePane.LEFT, filePath))
        assertThat(viewModel.uiState.value.leftPane.selectedFiles).doesNotContain(filePath)
    }

    @Test
    fun selectAll_selectsAllFiles() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()

        viewModel.onEvent(FileManagerEvent.SelectAllFiles(ActivePane.LEFT))

        val state = viewModel.uiState.value
        assertThat(state.leftPane.selectedFiles).containsExactlyElementsIn(
            state.leftPane.files.map { it.path },
        )
    }

    @Test
    fun deleteSelected_success_refreshesPane() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        val filePath = sampleFiles[1].path
        viewModel.onEvent(FileManagerEvent.ToggleFileSelection(ActivePane.LEFT, filePath))

        viewModel.onEvent(FileManagerEvent.DeleteSelected(ActivePane.LEFT))

        val state = viewModel.uiState.value
        assertThat(state.leftPane.selectedFiles).isEmpty()
        assertThat(state.leftPane.error).isNull()
        coVerify { localRepository.deleteFile(filePath) }
    }

    @Test
    fun setViewMode_updatesMode() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()

        viewModel.onEvent(FileManagerEvent.SetViewMode(ActivePane.LEFT, ViewMode.GRID))

        assertThat(viewModel.uiState.value.leftPane.viewMode).isEqualTo(ViewMode.GRID)
    }

    @Test
    fun updateSplitRatio_clampsToValidRange() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(FileManagerEvent.UpdateSplitRatio(0.05f))
        assertThat(viewModel.uiState.value.splitRatio).isEqualTo(0.2f)

        viewModel.onEvent(FileManagerEvent.UpdateSplitRatio(0.95f))
        assertThat(viewModel.uiState.value.splitRatio).isEqualTo(0.8f)

        viewModel.onEvent(FileManagerEvent.UpdateSplitRatio(0.6f))
        assertThat(viewModel.uiState.value.splitRatio).isEqualTo(0.6f)
    }

    @Test
    fun toggleFoldState_togglesIsFolded() = runTest {
        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value.isFolded).isTrue()

        viewModel.onEvent(FileManagerEvent.SetFoldState(false))
        assertThat(viewModel.uiState.value.isFolded).isFalse()

        viewModel.onEvent(FileManagerEvent.SetFoldState(true))
        assertThat(viewModel.uiState.value.isFolded).isTrue()
    }

    @Test
    fun showFilePreview_success_setsContent() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        val file = sampleFiles[1]
        coEvery { localRepository.getFileContent(file.path) } returns "preview bytes".toByteArray()

        viewModel.onEvent(FileManagerEvent.ShowFilePreview(ActivePane.LEFT, file))

        val state = viewModel.uiState.value
        assertThat(state.previewFile).isEqualTo(file)
        assertThat(state.previewContent).isEqualTo("preview bytes")
        assertThat(state.previewLoading).isFalse()
        assertThat(state.previewError).isNull()
    }

    @Test
    fun closePreview_clearsPreviewState() = runTest {
        grantedTreesFlow.value = listOf(sampleTree)
        val viewModel = createViewModel()
        val file = sampleFiles[1]
        coEvery { localRepository.getFileContent(file.path) } returns "bytes".toByteArray()
        viewModel.onEvent(FileManagerEvent.ShowFilePreview(ActivePane.LEFT, file))
        assertThat(viewModel.uiState.value.previewFile).isNotNull()

        viewModel.onEvent(FileManagerEvent.ClosePreview)

        val state = viewModel.uiState.value
        assertThat(state.previewFile).isNull()
        assertThat(state.previewContent).isNull()
    }
}
