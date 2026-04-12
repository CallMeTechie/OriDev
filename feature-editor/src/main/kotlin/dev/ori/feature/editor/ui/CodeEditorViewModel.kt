package dev.ori.feature.editor.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.feature.FeatureGateManager
import dev.ori.core.common.feature.PremiumFeature
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.repository.LineChange
import dev.ori.domain.repository.LineDiffProvider
import dev.ori.domain.repository.LocalFileSystem
import dev.ori.domain.repository.RemoteFileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    @LocalFileSystem private val localRepository: FileSystemRepository,
    @RemoteFileSystem private val remoteRepository: FileSystemRepository,
    private val featureGateManager: FeatureGateManager,
    private val lineDiffProvider: LineDiffProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CodeEditorUiState(
            isReadOnly = !featureGateManager.isFeatureEnabled(PremiumFeature.CODE_EDITOR_WRITE),
        ),
    )
    val uiState: StateFlow<CodeEditorUiState> = _uiState.asStateFlow()

    init {
        val rawPath: String? = savedStateHandle["filePath"]
        val isRemote: Boolean = savedStateHandle["isRemote"] ?: false
        if (!rawPath.isNullOrEmpty()) {
            val decoded = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
            onEvent(CodeEditorEvent.OpenFile(decoded, isRemote))
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun onEvent(event: CodeEditorEvent) {
        when (event) {
            is CodeEditorEvent.OpenFile -> openFile(event.path, event.isRemote)
            is CodeEditorEvent.CloseTab -> closeTab(event.tabId)
            is CodeEditorEvent.SwitchTab -> switchTab(event.index)
            is CodeEditorEvent.ContentChanged -> contentChanged(event.content)
            CodeEditorEvent.Save -> save()
            CodeEditorEvent.ToggleSearch -> _uiState.update { it.copy(searchVisible = !it.searchVisible) }
            is CodeEditorEvent.SetSearchQuery -> setSearchQuery(event.query)
            is CodeEditorEvent.SetReplaceQuery -> _uiState.update { it.copy(replaceQuery = event.query) }
            CodeEditorEvent.ToggleCaseSensitive -> _uiState.update { it.copy(caseSensitive = !it.caseSensitive) }
            CodeEditorEvent.ReplaceAll -> replaceAll()
            CodeEditorEvent.FindNext -> { /* UI-level navigation -- no state change */ }
            CodeEditorEvent.FindPrevious -> { /* UI-level navigation -- no state change */ }
            CodeEditorEvent.ClearSavedMessage -> _uiState.update { it.copy(savedMessage = null) }
            CodeEditorEvent.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun repoFor(isRemote: Boolean): FileSystemRepository =
        if (isRemote) remoteRepository else localRepository

    private fun openFile(path: String, isRemote: Boolean) {
        val existingIndex = _uiState.value.tabs.indexOfFirst { it.filePath == path && it.isRemote == isRemote }
        if (existingIndex >= 0) {
            _uiState.update { it.copy(activeTabIndex = existingIndex) }
            return
        }
        val tabId = UUID.randomUUID().toString()
        val filename = path.substringAfterLast('/')
        val language = LanguageDetector.detect(filename).displayName
        val placeholderTab = EditorTab(
            id = tabId,
            filePath = path,
            filename = filename,
            content = "",
            originalContent = "",
            language = language,
            isRemote = isRemote,
            isLoading = true,
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + placeholderTab,
                activeTabIndex = state.tabs.size,
            )
        }
        viewModelScope.launch {
            val result = runCatching { repoFor(isRemote).getFileContent(path) }
            result.fold(
                onSuccess = { bytes ->
                    val text = bytes.toString(Charsets.UTF_8)
                    _uiState.update { state ->
                        state.copy(
                            tabs = state.tabs.map { tab ->
                                if (tab.id == tabId) {
                                    tab.copy(content = text, originalContent = text, isLoading = false)
                                } else {
                                    tab
                                }
                            },
                        )
                    }
                    if (!isRemote) {
                        loadGitDiffSummary(tabId, path)
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(
                            tabs = state.tabs.map { tab ->
                                if (tab.id == tabId) {
                                    tab.copy(isLoading = false, error = err.message)
                                } else {
                                    tab
                                }
                            },
                            error = "Failed to open $filename: ${err.message}",
                        )
                    }
                },
            )
        }
    }

    private fun loadGitDiffSummary(tabId: String, path: String) {
        viewModelScope.launch {
            val diff = runCatching { lineDiffProvider.getLineDiff(path) }.getOrDefault(emptyMap())
            val added = diff.values.count { it == LineChange.ADDED }
            val modified = diff.values.count { it == LineChange.MODIFIED }
            val summary = if (added == 0 && modified == 0) null else GitDiffSummary(added, modified)
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map { tab ->
                        if (tab.id == tabId) tab.copy(gitDiffSummary = summary) else tab
                    },
                )
            }
        }
    }

    private fun closeTab(tabId: String) {
        _uiState.update { state ->
            val newTabs = state.tabs.filterNot { it.id == tabId }
            val newIndex = if (state.activeTabIndex >= newTabs.size) {
                (newTabs.size - 1).coerceAtLeast(0)
            } else {
                state.activeTabIndex
            }
            state.copy(tabs = newTabs, activeTabIndex = newIndex)
        }
    }

    private fun switchTab(index: Int) {
        _uiState.update { it.copy(activeTabIndex = index) }
    }

    private fun contentChanged(content: String) {
        if (_uiState.value.isReadOnly) return
        _uiState.update { state ->
            val idx = state.activeTabIndex
            state.copy(
                tabs = state.tabs.mapIndexed { i, tab ->
                    if (i == idx) tab.copy(content = content) else tab
                },
            )
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.isReadOnly) {
            _uiState.update { it.copy(error = "Editing requires Premium") }
            return
        }
        val tab = state.activeTab ?: return
        if (!tab.isDirty) return
        viewModelScope.launch {
            val result = runCatching {
                repoFor(tab.isRemote).writeFileContent(tab.filePath, tab.content.toByteArray(Charsets.UTF_8))
            }
            result.fold(
                onSuccess = {
                    _uiState.update { s ->
                        s.copy(
                            tabs = s.tabs.map {
                                if (it.id == tab.id) it.copy(originalContent = it.content) else it
                            },
                            savedMessage = "Saved ${tab.filename}",
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(error = "Save failed: ${err.message}") }
                },
            )
        }
    }

    private fun setSearchQuery(query: String) {
        val tab = _uiState.value.activeTab
        val matches = if (tab != null && query.isNotEmpty()) {
            countMatches(tab.content, query, _uiState.value.caseSensitive)
        } else {
            0
        }
        _uiState.update { it.copy(searchQuery = query, matchCount = matches) }
    }

    private fun replaceAll() {
        val state = _uiState.value
        if (state.isReadOnly || state.searchQuery.isEmpty()) return
        val tab = state.activeTab ?: return
        val newContent = if (state.caseSensitive) {
            tab.content.replace(state.searchQuery, state.replaceQuery)
        } else {
            tab.content.replace(Regex(Regex.escape(state.searchQuery), RegexOption.IGNORE_CASE), state.replaceQuery)
        }
        _uiState.update { s ->
            s.copy(
                tabs = s.tabs.map {
                    if (it.id == tab.id) it.copy(content = newContent) else it
                },
                matchCount = 0,
            )
        }
    }

    private fun countMatches(text: String, query: String, caseSensitive: Boolean): Int {
        if (query.isEmpty()) return 0
        val pattern = if (caseSensitive) {
            Regex(Regex.escape(query))
        } else {
            Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        }
        return pattern.findAll(text).count()
    }
}
