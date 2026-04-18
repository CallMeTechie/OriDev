package dev.ori.feature.terminal.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.core.network.ssh.ShellHandle
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.security.clipboard.OriClipboard
import dev.ori.core.ui.theme.TerminalBackground
import dev.ori.core.ui.theme.TerminalText
import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.SessionRecordingRepository
import dev.ori.domain.usecase.AddSnippetUseCase
import dev.ori.domain.usecase.DeleteSnippetUseCase
import dev.ori.domain.usecase.ExportSessionRecordingUseCase
import dev.ori.domain.usecase.GetSnippetsUseCase
import dev.ori.domain.usecase.SendToClaudeUseCase
import dev.ori.domain.usecase.StartSessionRecordingUseCase
import dev.ori.domain.usecase.StopSessionRecordingUseCase
import dev.ori.domain.usecase.UpdateSnippetUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.terminal.TerminalEmulator
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
class TerminalViewModel @Inject constructor(
    private val sshClient: SshClient,
    private val connectionRepository: ConnectionRepository,
    private val getSnippetsUseCase: GetSnippetsUseCase,
    private val addSnippetUseCase: AddSnippetUseCase,
    private val updateSnippetUseCase: UpdateSnippetUseCase,
    private val deleteSnippetUseCase: DeleteSnippetUseCase,
    private val emulatorProvider: TerminalEmulatorProvider,
    private val sessionRecordingRepository: SessionRecordingRepository,
    private val startSessionRecordingUseCase: StartSessionRecordingUseCase,
    private val stopSessionRecordingUseCase: StopSessionRecordingUseCase,
    private val exportSessionRecordingUseCase: ExportSessionRecordingUseCase,
    private val sendToClaudeUseCase: SendToClaudeUseCase,
    private val oriClipboard: OriClipboard,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val shellHandles = ConcurrentHashMap<String, ShellHandle>()
    private val terminalEmulators = ConcurrentHashMap<String, TerminalEmulator>()
    private val codeBlockDetectors = ConcurrentHashMap<String, CodeBlockDetector>()
    private var serviceStarted = false

    init {
        loadSnippets()
    }

    fun onEvent(event: TerminalEvent) {
        when (event) {
            is TerminalEvent.CreateTab -> createTab(event.profileId, event.serverName)
            is TerminalEvent.CloseTab -> closeTab(event.tabId)
            is TerminalEvent.SwitchTab -> switchTab(event.index)
            is TerminalEvent.SendInput -> sendInput(event.data)
            is TerminalEvent.SendText -> sendText(event.text)
            is TerminalEvent.Paste -> paste(event.text)
            is TerminalEvent.ConfirmPaste -> confirmPaste()
            is TerminalEvent.CancelPaste -> cancelPaste()
            is TerminalEvent.CopyToClipboard -> copyToClipboard(event.text)
            is TerminalEvent.CopyClaudeResponse -> copyClaudeResponse(event.text)
            is TerminalEvent.PasteFromSystem -> pasteFromSystem()
            is TerminalEvent.ToggleKeyboard -> toggleKeyboard()
            is TerminalEvent.UpdateSplitRatio -> updateSplitRatio(event.ratio)
            is TerminalEvent.ToggleSnippets -> toggleSnippets()
            is TerminalEvent.ExecuteSnippet -> executeSnippet(event.command)
            is TerminalEvent.ShowAddSnippetDialog -> showAddSnippetDialog()
            is TerminalEvent.ShowEditSnippetDialog -> showEditSnippetDialog(event.snippet)
            is TerminalEvent.HideSnippetDialog -> hideSnippetDialog()
            is TerminalEvent.SaveSnippet -> saveSnippet(event.name, event.command, event.category)
            is TerminalEvent.DeleteSnippet -> deleteSnippetEvent(event.snippet)
            is TerminalEvent.SetSnippetSearchQuery -> _uiState.update { it.copy(snippetSearchQuery = event.query) }
            is TerminalEvent.TogglePreferences -> togglePreferences()
            is TerminalEvent.SetFontSize -> setFontSize(event.size)
            is TerminalEvent.ResizeTerminal -> resizeTerminal(event.cols, event.rows)
            is TerminalEvent.ClearError -> clearError()
            is TerminalEvent.ToggleServerPicker -> toggleServerPicker()
            is TerminalEvent.SelectServer -> selectServer(event.profileId, event.serverName)
            is TerminalEvent.StartRecording -> startRecording()
            is TerminalEvent.StopRecording -> stopRecording()
            is TerminalEvent.ExportRecording -> exportRecording()
            is TerminalEvent.ShowSendToClaude -> showSendToClaude(event.selectedText)
            is TerminalEvent.HideSendToClaude -> hideSendToClaude()
            is TerminalEvent.SetClaudePrompt -> _uiState.update { it.copy(sendToClaudeInput = event.prompt) }
            is TerminalEvent.SendToClaude -> sendToClaude(event.prompt)
            is TerminalEvent.ClearClaudeResponse -> _uiState.update {
                it.copy(claudeResponse = null, claudeError = null)
            }
            is TerminalEvent.ToggleCodeBlocksSheet -> _uiState.update {
                it.copy(showCodeBlocksSheet = !it.showCodeBlocksSheet)
            }
            is TerminalEvent.CopyCodeBlock -> copyCodeBlock(event.blockId)
            is TerminalEvent.OpenCodeBlockInEditor -> _uiState.update {
                it.copy(codeBlockSnackbar = "Open in Editor: Coming soon")
            }
            is TerminalEvent.ClearCodeBlocks -> _uiState.update { it.copy(detectedCodeBlocks = emptyList()) }
            is TerminalEvent.ClearCodeBlockSnackbar -> _uiState.update { it.copy(codeBlockSnackbar = null) }
            is TerminalEvent.ToggleCtrl -> _uiState.update {
                it.copy(modifierState = it.modifierState.copy(ctrl = !it.modifierState.ctrl))
            }
            is TerminalEvent.ToggleAlt -> _uiState.update {
                it.copy(modifierState = it.modifierState.copy(alt = !it.modifierState.alt))
            }
            is TerminalEvent.ToggleStickyModifier -> _uiState.update {
                it.copy(modifierState = it.modifierState.copy(sticky = !it.modifierState.sticky))
            }
        }
    }

    private fun copyCodeBlock(blockId: String) {
        val block = _uiState.value.detectedCodeBlocks.find { it.id == blockId } ?: return
        copyToClipboard(block.content)
        _uiState.update { it.copy(codeBlockSnackbar = "Copied code block") }
    }

    fun getEmulator(tabId: String): TerminalEmulator? {
        return terminalEmulators[tabId]
    }

    private fun createTab(profileId: Long, serverName: String) {
        val tabId = UUID.randomUUID().toString()
        codeBlockDetectors[tabId] = CodeBlockDetector()

        _uiState.update { state ->
            val newTab = TerminalTabState(
                id = tabId,
                profileId = profileId,
                serverName = serverName,
            )
            state.copy(
                tabs = state.tabs + newTab,
                activeTabIndex = state.tabs.size,
            )
        }

        ensureServiceStarted()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = connectionRepository.getProfileById(profileId)
                    ?: throw IllegalStateException("Profile not found: $profileId")

                val session = sshClient.connect(
                    host = profile.host,
                    port = profile.port,
                    username = profile.username,
                    password = profile.credentialRef.toCharArray(),
                )

                val shellHandle = sshClient.openShell(session.sessionId)
                shellHandles[tabId] = shellHandle

                val emulator = emulatorProvider.create(
                    looper = Looper.getMainLooper(),
                    initialRows = DEFAULT_ROWS,
                    initialCols = DEFAULT_COLS,
                    defaultForeground = TerminalText,
                    defaultBackground = TerminalBackground,
                    onKeyboardInput = { bytes ->
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                shellHandle.outputStream.write(bytes)
                                shellHandle.outputStream.flush()
                            } catch (_: IOException) { /* connection lost */ }
                        }
                    },
                    onResize = { dimensions ->
                        shellHandle.onResize(dimensions.columns, dimensions.rows)
                    },
                    onClipboardCopy = { text ->
                        onEvent(TerminalEvent.CopyToClipboard(text))
                    },
                )
                terminalEmulators[tabId] = emulator

                _uiState.update { state ->
                    state.copy(
                        tabs = state.tabs.map { tab ->
                            if (tab.id == tabId) {
                                tab.copy(
                                    isConnected = true,
                                    shellId = shellHandle.shellId,
                                )
                            } else {
                                tab
                            }
                        },
                    )
                }

                startReaderCoroutine(tabId, shellHandle)
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(error = "Failed to connect: ${e.message}")
                }
            }
        }
    }

    private fun startReaderCoroutine(tabId: String, shellHandle: ShellHandle) {
        viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            val emulator = terminalEmulators[tabId]
            try {
                while (true) {
                    val bytesRead = shellHandle.inputStream.read(buffer)
                    if (bytesRead == -1) break
                    emulator?.writeInput(buffer, 0, bytesRead)
                    val detector = codeBlockDetectors[tabId]
                    if (detector != null) {
                        val newBlocks = detector.processChunk(buffer, bytesRead)
                        if (newBlocks.isNotEmpty()) {
                            _uiState.update { state ->
                                val combined = (state.detectedCodeBlocks + newBlocks).takeLast(MAX_CODE_BLOCKS)
                                state.copy(detectedCodeBlocks = combined)
                            }
                        }
                    }
                    uiState.value.activeRecordingId?.let { recId ->
                        sessionRecordingRepository.appendOutput(recId, buffer.copyOf(bytesRead))
                    }
                }
            } catch (_: IOException) {
                // Connection closed or lost
            }

            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map { tab ->
                        if (tab.id == tabId) tab.copy(isConnected = false) else tab
                    },
                    error = "Connection to ${getServerName(tabId)} lost",
                )
            }
        }
    }

    private fun getServerName(tabId: String): String {
        return _uiState.value.tabs.find { it.id == tabId }?.serverName ?: "server"
    }

    private fun sendInput(data: ByteArray) {
        val activeTab = getActiveTab() ?: return
        val handle = shellHandles[activeTab.id] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                handle.outputStream.write(data)
                handle.outputStream.flush()
            } catch (e: IOException) {
                _uiState.update { it.copy(error = "Write failed: ${e.message}") }
            }
        }
    }

    private fun sendText(text: String) {
        val modifier = _uiState.value.modifierState
        val translated = translateForModifiers(text, modifier)
        sendInput(translated)
        // Clear the latched Ctrl/Alt after a single emit unless the user
        // long-pressed to stick them. Sticky is preserved either way.
        if (!modifier.sticky && (modifier.ctrl || modifier.alt)) {
            _uiState.update {
                it.copy(modifierState = it.modifierState.copy(ctrl = false, alt = false))
            }
        }
    }

    private fun paste(text: String) {
        if (text.contains('\n')) {
            _uiState.update { it.copy(showPasteConfirmation = text) }
        } else {
            sendText(text)
        }
    }

    private fun confirmPaste() {
        val text = _uiState.value.showPasteConfirmation ?: return
        sendText(text)
        _uiState.update { it.copy(showPasteConfirmation = null) }
    }

    private fun cancelPaste() {
        _uiState.update { it.copy(showPasteConfirmation = null) }
    }

    private fun copyToClipboard(text: String) {
        _uiState.update { state ->
            val history = (listOf(text) + state.clipboardHistory).take(MAX_CLIPBOARD_HISTORY)
            state.copy(clipboardHistory = history)
        }
        // Terminal output is typically non-sensitive and user-initiated, so
        // skip the 30 s auto-clear. EXTRA_IS_SENSITIVE is still set by
        // OriClipboard so the system preview overlay is redacted.
        oriClipboard.copy(label = "Terminal", text = text, holdForSeconds = 0)
    }

    private fun copyClaudeResponse(text: String) {
        // Claude responses may contain sensitive context the user wants
        // gone — use the default 30 s auto-clear hold.
        oriClipboard.copy(label = "Claude", text = text)
    }

    private fun pasteFromSystem() {
        val clipboardManager = context.getSystemService<ClipboardManager>() ?: return
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        if (text.isNotEmpty()) {
            paste(text)
        }
    }

    private fun closeTab(tabId: String) {
        shellHandles.remove(tabId)?.onClose?.invoke()
        terminalEmulators.remove(tabId)
        codeBlockDetectors.remove(tabId)

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
        // Phase 14 Task 14.3 — clear modifiers on tab switch so a latched
        // Ctrl/Alt on tab A does not bleed into tab B. Sticky is preserved
        // (it is a user preference, not per-tab state).
        _uiState.update {
            it.copy(
                activeTabIndex = index,
                modifierState = it.modifierState.copy(ctrl = false, alt = false),
            )
        }
    }

    private fun toggleKeyboard() {
        _uiState.update { it.copy(isKeyboardVisible = !it.isKeyboardVisible) }
    }

    private fun updateSplitRatio(ratio: Float) {
        _uiState.update { it.copy(splitRatio = ratio.coerceIn(0.2f, 0.8f)) }
    }

    private fun toggleSnippets() {
        _uiState.update { it.copy(showSnippets = !it.showSnippets) }
    }

    private fun executeSnippet(command: String) {
        sendText(command + "\n")
        _uiState.update { it.copy(showSnippets = false) }
    }

    private fun showAddSnippetDialog() {
        _uiState.update { it.copy(editingSnippet = null, showSnippetDialog = true) }
    }

    private fun showEditSnippetDialog(snippet: CommandSnippet) {
        _uiState.update { it.copy(editingSnippet = snippet, showSnippetDialog = true) }
    }

    private fun hideSnippetDialog() {
        _uiState.update { it.copy(editingSnippet = null, showSnippetDialog = false) }
    }

    private fun saveSnippet(name: String, command: String, category: String) {
        val editing = _uiState.value.editingSnippet
        viewModelScope.launch {
            if (editing != null) {
                updateSnippetUseCase(editing.copy(name = name, command = command, category = category))
            } else {
                val current = _uiState.value.snippets
                addSnippetUseCase(
                    CommandSnippet(
                        name = name,
                        command = command,
                        category = category,
                        serverProfileId = null,
                        isWatchQuickCommand = false,
                        sortOrder = current.size,
                    ),
                )
            }
            _uiState.update { it.copy(editingSnippet = null, showSnippetDialog = false) }
        }
    }

    private fun deleteSnippetEvent(snippet: CommandSnippet) {
        viewModelScope.launch {
            deleteSnippetUseCase(snippet)
            _uiState.update { it.copy(editingSnippet = null, showSnippetDialog = false) }
        }
    }

    private fun togglePreferences() {
        _uiState.update { it.copy(showPreferences = !it.showPreferences) }
    }

    private fun setFontSize(size: Float) {
        _uiState.update { it.copy(terminalFontSize = size.coerceIn(10f, 24f)) }
    }

    private fun resizeTerminal(cols: Int, rows: Int) {
        val activeTab = getActiveTab() ?: return
        terminalEmulators[activeTab.id]?.resize(cols, rows)
        // The emulator's onResize callback will propagate to shellHandle.onResize
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun toggleServerPicker() {
        val currentlyShowing = _uiState.value.showServerPicker
        if (!currentlyShowing) {
            viewModelScope.launch {
                connectionRepository.getAllProfiles().collect { profiles ->
                    _uiState.update {
                        it.copy(
                            showServerPicker = true,
                            availableServers = profiles,
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(showServerPicker = false) }
        }
    }

    private fun selectServer(profileId: Long, serverName: String) {
        _uiState.update { it.copy(showServerPicker = false) }
        createTab(profileId, serverName)
    }

    private fun startRecording() {
        val tab = getActiveTab() ?: return
        viewModelScope.launch {
            runCatching { startSessionRecordingUseCase(tab.profileId) }
                .onSuccess { recording ->
                    _uiState.update { it.copy(isRecording = true, activeRecordingId = recording.id) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = "Recording failed: ${err.message}") }
                }
        }
    }

    private fun stopRecording() {
        val recordingId = _uiState.value.activeRecordingId ?: return
        viewModelScope.launch {
            runCatching { stopSessionRecordingUseCase(recordingId) }
                .onFailure { err ->
                    _uiState.update { it.copy(error = "Stop recording failed: ${err.message}") }
                }
            _uiState.update { it.copy(isRecording = false, activeRecordingId = null) }
        }
    }

    private fun exportRecording() {
        val recordingId = _uiState.value.activeRecordingId ?: return
        viewModelScope.launch {
            runCatching { exportSessionRecordingUseCase(recordingId) }
                .onFailure { err ->
                    _uiState.update { it.copy(error = "Export failed: ${err.message}") }
                }
        }
    }

    private fun showSendToClaude(selectedText: String) {
        _uiState.update {
            it.copy(
                showSendToClaude = true,
                sendToClaudeContext = selectedText,
                claudeResponse = null,
                claudeError = null,
            )
        }
    }

    private fun hideSendToClaude() {
        _uiState.update {
            it.copy(
                showSendToClaude = false,
                sendToClaudeContext = "",
                sendToClaudeInput = "",
                claudeResponse = null,
                claudeError = null,
                claudeLoading = false,
            )
        }
    }

    private fun sendToClaude(prompt: String) {
        val ctx = _uiState.value.sendToClaudeContext
        _uiState.update { it.copy(claudeLoading = true, claudeError = null, claudeResponse = null) }
        viewModelScope.launch {
            val result = sendToClaudeUseCase(prompt, ctx.ifEmpty { null })
            if (result.isSuccess) {
                val response = result.getOrNull().orEmpty()
                _uiState.update {
                    it.copy(claudeLoading = false, claudeResponse = response, claudeError = null)
                }
            } else {
                val message = result.exceptionOrNull()?.message ?: "Claude request failed"
                _uiState.update {
                    it.copy(claudeLoading = false, claudeError = message)
                }
            }
        }
    }

    private fun getActiveTab(): TerminalTabState? {
        val state = _uiState.value
        return state.tabs.getOrNull(state.activeTabIndex)
    }

    private fun loadSnippets() {
        viewModelScope.launch {
            getSnippetsUseCase(null).collect { snippets ->
                _uiState.update { it.copy(snippets = snippets) }
            }
        }
    }

    private fun ensureServiceStarted() {
        if (!serviceStarted) {
            val intent = Intent().apply {
                setClassName(context.packageName, "dev.ori.app.service.ConnectionService")
            }
            context.startForegroundService(intent)
            serviceStarted = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        shellHandles.values.forEach { it.onClose() }
        shellHandles.clear()
        terminalEmulators.clear()
        codeBlockDetectors.clear()
    }

    companion object {
        private const val MAX_CLIPBOARD_HISTORY = 10
        private const val DEFAULT_ROWS = 24
        private const val DEFAULT_COLS = 80
        private const val MAX_CODE_BLOCKS = 20
    }
}

/**
 * Phase 14 Task 14.3 — pure modifier translator. Extracted as a
 * top-level `internal` function so the 8 ctrl-letter + 8
 * ctrl-non-letter + Alt-prefix rows can be round-tripped in plain
 * JUnit 5 tests without spinning up a ViewModel.
 *
 * Ctrl mapping (first byte of [text], if [ModifierState.ctrl]):
 * - a-z / A-Z  → `c.code and 0x1F` (Ctrl+C → 0x03)
 * - '@' or ' ' → 0x00 (NUL, tmux-prefix)
 * - '['        → 0x1B (ESC)
 * - '\'        → 0x1C (FS / SIGQUIT)
 * - ']'        → 0x1D (GS / telnet-escape)
 * - '^'        → 0x1E (RS / readline undo)
 * - '_'        → 0x1F (US / readline incremental)
 * - '?'        → 0x7F (DEL, bash ^? alt)
 * - anything else → pass-through as UTF-8, latch was a no-op
 *
 * Alt mapping (applied *after* Ctrl): if [ModifierState.alt] is set,
 * prepend a single ESC (0x1B) byte to the result. This follows the
 * xterm/ANSI convention for Meta keys.
 *
 * [text] normally has length 1 from the on-screen keyboard, but we
 * accept longer strings for robustness (arrow-key escape sequences,
 * pastes from extra-keys row). For multi-char text the Ctrl
 * translation only applies to the first character; the rest pass
 * through unchanged. The Alt prefix is still prepended once.
 */
internal fun translateForModifiers(text: String, modifierState: ModifierState): ByteArray {
    if (text.isEmpty()) return ByteArray(0)

    val ctrlApplied: ByteArray = if (modifierState.ctrl) {
        applyCtrl(text)
    } else {
        text.toByteArray()
    }

    return if (modifierState.alt) {
        byteArrayOf(ESC_BYTE) + ctrlApplied
    } else {
        ctrlApplied
    }
}

private const val ESC_BYTE: Byte = 0x1B
private const val CTRL_MASK = 0x1F

@Suppress("MagicNumber")
private fun applyCtrl(text: String): ByteArray {
    val first = text[0]
    val mapped: Byte? = when {
        first in 'a'..'z' || first in 'A'..'Z' -> (first.code and CTRL_MASK).toByte()
        first == '@' || first == ' ' -> 0x00
        first == '[' -> 0x1B
        first == '\\' -> 0x1C
        first == ']' -> 0x1D
        first == '^' -> 0x1E
        first == '_' -> 0x1F
        first == '?' -> 0x7F
        else -> null
    }

    return if (mapped != null) {
        // Translated: prepend the control byte, then the rest of the
        // string as-is (normally empty for single-char input).
        if (text.length == 1) {
            byteArrayOf(mapped)
        } else {
            byteArrayOf(mapped) + text.substring(1).toByteArray()
        }
    } else {
        // Pass-through: Ctrl + unsupported char is a no-op latch; bytes
        // are the UTF-8 of the unchanged text. The latch is cleared by
        // the caller (TerminalViewModel.sendText) because a non-sticky
        // modifier always clears after any SendText.
        text.toByteArray()
    }
}
