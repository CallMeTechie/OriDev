package dev.ori.feature.editor.ui

data class EditorTab(
    val id: String,
    val filePath: String,
    val filename: String,
    val content: String,
    val originalContent: String,
    val language: String,
    val isRemote: Boolean,
    val isLoading: Boolean = false,
    val error: String? = null,
    val gitDiffSummary: GitDiffSummary? = null,
)

data class GitDiffSummary(val added: Int, val modified: Int)

val EditorTab.isDirty: Boolean
    get() = content != originalContent

data class CodeEditorUiState(
    val tabs: List<EditorTab> = emptyList(),
    val activeTabIndex: Int = 0,
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val matchCount: Int = 0,
    val caseSensitive: Boolean = false,
    val isReadOnly: Boolean = false,
    val savedMessage: String? = null,
    val error: String? = null,
    // Phase 11 P4.3 — remote file picker state.
    val pickerState: PickerState? = null,
)

/**
 * Phase 11 P4.3 — transient state for the "Open file" bottom sheet in the
 * Code Editor. Tracks which source (local vs remote) is being browsed, the
 * current directory, the loaded file listing, and loading / error flags.
 * Nulled out when the sheet is dismissed.
 */
data class PickerState(
    val isRemote: Boolean,
    val currentPath: String,
    val entries: List<dev.ori.domain.model.FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

val CodeEditorUiState.activeTab: EditorTab?
    get() = tabs.getOrNull(activeTabIndex)

sealed class CodeEditorEvent {
    data class OpenFile(val path: String, val isRemote: Boolean) : CodeEditorEvent()
    data class CloseTab(val tabId: String) : CodeEditorEvent()
    data class SwitchTab(val index: Int) : CodeEditorEvent()
    data class ContentChanged(val content: String) : CodeEditorEvent()
    data object Save : CodeEditorEvent()
    data object ToggleSearch : CodeEditorEvent()
    data class SetSearchQuery(val query: String) : CodeEditorEvent()
    data class SetReplaceQuery(val query: String) : CodeEditorEvent()
    data object ToggleCaseSensitive : CodeEditorEvent()
    data object ReplaceAll : CodeEditorEvent()
    data object FindNext : CodeEditorEvent()
    data object FindPrevious : CodeEditorEvent()
    data object ClearSavedMessage : CodeEditorEvent()
    data object ClearError : CodeEditorEvent()

    // Phase 11 P4.3 — remote file picker events.
    /** Open the picker at [startPath] on either local or remote file system. */
    data class ShowPicker(val isRemote: Boolean, val startPath: String) : CodeEditorEvent()

    /** Dismiss the picker without opening anything. */
    data object HidePicker : CodeEditorEvent()

    /** Navigate the open picker to [path] (refreshes the listing). */
    data class PickerNavigate(val path: String) : CodeEditorEvent()

    /** Switch the open picker between local and remote file systems. */
    data class PickerSetRemote(val isRemote: Boolean) : CodeEditorEvent()
}
