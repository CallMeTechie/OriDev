package dev.ori.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.components.OriTopBarDefaults
import dev.ori.core.ui.icons.lucide.Circle
import dev.ori.core.ui.icons.lucide.CircleStop
import dev.ori.core.ui.icons.lucide.Clipboard
import dev.ori.core.ui.icons.lucide.Code
import dev.ori.core.ui.icons.lucide.Copy
import dev.ori.core.ui.icons.lucide.Keyboard
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Play
import dev.ori.core.ui.icons.lucide.Settings
import dev.ori.core.ui.icons.lucide.Share2
import dev.ori.core.ui.theme.OriTypography
import dev.ori.core.ui.theme.TerminalBackground
import dev.ori.core.ui.theme.TerminalText
import dev.ori.domain.model.KeyboardMode
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
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    var showClipboardHistory by remember { mutableStateOf(false) }

    // Phase 14 Task 14.5 — ONE FocusRequester hoisted to the screen
    // level so the single TerminalImeAnchor in KeyboardHost keeps its
    // focus grip across tab switches, orientation changes, and the
    // keyboard-icon toggle. See KeyboardHost KDoc "Invariants".
    val imeFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    // In HYBRID / SYSTEM_ONLY the drag-divider between terminal and
    // keyboard is meaningless (the IME owns its own height). We hide
    // it and lift the terminal to weight(1f). splitRatio stays in
    // state but is only applied in CUSTOM.
    val isCustomMode = uiState.keyboardMode == KeyboardMode.CUSTOM

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

    // Phase 14 Task 14.5 — in HYBRID/SYSTEM_ONLY the KeyboardHost is
    // rendered unconditionally so the single TerminalImeAnchor never
    // unmounts. The keyboard-toggle therefore has to control the system
    // IME imperatively: hide it when toggling off, re-focus the anchor
    // (which summons the IME) when toggling on. In CUSTOM the toggle
    // still just governs composable visibility, so this block is a
    // no-op for that mode.
    LaunchedEffect(uiState.isKeyboardVisible, uiState.keyboardMode) {
        if (uiState.keyboardMode != KeyboardMode.CUSTOM) {
            if (uiState.isKeyboardVisible) {
                runCatching { imeFocusRequester.requestFocus() }
            } else {
                softwareKeyboardController?.hide()
            }
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
                            viewModel.onEvent(TerminalEvent.PasteFromSystem)
                        },
                        onToggleKeyboard = {
                            // Phase 14 Task 14.5 — in HYBRID/SYSTEM_ONLY the
                            // KeyboardHost (and therefore TerminalImeAnchor)
                            // stays mounted across this toggle — otherwise
                            // re-enabling the keyboard would build a fresh
                            // anchor with no focus, and the IME would not
                            // reappear. Instead we drive the IME directly:
                            // hide() when the user toggles off, and
                            // requestFocus() to summon it when toggling on.
                            // See LaunchedEffect below which reacts to the
                            // isKeyboardVisible / keyboardMode pair so we
                            // cover both the show and the hide path (and
                            // handle the case where the mode changes while
                            // already visible).
                            viewModel.onEvent(TerminalEvent.ToggleKeyboard)
                        },
                        onEvent = viewModel::onEvent,
                    )
                },
            )
        },
        // Phase 15 Task 15.1 — the previous Send-to-Claude FAB
        // permanently overlapped the keyboard area in HYBRID/SYSTEM_ONLY
        // and was unclear in intent. The proper UX (toolbar action on
        // selected terminal output) lands in a later phase. The
        // ShowSendToClaude event itself stays wired (codeblock detector
        // still triggers it).
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
                // Landscape/unfolded: terminal + keyboard side-by-side vertically with split.
                // In HYBRID / SYSTEM_ONLY the split-weight logic no longer makes sense because
                // the system IME provides its own height — the terminal gets weight(1f) and
                // the KeyboardHost is inserted after with its own imePadding().
                Column(modifier = Modifier.fillMaxSize()) {
                    // Terminal content area
                    TerminalContentArea(
                        emulator = activeEmulator,
                        fontSize = uiState.terminalFontSize,
                        onTap = { imeFocusRequester.requestFocus() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (isCustomMode) uiState.splitRatio else 1f),
                    )

                    // Draggable divider — only meaningful in CUSTOM where both halves are ours.
                    if (isCustomMode) {
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
                    }

                    // Keyboard host (3 modes) — only in CUSTOM is the weighted slice applied;
                    // HYBRID / SYSTEM_ONLY size themselves via the system IME + imePadding().
                    // See [TerminalKeyboardHostSlot] for the anchor-persistence invariant.
                    TerminalKeyboardHostSlot(
                        uiState = uiState,
                        imeFocusRequester = imeFocusRequester,
                        viewModel = viewModel,
                        customModeModifier = Modifier
                            .fillMaxWidth()
                            .weight(1f - uiState.splitRatio),
                    )
                }
            } else {
                // Portrait: terminal fullscreen, keyboard as bottom section
                Column(modifier = Modifier.fillMaxSize()) {
                    TerminalContentArea(
                        emulator = activeEmulator,
                        fontSize = uiState.terminalFontSize,
                        onTap = { imeFocusRequester.requestFocus() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )

                    TerminalKeyboardHostSlot(
                        uiState = uiState,
                        imeFocusRequester = imeFocusRequester,
                        viewModel = viewModel,
                        customModeModifier = Modifier.fillMaxWidth(),
                    )
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
            onCopyResponse = { text -> viewModel.onEvent(TerminalEvent.CopyClaudeResponse(text)) },
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

/**
 * Phase 14 Task 14.5 review fix — HYBRID/SYSTEM_ONLY must keep
 * [KeyboardHost] mounted even when `isKeyboardVisible = false` so the
 * single [TerminalImeAnchor] stays alive across the keyboard-toggle.
 * Unmounting it would drop focus and the IME would not reopen when the
 * user flips the toggle back on. The toggle instead drives the IME
 * imperatively via [LocalSoftwareKeyboardController] (see the
 * `LaunchedEffect(uiState.isKeyboardVisible, uiState.keyboardMode)`
 * in [TerminalScreen]). In CUSTOM mode the previous visibility-gated
 * behaviour is preserved (no anchor, no IME interaction).
 *
 * Extracted into its own composable so the landscape + portrait branches
 * stay symmetric and [TerminalScreen] stays under the detekt LongMethod
 * threshold.
 */
@Composable
private fun TerminalKeyboardHostSlot(
    uiState: TerminalUiState,
    imeFocusRequester: FocusRequester,
    viewModel: TerminalViewModel,
    customModeModifier: Modifier,
) {
    val isCustomMode = uiState.keyboardMode == KeyboardMode.CUSTOM
    when {
        isCustomMode && uiState.isKeyboardVisible -> {
            KeyboardHost(
                mode = KeyboardMode.CUSTOM,
                modifierState = uiState.modifierState,
                imeFocusRequester = imeFocusRequester,
                onInput = { bytes -> viewModel.onEvent(TerminalEvent.SendInput(bytes)) },
                onEvent = viewModel::onEvent,
                modifier = customModeModifier,
            )
        }
        !isCustomMode -> {
            KeyboardHost(
                mode = uiState.keyboardMode,
                modifierState = uiState.modifierState,
                imeFocusRequester = imeFocusRequester,
                onInput = { bytes -> viewModel.onEvent(TerminalEvent.SendInput(bytes)) },
                onEvent = viewModel::onEvent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // CUSTOM + isKeyboardVisible=false → render nothing.
    }
}

@Composable
@Suppress("LongMethod")
private fun TerminalTopBarActions(
    uiState: TerminalUiState,
    showClipboardHistory: Boolean,
    onShowClipboardHistoryChange: (Boolean) -> Unit,
    onPasteFromSystem: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onEvent: (TerminalEvent) -> Unit,
) {
    // Clipboard history
    Box {
        IconButton(onClick = { onShowClipboardHistoryChange(true) }) {
            Icon(LucideIcons.Copy, contentDescription = "Zwischenablageverlauf")
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
        Icon(LucideIcons.Clipboard, contentDescription = "Aus Zwischenablage einfügen")
    }

    // Snippets
    IconButton(onClick = { onEvent(TerminalEvent.ToggleSnippets) }) {
        Icon(LucideIcons.Play, contentDescription = "Snippets öffnen")
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
                LucideIcons.Code,
                contentDescription = "Erkannte Code-Blöcke anzeigen",
            )
        }
    }

    // Keyboard toggle
    IconButton(onClick = onToggleKeyboard) {
        Icon(
            imageVector = LucideIcons.Keyboard,
            contentDescription = if (uiState.isKeyboardVisible) {
                "Tastatur verbergen"
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
                LucideIcons.CircleStop
            } else {
                LucideIcons.Circle
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
        Icon(LucideIcons.Share2, contentDescription = "Aufzeichnung exportieren")
    }

    // Preferences
    IconButton(onClick = { onEvent(TerminalEvent.TogglePreferences) }) {
        Icon(LucideIcons.Settings, contentDescription = "Terminal-Einstellungen")
    }
}

@Composable
@Suppress("UnusedParameter")
private fun TerminalContentArea(
    emulator: TerminalEmulator?,
    fontSize: Float, // P2.1: now unused — placeholder text uses OriTypography.terminalBody
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    // Phase 14 Task 14.5 — a tap anywhere in the terminal pane should
    // summon the IME in HYBRID/SYSTEM_ONLY modes by re-focusing the
    // TerminalImeAnchor. In CUSTOM mode the focus-request is a no-op
    // (the anchor is not rendered).
    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() })
    }
    if (emulator != null) {
        val lineCount = emulator.dimensions.rows
        Terminal(
            terminalEmulator = emulator,
            modifier = modifier
                .background(TerminalBackground)
                .then(tapModifier)
                .semantics {
                    contentDescription = "Terminal, $lineCount Zeilen Ausgabe"
                },
        )
    } else {
        Box(
            modifier = modifier
                .background(TerminalBackground)
                .then(tapModifier)
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
