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
}
