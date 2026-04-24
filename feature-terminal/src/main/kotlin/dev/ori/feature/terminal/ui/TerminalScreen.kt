package dev.ori.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Foldable split-terminal Task 11 — device-width threshold in dp above
 * which the horizontal terminal split is available. Anything below this
 * (phones in portrait, folded Pixel Fold outer screen) falls back to
 * single-pane rendering. 600 dp matches the existing [isWideScreen]
 * heuristic for landscape/unfolded layouts.
 */
private const val SPLIT_THRESHOLD_DP = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateToConnections: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= SPLIT_THRESHOLD_DP

    // Foldable split-terminal Task 11 — horizontal terminal split kicks in
    // when the device is wide enough AND the user has at least two tabs.
    // Below either threshold the single-pane rendering path runs unchanged.
    val isSplitAvailable = configuration.screenWidthDp >= SPLIT_THRESHOLD_DP
    val isSplitActive = isSplitAvailable && uiState.tabs.size >= 2

    // Foldable split-terminal Task 13 — keep the ViewModel's runtime
    // split-flag in sync so [TerminalViewModel.getActiveTab] can route
    // keystrokes and resizes through [computeActiveTabId].
    LaunchedEffect(isSplitActive) {
        viewModel.setSplitActive(isSplitActive)
    }

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
        // Phase 15 Task 15.2 — imePadding() lives here, at the root of the
        // terminal stack, so the ENTIRE stack (tab bar + terminal pane +
        // KeyboardHost) lifts uniformly when the system IME opens. Previously
        // the padding was applied inside KeyboardHost, which caused the 53dp
        // extra-keys row to float 200-400px above Gboard. Scaffold's
        // innerPadding (status/nav bars) and imePadding() are orthogonal —
        // the former handles system bars, the latter the soft keyboard.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            // Tab bar — the `+` button stays reachable even when tabs is empty
            // so the user can always open a profile picker via the tab bar too.
            // The empty-state CTA below is the second path to the same goal.
            TerminalTabBar(
                tabs = uiState.tabs,
                activeTabIndex = uiState.activeTabIndex,
                onTabSelect = { index ->
                    uiState.tabs.getOrNull(index)?.let { tab ->
                        viewModel.onEvent(TerminalEvent.SelectTab(tab.id))
                    }
                },
                onTabClose = { viewModel.onEvent(TerminalEvent.CloseTab(it)) },
                onAddTab = {
                    viewModel.onEvent(TerminalEvent.OpenProfilePicker)
                },
                leftPaneTabId = uiState.leftPaneTabId,
                rightPaneTabId = uiState.rightPaneTabId,
                activePaneIndex = uiState.activePaneIndex,
                isSplitActive = isSplitActive,
                onMoveTabToPane = viewModel::moveTabToPane,
            )

            if (uiState.tabs.isEmpty()) {
                // PR 3 Spec Section 6 — empty state when no sessions are open.
                // Mirrors the file-manager's RemoteEmptyState. The `+` button in
                // the tab bar above is left in place as a redundant path to the
                // same profile-picker flow.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Keine aktiven Verbindungen",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Öffne Connections → Server antippen → Terminal öffnen.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onNavigateToConnections) {
                        Text("Zu Connections")
                    }
                }
            } else {
                // Foldable split-terminal Task 11 — unified split-or-single body.
                // Terminal-area is either ONE [PaneContent] (single-pane) or TWO
                // side-by-side [PaneContent]s (split mode @ >=600dp + >=2 tabs).
                // The keyboard host is ALWAYS rendered underneath and keeps the
                // existing Phase 14 drag-divider / IME / weight semantics.
                Column(modifier = Modifier.fillMaxSize()) {
                    TerminalBody(
                        uiState = uiState,
                        viewModel = viewModel,
                        isSplitActive = isSplitActive,
                        onTerminalTap = { imeFocusRequester.requestFocus() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (isWideScreen && isCustomMode) uiState.splitRatio else 1f),
                    )

                    // Draggable divider — only meaningful in wide-screen CUSTOM where
                    // both halves (terminal + in-app keyboard) are ours to weight.
                    if (isWideScreen && isCustomMode) {
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

                    // Keyboard host (3 modes). In wide-screen CUSTOM it gets the
                    // bottom split; in portrait / HYBRID / SYSTEM_ONLY it sizes
                    // itself via the system IME + imePadding(). See
                    // [TerminalKeyboardHostSlot] for the anchor-persistence
                    // invariant.
                    TerminalKeyboardHostSlot(
                        uiState = uiState,
                        imeFocusRequester = imeFocusRequester,
                        viewModel = viewModel,
                        isSplitActive = isSplitActive,
                        customModeModifier = if (isWideScreen) {
                            Modifier
                                .fillMaxWidth()
                                .weight(1f - uiState.splitRatio)
                        } else {
                            Modifier.fillMaxWidth()
                        },
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

    // Profile picker bottom-sheet (PR 2 Chunk 2 — replaces ServerPickerDialog)
    if (uiState.showProfilePicker) {
        val profiles by viewModel.profiles.collectAsStateWithLifecycle()
        TerminalProfilePicker(
            profiles = profiles,
            onPick = { profileId ->
                viewModel.openNewTab(profileId)
                viewModel.onEvent(TerminalEvent.DismissProfilePicker)
            },
            onDismiss = { viewModel.onEvent(TerminalEvent.DismissProfilePicker) },
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
 * Foldable split-terminal Task 11 — body of the terminal column
 * between [TerminalTabBar] and [TerminalKeyboardHostSlot]. Renders
 * either ONE [PaneContent] (single-pane) or TWO side-by-side
 * [PaneContent]s (split mode) depending on [isSplitActive].
 *
 * [PaneContent] owns the empty-state picker + focus chrome; this
 * composable's only job is wiring the correct tab into each slot,
 * dispatching pane-move events to [TerminalViewModel], and supplying
 * the [sessionBodyForTab] renderer to each pane's `sessionBody` slot.
 */
@Composable
private fun TerminalBody(
    uiState: TerminalUiState,
    viewModel: TerminalViewModel,
    isSplitActive: Boolean,
    onTerminalTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isSplitActive) {
        val leftTab = uiState.tabs.firstOrNull { it.id == uiState.leftPaneTabId }
        val rightTab = uiState.tabs.firstOrNull { it.id == uiState.rightPaneTabId }
        val isLeftActive = uiState.activePaneIndex == 0

        Row(modifier = modifier.fillMaxSize()) {
            PaneContent(
                tab = leftTab,
                isFocused = isLeftActive,
                isSplitActive = true,
                allTabs = uiState.tabs,
                leftPaneTabId = uiState.leftPaneTabId,
                rightPaneTabId = uiState.rightPaneTabId,
                paneContentDescription = "Terminal links",
                traversalPriority = if (isLeftActive) 0f else 1f,
                onTap = { viewModel.setActivePane(0) },
                onPickTab = { tabId -> viewModel.moveTabToPane(tabId, pane = 0) },
                onNewTabInThisSlot = { viewModel.onEvent(TerminalEvent.OpenProfilePicker) },
                modifier = Modifier.fillMaxHeight().weight(1f),
                sessionBody = { tab ->
                    sessionBodyForTab(
                        tab = tab,
                        viewModel = viewModel,
                        uiState = uiState,
                        onTap = onTerminalTap,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
            VerticalDivider(modifier = Modifier.width(1.dp))
            PaneContent(
                tab = rightTab,
                isFocused = !isLeftActive,
                isSplitActive = true,
                allTabs = uiState.tabs,
                leftPaneTabId = uiState.leftPaneTabId,
                rightPaneTabId = uiState.rightPaneTabId,
                paneContentDescription = "Terminal rechts",
                traversalPriority = if (isLeftActive) 1f else 0f,
                onTap = { viewModel.setActivePane(1) },
                onPickTab = { tabId -> viewModel.moveTabToPane(tabId, pane = 1) },
                onNewTabInThisSlot = { viewModel.onEvent(TerminalEvent.OpenProfilePicker) },
                modifier = Modifier.fillMaxHeight().weight(1f),
                sessionBody = { tab ->
                    sessionBodyForTab(
                        tab = tab,
                        viewModel = viewModel,
                        uiState = uiState,
                        onTap = onTerminalTap,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    } else {
        val singleTab = uiState.tabs.getOrNull(uiState.activeTabIndex)
        PaneContent(
            tab = singleTab,
            isFocused = true,
            isSplitActive = false,
            allTabs = uiState.tabs,
            leftPaneTabId = uiState.leftPaneTabId,
            rightPaneTabId = uiState.rightPaneTabId,
            paneContentDescription = "Terminal",
            traversalPriority = 0f,
            onTap = {},
            onPickTab = { /* single-pane ignores the picker */ },
            onNewTabInThisSlot = { viewModel.onEvent(TerminalEvent.OpenProfilePicker) },
            modifier = modifier.fillMaxSize(),
            sessionBody = { tab ->
                sessionBodyForTab(
                    tab = tab,
                    viewModel = viewModel,
                    uiState = uiState,
                    onTap = onTerminalTap,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}

/**
 * Foldable split-terminal Task 11 — terminal-buffer renderer for a
 * specific [tab]. Extracted so [PaneContent]'s `sessionBody` slot can
 * reuse the exact same code path in both single-pane and split mode.
 * Delegates to [TerminalContentArea] (the existing Phase 14 renderer).
 */
@Composable
private fun sessionBodyForTab(
    tab: TerminalTabState,
    viewModel: TerminalViewModel,
    uiState: TerminalUiState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emulator = viewModel.getEmulator(tab.id)
    TerminalContentArea(
        emulator = emulator,
        fontSize = uiState.terminalFontSize,
        onTap = onTap,
        modifier = modifier,
    )
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
    isSplitActive: Boolean,
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
                activePaneIndex = uiState.activePaneIndex,
                isSplitActive = isSplitActive,
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
                activePaneIndex = uiState.activePaneIndex,
                isSplitActive = isSplitActive,
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
