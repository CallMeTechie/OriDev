package dev.ori.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.components.OriTopBarDefaults
import dev.ori.core.ui.theme.OriTypography
import dev.ori.core.ui.theme.TerminalBackground
import dev.ori.core.ui.theme.TerminalText
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UnusedParameter")
fun TerminalScreen(
    initialProfileId: Long? = null,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    var showClipboardHistory by remember { mutableStateOf(false) }

    // Show error as snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(TerminalEvent.ClearError)
        }
    }

    LaunchedEffect(uiState.codeBlockSnackbar) {
        uiState.codeBlockSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onEvent(TerminalEvent.ClearCodeBlockSnackbar)
        }
    }

    val activeTab = uiState.tabs.getOrNull(uiState.activeTabIndex)
    val activeEmulator = activeTab?.let { viewModel.getEmulator(it.id) }

    Scaffold(
        topBar = {
            // Phase 11 P2.1 — replaces M3 TopAppBar (64 dp default) with the
            // 44 dp HeightCompact OriTopBar per terminal.html mockup spec.
            OriTopBar(
                title = "Terminal",
                height = OriTopBarDefaults.HeightCompact,
                actions = {
                    TerminalTopBarActions(
                        uiState = uiState,
                        showClipboardHistory = showClipboardHistory,
                        onShowClipboardHistoryChange = { showClipboardHistory = it },
                        onPasteFromSystem = {
                            clipboardManager.getText()?.text?.let { text ->
                                viewModel.onEvent(TerminalEvent.Paste(text))
                            }
                        },
                        onEvent = viewModel::onEvent,
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.onEvent(TerminalEvent.ShowSendToClaude("")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "An Claude senden",
                    )
                },
                text = { Text("Send to Claude") },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Tab bar
            TerminalTabBar(
                tabs = uiState.tabs,
                activeTabIndex = uiState.activeTabIndex,
                onTabSelect = { viewModel.onEvent(TerminalEvent.SwitchTab(it)) },
                onTabClose = { viewModel.onEvent(TerminalEvent.CloseTab(it)) },
                onAddTab = {
                    viewModel.onEvent(TerminalEvent.ToggleServerPicker)
                },
            )

            if (isWideScreen) {
                // Landscape/unfolded: terminal + keyboard side-by-side vertically with split
                Column(modifier = Modifier.fillMaxSize()) {
                    // Terminal content area
                    TerminalContentArea(
                        emulator = activeEmulator,
                        fontSize = uiState.terminalFontSize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(uiState.splitRatio),
                    )

                    // Draggable divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    val delta = dragAmount / size.height.toFloat()
                                    viewModel.onEvent(
                                        TerminalEvent.UpdateSplitRatio(uiState.splitRatio + delta),
                                    )
                                }
                            },
                    )

                    // Keyboard
                    if (uiState.isKeyboardVisible) {
                        CustomKeyboard(
                            onInput = { viewModel.onEvent(TerminalEvent.SendInput(it)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f - uiState.splitRatio),
                        )
                    }
                }
            } else {
                // Portrait: terminal fullscreen, keyboard as bottom section
                Column(modifier = Modifier.fillMaxSize()) {
                    TerminalContentArea(
                        emulator = activeEmulator,
                        fontSize = uiState.terminalFontSize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )

                    if (uiState.isKeyboardVisible) {
                        CustomKeyboard(
                            onInput = { viewModel.onEvent(TerminalEvent.SendInput(it)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    // Paste confirmation dialog
    uiState.showPasteConfirmation?.let { text ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TerminalEvent.CancelPaste) },
            title = { Text("Paste multiline text?") },
            text = {
                val lineCount = text.count { it == '\n' } + 1
                Text("This text contains $lineCount lines. Pasting may execute multiple commands.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(TerminalEvent.ConfirmPaste) }) {
                    Text("Paste")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(TerminalEvent.CancelPaste) }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Snippet sheet
    if (uiState.showSnippets) {
        SnippetSheet(
            snippets = uiState.snippets,
            searchQuery = uiState.snippetSearchQuery,
            editingSnippet = uiState.editingSnippet,
            showDialog = uiState.showSnippetDialog,
            onSnippetClick = { viewModel.onEvent(TerminalEvent.ExecuteSnippet(it.command)) },
            onSearchQueryChange = { viewModel.onEvent(TerminalEvent.SetSnippetSearchQuery(it)) },
            onAddClick = { viewModel.onEvent(TerminalEvent.ShowAddSnippetDialog) },
            onEditClick = { viewModel.onEvent(TerminalEvent.ShowEditSnippetDialog(it)) },
            onDeleteClick = { viewModel.onEvent(TerminalEvent.DeleteSnippet(it)) },
            onSaveSnippet = { name, command, category ->
                viewModel.onEvent(TerminalEvent.SaveSnippet(name, command, category))
            },
            onDismissDialog = { viewModel.onEvent(TerminalEvent.HideSnippetDialog) },
            onDismiss = { viewModel.onEvent(TerminalEvent.ToggleSnippets) },
        )
    }

    // Server picker dialog
    if (uiState.showServerPicker) {
        ServerPickerDialog(
            servers = uiState.availableServers,
            onSelect = { profileId, serverName ->
                viewModel.onEvent(TerminalEvent.SelectServer(profileId, serverName))
            },
            onDismiss = { viewModel.onEvent(TerminalEvent.ToggleServerPicker) },
        )
    }

    // Send to Claude sheet
    if (uiState.showSendToClaude) {
        SendToClaudeSheet(
            contextText = uiState.sendToClaudeContext,
            initialPrompt = uiState.sendToClaudeInput,
            loading = uiState.claudeLoading,
            response = uiState.claudeResponse,
            errorMessage = uiState.claudeError,
            onSend = { prompt -> viewModel.onEvent(TerminalEvent.SendToClaude(prompt)) },
            onDismiss = { viewModel.onEvent(TerminalEvent.HideSendToClaude) },
        )
    }

    // Code blocks sheet
    if (uiState.showCodeBlocksSheet) {
        CodeBlocksSheet(
            blocks = uiState.detectedCodeBlocks,
            onCopy = { viewModel.onEvent(TerminalEvent.CopyCodeBlock(it)) },
            onOpenInEditor = { viewModel.onEvent(TerminalEvent.OpenCodeBlockInEditor(it)) },
            onClear = { viewModel.onEvent(TerminalEvent.ClearCodeBlocks) },
            onDismiss = { viewModel.onEvent(TerminalEvent.ToggleCodeBlocksSheet) },
        )
    }

    // Preferences sheet
    if (uiState.showPreferences) {
        TerminalPreferencesSheet(
            fontSize = uiState.terminalFontSize,
            onFontSizeChange = { viewModel.onEvent(TerminalEvent.SetFontSize(it)) },
            onDismiss = { viewModel.onEvent(TerminalEvent.TogglePreferences) },
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun TerminalTopBarActions(
    uiState: TerminalUiState,
    showClipboardHistory: Boolean,
    onShowClipboardHistoryChange: (Boolean) -> Unit,
    onPasteFromSystem: () -> Unit,
    onEvent: (TerminalEvent) -> Unit,
) {
    // Clipboard history
    Box {
        IconButton(onClick = { onShowClipboardHistoryChange(true) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Zwischenablageverlauf")
        }
        ClipboardHistory(
            expanded = showClipboardHistory,
            entries = uiState.clipboardHistory,
            onEntryClick = { text ->
                onEvent(TerminalEvent.Paste(text))
                onShowClipboardHistoryChange(false)
            },
            onDismiss = { onShowClipboardHistoryChange(false) },
        )
    }

    // Paste from system clipboard
    IconButton(onClick = onPasteFromSystem) {
        Icon(Icons.Default.ContentPaste, contentDescription = "Aus Zwischenablage einfügen")
    }

    // Snippets
    IconButton(onClick = { onEvent(TerminalEvent.ToggleSnippets) }) {
        Icon(Icons.Default.PlayArrow, contentDescription = "Snippets öffnen")
    }

    // Detected code blocks
    IconButton(onClick = { onEvent(TerminalEvent.ToggleCodeBlocksSheet) }) {
        BadgedBox(
            badge = {
                if (uiState.detectedCodeBlocks.isNotEmpty()) {
                    Badge { Text(uiState.detectedCodeBlocks.size.toString()) }
                }
            },
        ) {
            Icon(
                Icons.Default.Code,
                contentDescription = "Erkannte Code-Blöcke anzeigen",
            )
        }
    }

    // Keyboard toggle
    IconButton(onClick = { onEvent(TerminalEvent.ToggleKeyboard) }) {
        Icon(
            imageVector = if (uiState.isKeyboardVisible) {
                Icons.Default.KeyboardHide
            } else {
                Icons.Default.Keyboard
            },
            contentDescription = if (uiState.isKeyboardVisible) {
                "Tastatur ausblenden"
            } else {
                "Tastatur einblenden"
            },
        )
    }

    // Recording toggle
    IconButton(onClick = {
        if (uiState.isRecording) {
            onEvent(TerminalEvent.StopRecording)
        } else {
            onEvent(TerminalEvent.StartRecording)
        }
    }) {
        Icon(
            imageVector = if (uiState.isRecording) {
                Icons.Default.Stop
            } else {
                Icons.Default.FiberManualRecord
            },
            contentDescription = if (uiState.isRecording) {
                "Aufzeichnung stoppen"
            } else {
                "Aufzeichnung starten"
            },
            tint = if (uiState.isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }

    // Export recording
    IconButton(
        onClick = { onEvent(TerminalEvent.ExportRecording) },
        enabled = uiState.activeRecordingId != null,
    ) {
        Icon(Icons.Default.IosShare, contentDescription = "Aufzeichnung exportieren")
    }

    // Preferences
    IconButton(onClick = { onEvent(TerminalEvent.TogglePreferences) }) {
        Icon(Icons.Default.Settings, contentDescription = "Terminal-Einstellungen")
    }
}

@Composable
@Suppress("UnusedParameter")
private fun TerminalContentArea(
    emulator: TerminalEmulator?,
    fontSize: Float, // P2.1: now unused — placeholder text uses OriTypography.terminalBody
    modifier: Modifier = Modifier,
) {
    if (emulator != null) {
        val lineCount = emulator.dimensions.rows
        Terminal(
            terminalEmulator = emulator,
            modifier = modifier
                .background(TerminalBackground)
                .semantics {
                    contentDescription = "Terminal, $lineCount Zeilen Ausgabe"
                },
        )
    } else {
        Box(
            modifier = modifier
                .background(TerminalBackground)
                .semantics {
                    contentDescription = "Terminal, keine aktive Sitzung"
                }
                .padding(8.dp),
        ) {
            Text(
                // Phase 11 P2.1 — JetBrains Mono via OriTypography.terminalBody
                // (was generic FontFamily.Monospace which fell back to system
                // monospace on Android — typically a less legible font).
                text = "No active session. Tap + to open a new terminal tab.",
                style = OriTypography.terminalBody.copy(
                    color = TerminalText.copy(alpha = 0.5f),
                ),
            )
        }
    }
}
