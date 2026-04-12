package dev.ori.feature.editor.ui

data class DiffViewerUiState(
    val oldTitle: String = "",
    val newTitle: String = "",
    val diffLines: List<DiffLine> = emptyList(),
    val viewMode: DiffViewMode = DiffViewMode.UNIFIED,
    val isLoading: Boolean = true,
    val error: String? = null,
)

enum class DiffViewMode { UNIFIED, SIDE_BY_SIDE }

sealed class DiffViewerEvent {
    data class SetViewMode(val mode: DiffViewMode) : DiffViewerEvent()
    data object ClearError : DiffViewerEvent()
}
