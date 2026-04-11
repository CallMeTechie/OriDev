package dev.ori.feature.terminal.ui

import dev.ori.domain.model.CommandSnippet

data class TerminalTabState(
    val id: String,
    val profileId: Long,
    val serverName: String,
    val isConnected: Boolean = false,
    val shellId: String? = null,
    val outputVersion: Long = 0,
)

data class TerminalUiState(
    val tabs: List<TerminalTabState> = emptyList(),
    val activeTabIndex: Int = 0,
    val isKeyboardVisible: Boolean = true,
    val splitRatio: Float = 0.6f,
    val clipboardHistory: List<String> = emptyList(),
    val showSnippets: Boolean = false,
    val snippets: List<CommandSnippet> = emptyList(),
    val showPasteConfirmation: String? = null,
    val showPreferences: Boolean = false,
    val terminalFontSize: Float = 14f,
    val error: String? = null,
)

sealed class TerminalEvent {
    data class CreateTab(val profileId: Long, val serverName: String) : TerminalEvent()
    data class CloseTab(val tabId: String) : TerminalEvent()
    data class SwitchTab(val index: Int) : TerminalEvent()
    data class SendInput(val data: ByteArray) : TerminalEvent() {
        override fun equals(other: Any?) = other is SendInput && data.contentEquals(other.data)
        override fun hashCode() = data.contentHashCode()
    }
    data class SendText(val text: String) : TerminalEvent()
    data class Paste(val text: String) : TerminalEvent()
    data object ConfirmPaste : TerminalEvent()
    data object CancelPaste : TerminalEvent()
    data class CopyToClipboard(val text: String) : TerminalEvent()
    data object ToggleKeyboard : TerminalEvent()
    data class UpdateSplitRatio(val ratio: Float) : TerminalEvent()
    data object ToggleSnippets : TerminalEvent()
    data class ExecuteSnippet(val command: String) : TerminalEvent()
    data object TogglePreferences : TerminalEvent()
    data class SetFontSize(val size: Float) : TerminalEvent()
    data class ResizeTerminal(val cols: Int, val rows: Int) : TerminalEvent()
    data object ClearError : TerminalEvent()
}
