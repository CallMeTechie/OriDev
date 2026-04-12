package dev.ori.feature.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.component.OriDevTopBar

@Composable
fun CodeEditorScreen(
    viewModel: CodeEditorViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(CodeEditorEvent.ClearError)
        }
    }
    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(CodeEditorEvent.ClearSavedMessage)
        }
    }

    val activeTab = uiState.activeTab
    val title = buildString {
        append(activeTab?.filename ?: "Editor")
        if (activeTab?.isDirty == true) append(" *")
    }

    Scaffold(
        topBar = {
            OriDevTopBar(
                title = title,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.onEvent(CodeEditorEvent.Save) },
                        enabled = activeTab?.isDirty == true && !uiState.isReadOnly,
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Datei speichern")
                    }
                    IconButton(onClick = { viewModel.onEvent(CodeEditorEvent.ToggleSearch) }) {
                        Icon(Icons.Default.Search, contentDescription = "Suchen und Ersetzen")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.tabs.size > 1) {
                EditorTabBar(
                    tabs = uiState.tabs,
                    activeTabIndex = uiState.activeTabIndex,
                    onSelect = { viewModel.onEvent(CodeEditorEvent.SwitchTab(it)) },
                    onClose = { viewModel.onEvent(CodeEditorEvent.CloseTab(it)) },
                )
            }
            if (uiState.searchVisible) {
                SearchReplaceBar(
                    searchQuery = uiState.searchQuery,
                    replaceQuery = uiState.replaceQuery,
                    matchCount = uiState.matchCount,
                    caseSensitive = uiState.caseSensitive,
                    isReadOnly = uiState.isReadOnly,
                    onSearchQueryChange = { viewModel.onEvent(CodeEditorEvent.SetSearchQuery(it)) },
                    onReplaceQueryChange = { viewModel.onEvent(CodeEditorEvent.SetReplaceQuery(it)) },
                    onFindNext = { viewModel.onEvent(CodeEditorEvent.FindNext) },
                    onFindPrevious = { viewModel.onEvent(CodeEditorEvent.FindPrevious) },
                    onReplaceAll = { viewModel.onEvent(CodeEditorEvent.ReplaceAll) },
                    onToggleCaseSensitive = { viewModel.onEvent(CodeEditorEvent.ToggleCaseSensitive) },
                )
            }
            if (activeTab != null) {
                val language = activeTab.filename.substringAfterLast('.', "Text")
                SoraEditorView(
                    content = activeTab.content,
                    filename = activeTab.filename,
                    readOnly = uiState.isReadOnly,
                    onContentChange = { viewModel.onEvent(CodeEditorEvent.ContentChanged(it)) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Code-Editor, Sprache $language"
                        },
                )
                GitDiffStatusBar(summary = activeTab.gitDiffSummary)
            }
        }
    }
}
