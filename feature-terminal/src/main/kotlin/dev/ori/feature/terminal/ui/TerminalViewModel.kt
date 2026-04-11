package dev.ori.feature.terminal.ui

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.core.network.ssh.ShellHandle
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.ui.theme.TerminalBackground
import dev.ori.core.ui.theme.TerminalText
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.usecase.GetSnippetsUseCase
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
class TerminalViewModel @Inject constructor(
    private val sshClient: SshClient,
    private val connectionRepository: ConnectionRepository,
    private val getSnippetsUseCase: GetSnippetsUseCase,
    private val emulatorProvider: TerminalEmulatorProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val shellHandles = ConcurrentHashMap<String, ShellHandle>()
    private val terminalEmulators = ConcurrentHashMap<String, TerminalEmulator>()
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
            is TerminalEvent.ToggleKeyboard -> toggleKeyboard()
            is TerminalEvent.UpdateSplitRatio -> updateSplitRatio(event.ratio)
            is TerminalEvent.ToggleSnippets -> toggleSnippets()
            is TerminalEvent.ExecuteSnippet -> executeSnippet(event.command)
            is TerminalEvent.TogglePreferences -> togglePreferences()
            is TerminalEvent.SetFontSize -> setFontSize(event.size)
            is TerminalEvent.ResizeTerminal -> resizeTerminal(event.cols, event.rows)
            is TerminalEvent.ClearError -> clearError()
            is TerminalEvent.ToggleServerPicker -> toggleServerPicker()
            is TerminalEvent.SelectServer -> selectServer(event.profileId, event.serverName)
        }
    }

    fun getEmulator(tabId: String): TerminalEmulator? {
        return terminalEmulators[tabId]
    }

    private fun createTab(profileId: Long, serverName: String) {
        val tabId = UUID.randomUUID().toString()

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
        sendInput(text.toByteArray())
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
    }

    private fun closeTab(tabId: String) {
        shellHandles.remove(tabId)?.onClose?.invoke()
        terminalEmulators.remove(tabId)

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
    }

    companion object {
        private const val MAX_CLIPBOARD_HISTORY = 10
        private const val DEFAULT_ROWS = 24
        private const val DEFAULT_COLS = 80
    }
}
