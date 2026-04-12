package dev.ori.feature.editor.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DiffViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val diffId: String = savedStateHandle.get<String>("diffId")
        ?: error("diffId required")

    private val _uiState = MutableStateFlow(DiffViewerUiState())
    val uiState: StateFlow<DiffViewerUiState> = _uiState.asStateFlow()

    init {
        val payload = DiffDataHolder.get(diffId)
        if (payload == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Diff data expired. Please reopen.",
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    oldTitle = payload.oldTitle,
                    newTitle = payload.newTitle,
                )
            }
            viewModelScope.launch {
                val lines = withContext(Dispatchers.Default) {
                    DiffCalculator.computeDiff(payload.oldContent, payload.newContent)
                }
                _uiState.update {
                    it.copy(
                        diffLines = lines,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onEvent(event: DiffViewerEvent) {
        when (event) {
            is DiffViewerEvent.SetViewMode -> _uiState.update { it.copy(viewMode = event.mode) }
            is DiffViewerEvent.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    override fun onCleared() {
        DiffDataHolder.remove(diffId)
        super.onCleared()
    }
}
