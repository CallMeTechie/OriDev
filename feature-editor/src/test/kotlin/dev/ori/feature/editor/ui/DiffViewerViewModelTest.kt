package dev.ori.feature.editor.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiffViewerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        DiffDataHolder.clear()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        DiffDataHolder.clear()
    }

    private fun createViewModel(id: String): DiffViewerViewModel {
        val handle = SavedStateHandle(mapOf("diffId" to id))
        return DiffViewerViewModel(handle)
    }

    private fun callOnCleared(vm: DiffViewerViewModel) {
        // onCleared is protected; invoke via reflection.
        val viewModelClass = androidx.lifecycle.ViewModel::class.java
        val method = viewModelClass.getDeclaredMethod("onCleared").apply {
            isAccessible = true
        }
        method.invoke(vm)
    }

    @Test
    fun `init loads diff from holder`() {
        DiffDataHolder.put(
            "d1",
            DiffPayload(
                oldContent = "a\nb",
                newContent = "a\nB",
                oldTitle = "old.txt",
                newTitle = "new.txt",
            ),
        )
        val vm = createViewModel("d1")
        // Titles are set synchronously in init; diff lines are computed on
        // Dispatchers.Default which runs on a real background thread, so we
        // poll real wall-clock time rather than virtual test time.
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.uiState.value.isLoading && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.oldTitle).isEqualTo("old.txt")
        assertThat(state.newTitle).isEqualTo("new.txt")
        assertThat(state.diffLines).isNotEmpty()
        assertThat(state.error).isNull()
    }

    @Test
    fun `missing payload shows expired error`() {
        val vm = createViewModel("missing")
        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).contains("expired")
    }

    @Test
    fun `set view mode updates state`() {
        DiffDataHolder.put(
            "d2",
            DiffPayload("x", "x", "a", "a"),
        )
        val vm = createViewModel("d2")
        vm.onEvent(DiffViewerEvent.SetViewMode(DiffViewMode.SIDE_BY_SIDE))
        assertThat(vm.uiState.value.viewMode).isEqualTo(DiffViewMode.SIDE_BY_SIDE)
    }

    @Test
    fun `onCleared removes payload from holder`() {
        DiffDataHolder.put(
            "d3",
            DiffPayload("a", "b", "t1", "t2"),
        )
        val vm = createViewModel("d3")
        assertThat(DiffDataHolder.get("d3")).isNotNull()
        callOnCleared(vm)
        assertThat(DiffDataHolder.get("d3")).isNull()
    }
}
