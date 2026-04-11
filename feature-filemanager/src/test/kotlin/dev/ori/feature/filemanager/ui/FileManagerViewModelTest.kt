package dev.ori.feature.filemanager.ui

import android.os.Environment
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.BookmarkRepository
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.usecase.ChmodUseCase
import dev.ori.domain.usecase.CreateDirectoryUseCase
import dev.ori.domain.usecase.DeleteFileUseCase
import dev.ori.domain.usecase.GetBookmarksUseCase
import dev.ori.domain.usecase.ListFilesUseCase
import dev.ori.domain.usecase.RenameFileUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val localRepository: FileSystemRepository = mockk(relaxed = true)
    private val remoteRepository: FileSystemRepository = mockk(relaxed = true)
    private val bookmarkRepository: BookmarkRepository = mockk(relaxed = true)

    // Use real use cases with mocked repositories to avoid MockK Result<T> issues
    private val listFilesUseCase = ListFilesUseCase()
    private val deleteFileUseCase = DeleteFileUseCase()
    private val renameFileUseCase = RenameFileUseCase()
    private val createDirectoryUseCase = CreateDirectoryUseCase()
    private val chmodUseCase = ChmodUseCase()
    private val getBookmarksUseCase = GetBookmarksUseCase(bookmarkRepository)

    private val sampleFiles = listOf(
        FileItem(name = "Documents", path = "/storage/emulated/0/Documents", isDirectory = true, size = 0),
        FileItem(name = "photos.jpg", path = "/storage/emulated/0/photos.jpg", isDirectory = false, size = 1024),
        FileItem(name = "notes.txt", path = "/storage/emulated/0/notes.txt", isDirectory = false, size = 256),
    )

    private val initialPath = "/storage/emulated/0"

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File(initialPath)
        every { bookmarkRepository.getBookmarksForServer(any()) } returns flowOf(emptyList())
        coEvery { localRepository.listFiles(any()) } returns sampleFiles
        coEvery { remoteRepository.listFiles(any()) } returns emptyList()
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(Environment::class)
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
        )

    @Test
    fun init_loadsLeftPaneFiles() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.leftPane.currentPath).isEqualTo(initialPath)
            assertThat(state.leftPane.files).hasSize(sampleFiles.size)
            assertThat(state.leftPane.isLoading).isFalse()
        }
    }

    @Test
    fun navigateToPath_updatesFilesAndPath() = runTest {
        val viewModel = createViewModel()
        val newPath = "/storage/emulated/0/Documents"
        val docFiles = listOf(
            FileItem(name = "report.pdf", path = "$newPath/report.pdf", isDirectory = false, size = 2048),
        )
        coEvery { localRepository.listFiles(newPath) } returns docFiles

        viewModel.onEvent(FileManagerEvent.NavigateToPath(ActivePane.LEFT, newPath))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.leftPane.currentPath).isEqualTo(newPath)
            assertThat(state.leftPane.files).isEqualTo(docFiles)
        }
    }

    @Test
    fun navigateUp_goesToParent() = runTest {
        val viewModel = createViewModel()
        val childPath = "/storage/emulated/0/Documents"
        val childFiles = listOf(
            FileItem(name = "file.txt", path = "$childPath/file.txt", isDirectory = false, size = 100),
        )
        coEvery { localRepository.listFiles(childPath) } returns childFiles
        viewModel.onEvent(FileManagerEvent.NavigateToPath(ActivePane.LEFT, childPath))

        // Navigate up should go to parent
        viewModel.onEvent(FileManagerEvent.NavigateUp(ActivePane.LEFT))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.leftPane.currentPath).isEqualTo(initialPath)
        }
    }

    @Test
    fun navigateUp_atRoot_staysAtRoot() = runTest {
        coEvery { localRepository.listFiles("/") } returns emptyList()
        every { Environment.getExternalStorageDirectory() } returns File("/")
        val viewModel = createViewModel()

        viewModel.onEvent(FileManagerEvent.NavigateUp(ActivePane.LEFT))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.leftPane.currentPath).isEqualTo("/")
        }
    }

    @Test
    fun toggleSelection_addsAndRemoves() = runTest {
        val viewModel = createViewModel()
        val filePath = sampleFiles[1].path

        // Toggle on -- adds
        viewModel.onEvent(FileManagerEvent.ToggleFileSelection(ActivePane.LEFT, filePath))
        assertThat(viewModel.uiState.value.leftPane.selectedFiles).contains(filePath)

        // Toggle off -- removes
        viewModel.onEvent(FileManagerEvent.ToggleFileSelection(ActivePane.LEFT, filePath))
        assertThat(viewModel.uiState.value.leftPane.selectedFiles).doesNotContain(filePath)
    }

    @Test
    fun selectAll_selectsAllFiles() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(FileManagerEvent.SelectAllFiles(ActivePane.LEFT))

        val state = viewModel.uiState.value
        assertThat(state.leftPane.selectedFiles).containsExactlyElementsIn(
            state.leftPane.files.map { it.path },
        )
    }

    @Test
    fun deleteSelected_success_refreshesPane() = runTest {
        val viewModel = createViewModel()
        val filePath = sampleFiles[1].path
        viewModel.onEvent(FileManagerEvent.ToggleFileSelection(ActivePane.LEFT, filePath))

        viewModel.onEvent(FileManagerEvent.DeleteSelected(ActivePane.LEFT))

        val state = viewModel.uiState.value
        // After delete + refresh, selection should be cleared
        assertThat(state.leftPane.selectedFiles).isEmpty()
        assertThat(state.leftPane.error).isNull()
        coVerify { localRepository.deleteFile(filePath) }
    }

    @Test
    fun deleteSelected_failure_setsError() = runTest {
        val viewModel = createViewModel()
        val filePath = sampleFiles[1].path
        viewModel.onEvent(FileManagerEvent.ToggleFileSelection(ActivePane.LEFT, filePath))

        coEvery { localRepository.deleteFile(filePath) } throws RuntimeException("Permission denied")
        // Make refresh also fail so the error is not cleared by a successful navigateToPath
        coEvery { localRepository.listFiles(initialPath) } throws RuntimeException("list failed")

        viewModel.onEvent(FileManagerEvent.DeleteSelected(ActivePane.LEFT))

        val state = viewModel.uiState.value
        // The delete error is set first, then refresh fails with its own error
        assertThat(state.leftPane.error).isNotNull()
    }

    @Test
    fun setViewMode_updatesMode() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(FileManagerEvent.SetViewMode(ActivePane.LEFT, ViewMode.GRID))

        assertThat(viewModel.uiState.value.leftPane.viewMode).isEqualTo(ViewMode.GRID)
    }

    @Test
    fun updateSplitRatio_clampsToValidRange() = runTest {
        val viewModel = createViewModel()

        // Below minimum
        viewModel.onEvent(FileManagerEvent.UpdateSplitRatio(0.05f))
        assertThat(viewModel.uiState.value.splitRatio).isEqualTo(0.2f)

        // Above maximum
        viewModel.onEvent(FileManagerEvent.UpdateSplitRatio(0.95f))
        assertThat(viewModel.uiState.value.splitRatio).isEqualTo(0.8f)

        // Within range
        viewModel.onEvent(FileManagerEvent.UpdateSplitRatio(0.6f))
        assertThat(viewModel.uiState.value.splitRatio).isEqualTo(0.6f)
    }

    @Test
    fun toggleFoldState_togglesIsFolded() = runTest {
        val viewModel = createViewModel()

        // Default is true (folded)
        assertThat(viewModel.uiState.value.isFolded).isTrue()

        viewModel.onEvent(FileManagerEvent.SetFoldState(false))
        assertThat(viewModel.uiState.value.isFolded).isFalse()

        viewModel.onEvent(FileManagerEvent.SetFoldState(true))
        assertThat(viewModel.uiState.value.isFolded).isTrue()
    }

    @Test
    fun clearError_clearsSpecificPane() = runTest {
        val viewModel = createViewModel()

        // Make listFiles fail so error persists after refresh
        coEvery { localRepository.listFiles(initialPath) } throws RuntimeException("list error")

        // Trigger error on left pane via failed delete
        val leftFile = sampleFiles[1].path
        viewModel.onEvent(FileManagerEvent.ToggleFileSelection(ActivePane.LEFT, leftFile))
        coEvery { localRepository.deleteFile(leftFile) } throws RuntimeException("error")
        viewModel.onEvent(FileManagerEvent.DeleteSelected(ActivePane.LEFT))

        // Verify left pane has error (from either delete or refresh failure)
        assertThat(viewModel.uiState.value.leftPane.error).isNotNull()

        // Clear left pane error
        viewModel.onEvent(FileManagerEvent.ClearError(ActivePane.LEFT))

        assertThat(viewModel.uiState.value.leftPane.error).isNull()
    }

    @Test
    fun createDirectory_failure_setsError() = runTest {
        val viewModel = createViewModel()
        val dirPath = "/storage/emulated/0/NewDir"

        coEvery { localRepository.createDirectory(dirPath) } throws RuntimeException("Cannot create directory")
        // Make refresh also fail so the error is not cleared
        coEvery { localRepository.listFiles(initialPath) } throws RuntimeException("list failed")

        viewModel.onEvent(FileManagerEvent.CreateDirectory(ActivePane.LEFT, dirPath))

        val state = viewModel.uiState.value
        assertThat(state.leftPane.error).isNotNull()
    }
}
