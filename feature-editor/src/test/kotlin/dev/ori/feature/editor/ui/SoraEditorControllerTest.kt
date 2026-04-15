package dev.ori.feature.editor.ui

import com.google.common.truth.Truth.assertThat
import io.github.rosemoe.sora.widget.CodeEditor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * Phase 11 P4.4 — unit tests for [SoraEditorController]. These exercise
 * the controller in isolation by mocking the underlying Sora-Editor
 * [CodeEditor], so no Robolectric / Android context is required.
 */
class SoraEditorControllerTest {

    private val editor: CodeEditor = mockk(relaxed = true)

    @Test
    fun initialState_noEditor_canUndoAndCanRedoAreFalse() {
        val controller = SoraEditorController()

        assertThat(controller.canUndo).isFalse()
        assertThat(controller.canRedo).isFalse()
    }

    @Test
    fun editorSet_refreshReadsCanUndoCanRedo() {
        every { editor.canUndo() } returns true
        every { editor.canRedo() } returns false
        val controller = SoraEditorController()

        controller.editor = editor

        assertThat(controller.canUndo).isTrue()
        assertThat(controller.canRedo).isFalse()
    }

    @Test
    fun undo_delegatesToEditorAndRefreshesFlags() {
        // First call (during editor= refresh) returns the "before undo" state,
        // second call (during undo() refresh) returns the "after undo" state.
        every { editor.canUndo() } returns true andThen false
        every { editor.canRedo() } returns false andThen true
        val controller = SoraEditorController()
        controller.editor = editor

        controller.undo()

        verify(exactly = 1) { editor.undo() }
        assertThat(controller.canUndo).isFalse()
        assertThat(controller.canRedo).isTrue()
    }

    @Test
    fun redo_delegatesToEditorAndRefreshesFlags() {
        every { editor.canUndo() } returns false andThen true
        every { editor.canRedo() } returns true andThen false
        val controller = SoraEditorController()
        controller.editor = editor

        controller.redo()

        verify(exactly = 1) { editor.redo() }
        assertThat(controller.canUndo).isTrue()
        assertThat(controller.canRedo).isFalse()
    }

    @Test
    fun undoRedo_withoutEditor_areNoOps() {
        val controller = SoraEditorController()

        // Should not crash even though there is no underlying editor.
        controller.undo()
        controller.redo()

        assertThat(controller.canUndo).isFalse()
        assertThat(controller.canRedo).isFalse()
    }

    @Test
    fun editorNulled_refreshClearsFlags() {
        every { editor.canUndo() } returns true
        every { editor.canRedo() } returns true
        val controller = SoraEditorController()
        controller.editor = editor
        assertThat(controller.canUndo).isTrue()
        assertThat(controller.canRedo).isTrue()

        controller.editor = null

        assertThat(controller.canUndo).isFalse()
        assertThat(controller.canRedo).isFalse()
    }
}
