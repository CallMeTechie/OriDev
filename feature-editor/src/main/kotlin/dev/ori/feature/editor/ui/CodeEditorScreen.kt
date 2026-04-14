package dev.ori.feature.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import dev.ori.core.ui.components.OriIconButton
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.components.OriTopBarDefaults
import dev.ori.core.ui.icons.lucide.ChevronLeft
import dev.ori.core.ui.icons.lucide.FolderOpen
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Redo2
import dev.ori.core.ui.icons.lucide.Save
import dev.ori.core.ui.icons.lucide.Search
import dev.ori.core.ui.icons.lucide.Undo2

@Composable
fun CodeEditorScreen(
    viewModel: CodeEditorViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Phase 11 P4.4 — imperative handle into Sora's built-in undo stack.
    val editorController = remember { SoraEditorController() }

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
            // Phase 11 P2.2 — replaces deprecated OriDevTopBar (M3 64dp wrapper)
            // with the 40 dp HeightDense OriTopBar per code-editor.html mockup.
            // Material Save/Search icons replaced with Lucide equivalents per
            // the Phase 11 forbidden-imports policy.
            OriTopBar(
                title = title,
                height = OriTopBarDefaults.HeightDense,
                navigationIcon = if (onNavigateBack != null) {
                    {
                        OriIconButton(
                            icon = LucideIcons.ChevronLeft,
                            contentDescription = "Zurück",
                            onClick = onNavigateBack,
                        )
                    }
                } else {
                    null
                },
                actions = {
                    OriIconButton(
                        icon = LucideIcons.FolderOpen,
                        contentDescription = "Datei öffnen",
                        onClick = {
                            val start = activeTab?.let { tab ->
                                tab.filePath.substringBeforeLast('/', missingDelimiterValue = "/")
                                    .ifEmpty { "/" }
                            } ?: "/storage/emulated/0"
                            val startRemote = activeTab?.isRemote ?: false
                            viewModel.onEvent(CodeEditorEvent.ShowPicker(startRemote, start))
                        },
                    )
                    // Phase 11 P4.4 — undo/redo actions driven by
                    // SoraEditorController (backed by Sora's internal edit
                    // stack). Disabled when there's nothing to un/redo or
                    // when the editor is read-only.
                    OriIconButton(
                        icon = LucideIcons.Undo2,
                        contentDescription = "Rückgängig",
                        onClick = { editorController.undo() },
                        enabled = editorController.canUndo && !uiState.isReadOnly,
                    )
                    OriIconButton(
                        icon = LucideIcons.Redo2,
                        contentDescription = "Wiederherstellen",
                        onClick = { editorController.redo() },
                        enabled = editorController.canRedo && !uiState.isReadOnly,
                    )
                    OriIconButton(
                        icon = LucideIcons.Save,
                        contentDescription = "Datei speichern",
                        onClick = { viewModel.onEvent(CodeEditorEvent.Save) },
                        enabled = activeTab?.isDirty == true && !uiState.isReadOnly,
                    )
                    OriIconButton(
                        icon = LucideIcons.Search,
                        contentDescription = "Suchen und Ersetzen",
                        onClick = { viewModel.onEvent(CodeEditorEvent.ToggleSearch) },
                    )
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
                    controller = editorController,
                )
                GitDiffStatusBar(summary = activeTab.gitDiffSummary)
            }
        }
    }

    // Phase 11 P4.3 — remote file picker sheet, rendered when pickerState is set.
    uiState.pickerState?.let { picker ->
        RemoteFilePickerSheet(
            picker = picker,
            onDismiss = { viewModel.onEvent(CodeEditorEvent.HidePicker) },
            onNavigate = { path -> viewModel.onEvent(CodeEditorEvent.PickerNavigate(path)) },
            onSetRemote = { isRemote -> viewModel.onEvent(CodeEditorEvent.PickerSetRemote(isRemote)) },
            onOpenFile = { path, isRemote ->
                viewModel.onEvent(CodeEditorEvent.OpenFile(path, isRemote))
                viewModel.onEvent(CodeEditorEvent.HidePicker)
            },
        )
    }
}
