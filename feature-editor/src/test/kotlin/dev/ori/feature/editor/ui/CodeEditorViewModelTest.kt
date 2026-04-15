package dev.ori.feature.editor.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.feature.FeatureGateManager
import dev.ori.core.common.feature.PremiumFeature
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.repository.LineDiffProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodeEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val localRepo: FileSystemRepository = mockk(relaxed = true)
    private val remoteRepo: FileSystemRepository = mockk(relaxed = true)
    private val featureGate: FeatureGateManager = mockk(relaxed = true)
    private val lineDiffProvider: LineDiffProvider = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { featureGate.isFeatureEnabled(PremiumFeature.CODE_EDITOR_WRITE) } returns true
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun savedState(path: String, isRemote: Boolean): SavedStateHandle =
        SavedStateHandle(mapOf("filePath" to path, "isRemote" to isRemote))

    private fun createViewModel(
        path: String = "/tmp/test.kt",
        isRemote: Boolean = false,
    ): CodeEditorViewModel = CodeEditorViewModel(
        localRepository = localRepo,
        remoteRepository = remoteRepo,
        featureGateManager = featureGate,
        lineDiffProvider = lineDiffProvider,
        savedStateHandle = savedState(path, isRemote),
    )

    @Test
    fun init_localFile_loadsContent() = runTest {
        coEvery { localRepo.getFileContent("/tmp/test.kt") } returns "fun main() {}".toByteArray()

        val vm = createViewModel()

        val state = vm.uiState.value
        assertThat(state.tabs).hasSize(1)
        assertThat(state.tabs[0].content).isEqualTo("fun main() {}")
        assertThat(state.tabs[0].isRemote).isFalse()
    }

    @Test
    fun init_remoteFile_loadsContent() = runTest {
        coEvery { remoteRepo.getFileContent("/srv/app.py") } returns "print('hi')".toByteArray()

        val vm = createViewModel(path = "/srv/app.py", isRemote = true)

        val state = vm.uiState.value
        assertThat(state.tabs).hasSize(1)
        assertThat(state.tabs[0].content).isEqualTo("print('hi')")
        assertThat(state.tabs[0].isRemote).isTrue()
    }

    @Test
    fun contentChanged_updatesActiveTab() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "original".toByteArray()
        val vm = createViewModel()

        vm.onEvent(CodeEditorEvent.ContentChanged("new content"))

        val tab = vm.uiState.value.activeTab!!
        assertThat(tab.content).isEqualTo("new content")
        assertThat(tab.isDirty).isTrue()
    }

    @Test
    fun save_success_clearsDirty() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "orig".toByteArray()
        coEvery { localRepo.writeFileContent(any(), any()) } returns Unit
        val vm = createViewModel()

        vm.onEvent(CodeEditorEvent.ContentChanged("modified"))
        vm.onEvent(CodeEditorEvent.Save)

        val tab = vm.uiState.value.activeTab!!
        assertThat(tab.isDirty).isFalse()
        assertThat(tab.originalContent).isEqualTo("modified")
    }

    @Test
    fun save_failure_setsError() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "orig".toByteArray()
        coEvery { localRepo.writeFileContent(any(), any()) } throws RuntimeException("disk full")
        val vm = createViewModel()

        vm.onEvent(CodeEditorEvent.ContentChanged("modified"))
        vm.onEvent(CodeEditorEvent.Save)

        assertThat(vm.uiState.value.error).contains("Save failed")
    }

    @Test
    fun toggleSearch_showsSearchBar() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "hello".toByteArray()
        val vm = createViewModel()

        assertThat(vm.uiState.value.searchVisible).isFalse()
        vm.onEvent(CodeEditorEvent.ToggleSearch)
        assertThat(vm.uiState.value.searchVisible).isTrue()
    }

    @Test
    fun setSearchQuery_updatesState() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "hello world, hello".toByteArray()
        val vm = createViewModel()

        vm.onEvent(CodeEditorEvent.SetSearchQuery("hello"))

        val state = vm.uiState.value
        assertThat(state.searchQuery).isEqualTo("hello")
        assertThat(state.matchCount).isEqualTo(2)
    }

    @Test
    fun switchTab_updatesActiveIndex() = runTest {
        coEvery { localRepo.getFileContent("/tmp/test.kt") } returns "a".toByteArray()
        coEvery { localRepo.getFileContent("/tmp/other.kt") } returns "b".toByteArray()
        val vm = createViewModel()

        vm.onEvent(CodeEditorEvent.OpenFile("/tmp/other.kt", isRemote = false))
        assertThat(vm.uiState.value.tabs).hasSize(2)
        assertThat(vm.uiState.value.activeTabIndex).isEqualTo(1)

        vm.onEvent(CodeEditorEvent.SwitchTab(0))

        assertThat(vm.uiState.value.activeTabIndex).isEqualTo(0)
    }

    @Test
    fun readOnlyMode_blocksContentChanges() = runTest {
        every { featureGate.isFeatureEnabled(PremiumFeature.CODE_EDITOR_WRITE) } returns false
        coEvery { localRepo.getFileContent(any()) } returns "orig".toByteArray()
        val vm = createViewModel()

        vm.onEvent(CodeEditorEvent.ContentChanged("attempted modify"))

        assertThat(vm.uiState.value.activeTab!!.content).isEqualTo("orig")
        assertThat(vm.uiState.value.isReadOnly).isTrue()
    }

    @Test
    fun replaceAll_replacesOccurrences() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "foo bar foo".toByteArray()
        val vm = createViewModel()

        vm.onEvent(CodeEditorEvent.SetSearchQuery("foo"))
        vm.onEvent(CodeEditorEvent.SetReplaceQuery("baz"))
        vm.onEvent(CodeEditorEvent.ReplaceAll)

        assertThat(vm.uiState.value.activeTab!!.content).isEqualTo("baz bar baz")
    }

    // --- Phase 11 P4.5 — large-file / binary-file guards ---

    @Test
    fun init_fileAboveSizeLimit_setsErrorAndSkipsContent() = runTest {
        // 10 MB + 1 byte — just over the hard limit.
        val oversized = ByteArray(10 * 1024 * 1024 + 1) { 'a'.code.toByte() }
        coEvery { localRepo.getFileContent(any()) } returns oversized

        val vm = createViewModel()

        val tab = vm.uiState.value.activeTab!!
        assertThat(tab.content).isEmpty()
        assertThat(tab.error).contains("editor limit")
        assertThat(vm.uiState.value.error).contains("Cannot open")
    }

    @Test
    fun init_binaryFile_setsErrorAndSkipsContent() = runTest {
        // Prepend a NULL byte to trip the binary sniff.
        val binary = byteArrayOf(0x00, 0x01, 0x02, 0x03) + "trailing text".toByteArray()
        coEvery { localRepo.getFileContent(any()) } returns binary

        val vm = createViewModel()

        val tab = vm.uiState.value.activeTab!!
        assertThat(tab.content).isEmpty()
        assertThat(tab.error).contains("Binary")
    }

    // --- Phase 11 P4.3 — remote file picker events ---

    private fun fileItem(name: String, isDirectory: Boolean, parent: String = "/storage/emulated/0"): FileItem =
        FileItem(name = name, path = "$parent/$name", isDirectory = isDirectory)

    @Test
    fun showPicker_localFileSystem_loadsEntriesFromLocalRepo() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "x".toByteArray()
        val entries = listOf(
            fileItem("zeta", isDirectory = true),
            fileItem("Alpha", isDirectory = true),
            fileItem("readme.md", isDirectory = false),
        )
        coEvery { localRepo.listFiles("/storage/emulated/0") } returns entries

        val vm = createViewModel()
        vm.onEvent(CodeEditorEvent.ShowPicker(isRemote = false, startPath = "/storage/emulated/0"))

        val picker = vm.uiState.value.pickerState
        assertThat(picker).isNotNull()
        assertThat(picker!!.isRemote).isFalse()
        assertThat(picker.currentPath).isEqualTo("/storage/emulated/0")
        assertThat(picker.entries).hasSize(3)
        assertThat(picker.isLoading).isFalse()
        assertThat(picker.error).isNull()
        // Sorted: directories first, then case-insensitive alpha.
        assertThat(picker.entries.map { it.name }).containsExactly("Alpha", "zeta", "readme.md").inOrder()
    }

    @Test
    fun showPicker_remoteFileSystem_loadsFromRemoteRepo() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "x".toByteArray()
        val entries = listOf(
            FileItem(name = "etc", path = "/etc", isDirectory = true),
            FileItem(name = "var", path = "/var", isDirectory = true),
            FileItem(name = "hosts", path = "/hosts", isDirectory = false),
        )
        coEvery { remoteRepo.listFiles("/") } returns entries

        val vm = createViewModel()
        vm.onEvent(CodeEditorEvent.ShowPicker(isRemote = true, startPath = "/"))

        val picker = vm.uiState.value.pickerState
        assertThat(picker).isNotNull()
        assertThat(picker!!.isRemote).isTrue()
        assertThat(picker.currentPath).isEqualTo("/")
        assertThat(picker.entries).hasSize(3)
        assertThat(picker.isLoading).isFalse()
        assertThat(picker.error).isNull()
        assertThat(picker.entries.map { it.name }).containsExactly("etc", "var", "hosts").inOrder()
        coVerify { remoteRepo.listFiles("/") }
    }

    @Test
    fun pickerNavigate_updatesPathAndReloadsEntries() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "x".toByteArray()
        coEvery { localRepo.listFiles("/storage/emulated/0") } returns listOf(
            fileItem("Documents", isDirectory = true),
        )
        coEvery { localRepo.listFiles("/storage/emulated/0/Documents") } returns listOf(
            FileItem("notes.txt", "/storage/emulated/0/Documents/notes.txt", isDirectory = false),
            FileItem("work", "/storage/emulated/0/Documents/work", isDirectory = true),
        )

        val vm = createViewModel()
        vm.onEvent(CodeEditorEvent.ShowPicker(isRemote = false, startPath = "/storage/emulated/0"))
        vm.onEvent(CodeEditorEvent.PickerNavigate("/storage/emulated/0/Documents"))

        val picker = vm.uiState.value.pickerState!!
        assertThat(picker.currentPath).isEqualTo("/storage/emulated/0/Documents")
        assertThat(picker.entries.map { it.name }).containsExactly("work", "notes.txt").inOrder()
        assertThat(picker.isLoading).isFalse()
    }

    @Test
    fun pickerSetRemote_togglesToRemoteWithRootPath() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "x".toByteArray()
        coEvery { localRepo.listFiles("/storage/emulated/0") } returns emptyList()
        coEvery { remoteRepo.listFiles("/") } returns listOf(
            FileItem("home", "/home", isDirectory = true),
        )

        val vm = createViewModel()
        vm.onEvent(CodeEditorEvent.ShowPicker(isRemote = false, startPath = "/storage/emulated/0"))
        vm.onEvent(CodeEditorEvent.PickerSetRemote(true))

        val picker = vm.uiState.value.pickerState!!
        assertThat(picker.isRemote).isTrue()
        assertThat(picker.currentPath).isEqualTo("/")
        assertThat(picker.entries.map { it.name }).containsExactly("home")
        coVerify { remoteRepo.listFiles("/") }
    }

    @Test
    fun pickerSetRemote_togglesToLocalWithDefaultPath() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "x".toByteArray()
        coEvery { remoteRepo.listFiles("/") } returns emptyList()
        coEvery { localRepo.listFiles("/storage/emulated/0") } returns listOf(
            fileItem("Downloads", isDirectory = true),
        )

        val vm = createViewModel()
        vm.onEvent(CodeEditorEvent.ShowPicker(isRemote = true, startPath = "/"))
        vm.onEvent(CodeEditorEvent.PickerSetRemote(false))

        val picker = vm.uiState.value.pickerState!!
        assertThat(picker.isRemote).isFalse()
        assertThat(picker.currentPath).isEqualTo("/storage/emulated/0")
        assertThat(picker.entries.map { it.name }).containsExactly("Downloads")
        coVerify { localRepo.listFiles("/storage/emulated/0") }
    }

    @Test
    fun hidePicker_clearsPickerState() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "x".toByteArray()
        coEvery { localRepo.listFiles(any()) } returns emptyList()

        val vm = createViewModel()
        vm.onEvent(CodeEditorEvent.ShowPicker(isRemote = false, startPath = "/storage/emulated/0"))
        assertThat(vm.uiState.value.pickerState).isNotNull()

        vm.onEvent(CodeEditorEvent.HidePicker)

        assertThat(vm.uiState.value.pickerState).isNull()
    }

    @Test
    fun showPicker_listFilesThrows_setsErrorInPickerState() = runTest {
        coEvery { localRepo.getFileContent(any()) } returns "x".toByteArray()
        coEvery { localRepo.listFiles("/storage/emulated/0") } throws RuntimeException("permission denied")

        val vm = createViewModel()
        vm.onEvent(CodeEditorEvent.ShowPicker(isRemote = false, startPath = "/storage/emulated/0"))

        val picker = vm.uiState.value.pickerState!!
        assertThat(picker.error).isNotNull()
        assertThat(picker.error).contains("permission denied")
        assertThat(picker.entries).isEmpty()
        assertThat(picker.isLoading).isFalse()
    }

    @Test
    fun init_smallTextFile_loadsNormally() = runTest {
        // Sanity: ensure the guard doesn't regress the happy path.
        coEvery { localRepo.getFileContent(any()) } returns "hello world".toByteArray()

        val vm = createViewModel()

        val tab = vm.uiState.value.activeTab!!
        assertThat(tab.content).isEqualTo("hello world")
        assertThat(tab.error).isNull()
    }
}
