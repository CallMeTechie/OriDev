package dev.ori.feature.terminal.ui

import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.model.ServerProfile

data class TerminalTabState(
    val id: String,
    val profileId: Long,
    val serverName: String,
    val isConnected: Boolean = false,
    val shellId: String? = null,
    val outputVersion: Long = 0,
)

/**
 * Phase 14 Task 14.3 — single source of truth for terminal keyboard
 * modifier state. Both [CustomKeyboard] and the upcoming
 * TerminalExtraKeys (Task 14.4) read and write this flag, so a
 * latched Ctrl survives a mode switch between CUSTOM and HYBRID
 * keyboards without drifting.
 *
 * - [ctrl]: next printable char is translated via the Ctrl table in
 *   [TerminalViewModel]. Cleared after emit unless [sticky] is true.
 * - [alt]: next emitted bytes are prefixed with ESC (0x1B). Cleared
 *   after emit unless [sticky] is true.
 * - [sticky]: when true, neither [ctrl] nor [alt] auto-clear after
 *   an emit. The [TerminalEvent.ToggleStickyModifier] event is fired
 *   from the long-press handler on the Ctrl/Alt buttons in the
 *   `TerminalExtraKeys` row (Task 14.4). The CUSTOM-mode keyboard
 *   intentionally has no long-press affordance — Phase 14 Task 14.3
 *   restricts itself to "no behaviour change" for CustomKeyboard.
 */
data class ModifierState(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val sticky: Boolean = false,
)

data class TerminalUiState(
    val tabs: List<TerminalTabState> = emptyList(),
    val activeTabIndex: Int = 0,
    val isKeyboardVisible: Boolean = true,
    val splitRatio: Float = 0.5f,
    val clipboardHistory: List<String> = emptyList(),
    val showSnippets: Boolean = false,
    val snippets: List<CommandSnippet> = emptyList(),
    val snippetSearchQuery: String = "",
    val editingSnippet: CommandSnippet? = null,
    val showSnippetDialog: Boolean = false,
    val showPasteConfirmation: String? = null,
    val showPreferences: Boolean = false,
    val terminalFontSize: Float = 14f,
    val error: String? = null,
    val showServerPicker: Boolean = false,
    val availableServers: List<ServerProfile> = emptyList(),
    val isRecording: Boolean = false,
    val activeRecordingId: Long? = null,
    val showSendToClaude: Boolean = false,
    val sendToClaudeContext: String = "",
    val sendToClaudeInput: String = "",
    val claudeResponse: String? = null,
    val claudeLoading: Boolean = false,
    val claudeError: String? = null,
    val detectedCodeBlocks: List<DetectedCodeBlock> = emptyList(),
    val showCodeBlocksSheet: Boolean = false,
    val codeBlockSnackbar: String? = null,
    val modifierState: ModifierState = ModifierState(),
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
    data class CopyClaudeResponse(val text: String) : TerminalEvent()
    data object PasteFromSystem : TerminalEvent()
    data object ToggleKeyboard : TerminalEvent()
    data class UpdateSplitRatio(val ratio: Float) : TerminalEvent()
    data object ToggleSnippets : TerminalEvent()
    data class ExecuteSnippet(val command: String) : TerminalEvent()
    data object ShowAddSnippetDialog : TerminalEvent()
    data class ShowEditSnippetDialog(val snippet: CommandSnippet) : TerminalEvent()
    data object HideSnippetDialog : TerminalEvent()
    data class SaveSnippet(val name: String, val command: String, val category: String) : TerminalEvent()
    data class DeleteSnippet(val snippet: CommandSnippet) : TerminalEvent()
    data class SetSnippetSearchQuery(val query: String) : TerminalEvent()
    data object TogglePreferences : TerminalEvent()
    data class SetFontSize(val size: Float) : TerminalEvent()
    data class ResizeTerminal(val cols: Int, val rows: Int) : TerminalEvent()
    data object ClearError : TerminalEvent()
    data object ToggleServerPicker : TerminalEvent()
    data class SelectServer(val profileId: Long, val serverName: String) : TerminalEvent()
    data object StartRecording : TerminalEvent()
    data object StopRecording : TerminalEvent()
    data object ExportRecording : TerminalEvent()
    data class ShowSendToClaude(val selectedText: String) : TerminalEvent()
    data object HideSendToClaude : TerminalEvent()
    data class SetClaudePrompt(val prompt: String) : TerminalEvent()
    data class SendToClaude(val prompt: String) : TerminalEvent()
    data object ClearClaudeResponse : TerminalEvent()
    data object ToggleCodeBlocksSheet : TerminalEvent()
    data class CopyCodeBlock(val blockId: String) : TerminalEvent()
    data class OpenCodeBlockInEditor(val blockId: String) : TerminalEvent()
    data object ClearCodeBlocks : TerminalEvent()
    data object ClearCodeBlockSnackbar : TerminalEvent()

    // Phase 14 Task 14.3 — modifier state events (single source of truth
    // consumed by CustomKeyboard and the upcoming TerminalExtraKeys).
    data object ToggleCtrl : TerminalEvent()
    data object ToggleAlt : TerminalEvent()
    data object ToggleStickyModifier : TerminalEvent()
}
