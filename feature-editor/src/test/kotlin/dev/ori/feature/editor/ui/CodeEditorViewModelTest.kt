package dev.ori.feature.editor.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.feature.FeatureGateManager
import dev.ori.core.common.feature.PremiumFeature
import dev.ori.domain.repository.FileSystemRepository
import io.mockk.coEvery
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
}
