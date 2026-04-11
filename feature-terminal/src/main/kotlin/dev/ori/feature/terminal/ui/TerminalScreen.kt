package dev.ori.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.theme.TerminalBackground
import dev.ori.core.ui.theme.TerminalText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
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

    // Auto-scroll terminal output
    val scrollState = rememberScrollState()
    val activeTab = uiState.tabs.getOrNull(uiState.activeTabIndex)

    LaunchedEffect(activeTab?.outputVersion) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                actions = {
                    // Clipboard history
                    Box {
                        IconButton(onClick = { showClipboardHistory = true }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Clipboard history")
                        }
                        ClipboardHistory(
                            expanded = showClipboardHistory,
                            entries = uiState.clipboardHistory,
                            onEntryClick = { text ->
                                viewModel.onEvent(TerminalEvent.Paste(text))
                                showClipboardHistory = false
                            },
                            onDismiss = { showClipboardHistory = false },
                        )
                    }

                    // Paste from system clipboard
                    IconButton(onClick = {
                        clipboardManager.getText()?.text?.let { text ->
                            viewModel.onEvent(TerminalEvent.Paste(text))
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }

                    // Snippets
                    IconButton(onClick = { viewModel.onEvent(TerminalEvent.ToggleSnippets) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Snippets")
                    }

                    // Keyboard toggle
                    IconButton(onClick = { viewModel.onEvent(TerminalEvent.ToggleKeyboard) }) {
                        Icon(
                            imageVector = if (uiState.isKeyboardVisible) {
                                Icons.Default.KeyboardHide
                            } else {
                                Icons.Default.Keyboard
                            },
                            contentDescription = "Toggle keyboard",
                        )
                    }

                    // Preferences
                    IconButton(onClick = { viewModel.onEvent(TerminalEvent.TogglePreferences) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Preferences")
                    }
                },
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
                    // TODO: show server picker dialog
                },
            )

            if (isWideScreen) {
                // Landscape/unfolded: terminal + keyboard side-by-side vertically with split
                Column(modifier = Modifier.fillMaxSize()) {
                    // Terminal content area
                    TerminalContentArea(
                        output = activeTab?.let { viewModel.getTerminalOutput(it.id) } ?: "",
                        fontSize = uiState.terminalFontSize,
                        scrollState = scrollState,
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
                                        TerminalEvent.UpdateSplitRatio(uiState.splitRatio + delta)
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
                        output = activeTab?.let { viewModel.getTerminalOutput(it.id) } ?: "",
                        fontSize = uiState.terminalFontSize,
                        scrollState = scrollState,
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
            onSnippetClick = { viewModel.onEvent(TerminalEvent.ExecuteSnippet(it)) },
            onDismiss = { viewModel.onEvent(TerminalEvent.ToggleSnippets) },
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

// TODO: Replace placeholder with termlib Terminal composable.
// Migration path:
//   1. Create TerminalEmulator per tab via TerminalEmulatorFactory.Companion.create()
//      passing the main Looper, initial cols/rows, and callbacks:
//      - onKeyboardInput: write bytes to shellHandle.outputStream
//      - onResize: invoke shellHandle.onResize
//   2. Feed SSH output bytes into emulator.writeInput() instead of StringBuilder
//   3. Replace TerminalContentArea with:
//        org.connectbot.terminal.Terminal(emulator = emulator, modifier = modifier)
//   4. Remove manual scroll state, font rendering, and ANSI concerns -- termlib handles all of it.
@Composable
private fun TerminalContentArea(
    output: String,
    fontSize: Float,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Text(
            text = output.ifEmpty { "No active session. Tap + to open a new terminal tab." },
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            color = if (output.isEmpty()) TerminalText.copy(alpha = 0.5f) else TerminalText,
            modifier = modifier
                .background(TerminalBackground)
                .padding(8.dp)
                .verticalScroll(scrollState),
        )
    }
}
