package dev.ori.feature.terminal.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.core.network.ssh.ShellHandle
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.security.clipboard.OriClipboard
import dev.ori.core.security.crash.NonFatalErrorLogger
import dev.ori.core.ui.theme.TerminalBackground
import dev.ori.core.ui.theme.TerminalText
import dev.ori.data.session.ResumeCoordinator
import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.model.Session
import dev.ori.domain.model.TabMemo
import dev.ori.domain.preferences.KeyboardPreferences
import dev.ori.domain.preferences.SessionResumePreferences
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.SessionRecordingRepository
import dev.ori.domain.repository.SessionRegistry
import dev.ori.domain.usecase.AddSnippetUseCase
import dev.ori.domain.usecase.DeleteSnippetUseCase
import dev.ori.domain.usecase.ExportSessionRecordingUseCase
import dev.ori.domain.usecase.GetSnippetsUseCase
import dev.ori.domain.usecase.SendToClaudeUseCase
import dev.ori.domain.usecase.StartSessionRecordingUseCase
import dev.ori.domain.usecase.StopSessionRecordingUseCase
import dev.ori.domain.usecase.UpdateSnippetUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.terminal.TerminalEmulator
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Resolves which tab ID should receive keystrokes + output. In split
 * mode (>=600dp + >=2 tabs) the active-pane slot wins; otherwise the
 * classic single-pane activeTabIndex applies.
 */
internal fun computeActiveTabId(state: TerminalUiState, isSplitActive: Boolean): String? =
    if (isSplitActive) {
        when (state.activePaneIndex) {
            1 -> state.rightPaneTabId
            else -> state.leftPaneTabId
        }
    } else {
        state.tabs.getOrNull(state.activeTabIndex)?.id
    }

@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList")
class TerminalViewModel @Inject constructor(
    private val sshClient: SshClient,
    private val connectionRepository: ConnectionRepository,
    private val sessionRegistry: SessionRegistry,
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
    private val keyboardPreferences: KeyboardPreferences,
    private val sessionResumePrefs: SessionResumePreferences,
    private val resumeCoordinator: ResumeCoordinator,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    /**
     * Foldable split-terminal Task 6 — cold-start restore-gate latch. Set
     * to `true` for the duration of Phase 1 (pane-id preload from
     * DataStore) + Phase 3 (wait for [ResumeCoordinator.RestoreState.Settled]
     * or 60 s timeout). While true, [resolvePaneAssignments] skips Rule 1
     * (orphan cleanup) so cold-start pane IDs are not nulled before the
     * matching tabs arrive. The `finally` block in [launchRestoreGate]
     * guarantees the latch clears and the reducer runs exactly once, even
     * on total coordinator failure or cancellation.
     */
    private val isRestoringPanes = AtomicBoolean(false)

    @VisibleForTesting
    internal fun isRestoringPanesForTest(): Boolean = isRestoringPanes.get()

    /**
     * Profile list for the [TerminalProfilePicker] bottom-sheet. The
     * picker is hosted by [TerminalScreen] and reads this flow via
     * `collectAsStateWithLifecycle()`; the 5 s `WhileSubscribed` timeout
     * means the Room query is torn down shortly after the user dismisses
     * the sheet but survives a brief config-change round-trip.
     */
    val profiles: StateFlow<List<ServerProfile>> =
        connectionRepository.getAllProfiles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PROFILES_SHARING_TIMEOUT_MS), emptyList())

    private val shellHandles = ConcurrentHashMap<String, ShellHandle>()
    private val terminalEmulators = ConcurrentHashMap<String, TerminalEmulator>()
    private val codeBlockDetectors = ConcurrentHashMap<String, CodeBlockDetector>()

    /**
     * Full Session Persistence Task 8 — per-profile restore latches. The
     * restore observer fires `restoreMissingTabs` only when the profile's
     * latch is still `false`, and flips it to `true` **after** all
     * requested PTYs have been opened. If the restore throws mid-way,
     * the latch stays `false` and the next emission retries.
     *
     * Tracked outside [TerminalUiState] because it is pure bookkeeping
     * (no UI representation) and must survive state snapshots.
     */
    private val restoreLatches = ConcurrentHashMap<Long, AtomicBoolean>()

    /**
     * Full Session Persistence Task 8 — debounced writer job. Cancelled
     * and replaced on every tab-mutating event so only the most recent
     * snapshot is actually written to DataStore. The 1 s debounce
     * collapses bursty events (opening three tabs quickly, switching
     * rapidly between tabs) into a single write.
     */
    private var tabMemoWriteJob: Job? = null

    /**
     * Phase 14 Task 14.5 — incoming [TerminalEvent.ResizeTerminal]
     * dimensions are funneled through this [MutableSharedFlow] and
     * debounced before dispatch. See [debouncedResizes] for the
     * debounce rationale (emoji-sheet wobble, IME layout switches,
     * voice input).
     */
    private val resizeRequests = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = BUFFER_CAPACITY)

    init {
        loadSnippets()
        collectKeyboardMode()
        collectDebouncedResizes()
        installRestoreObserver()
        launchRestoreGate()
    }

    /**
     * Foldable split-terminal Task 6 — cold-start restore-gate. Runs in
     * two phases:
     *
     * - **Phase 1 (preload):** Read persisted pane IDs +
     *   active-pane-index from [SessionResumePreferences] and splat them
     *   onto [TerminalUiState] *before* any tabs arrive. This gives the
     *   reducer the durable intent (which tab was in which pane) so that
     *   once the tabs do land, Rule 3/4 have a preferred target.
     * - **Phase 3 (coordinator wait):** Bounded-wait 60 s for
     *   [ResumeCoordinator.restoreState] to reach [Settled]. The latch
     *   stays `true` for this whole window so [resolvePaneAssignments]'s
     *   Rule 1 (orphan cleanup) does not null out the preloaded pane IDs
     *   while the registry is still bringing sessions up.
     *
     * The `try/finally` guarantees the latch clears and the reducer runs
     * exactly once — even if the coordinator never reaches [Settled], if
     * the scope is cancelled, or if the preload flow throws. The 60 s
     * cap is the worst-case ceiling before the UI must commit to
     * whatever tabs have arrived, regardless of in-flight reconnects.
     */
    private fun launchRestoreGate() {
        viewModelScope.launch {
            try {
                // Phase 1: durable-intent preload from DataStore. Done
                // BEFORE flipping the latch so the initial _uiState update
                // reflects the persisted pane IDs; tests that inspect the
                // state right after createViewModel() + advanceUntilIdle()
                // see the preloaded values.
                val left = sessionResumePrefs.leftPaneTabId.first()
                val right = sessionResumePrefs.rightPaneTabId.first()
                val active = sessionResumePrefs.activePaneIndex.first()
                _uiState.update {
                    it.copy(
                        leftPaneTabId = left,
                        rightPaneTabId = right,
                        activePaneIndex = active,
                    )
                }
                isRestoringPanes.set(true)

                // Phase 3: wait for the coordinator to finish its resume
                // pass (or 60 s — whichever comes first). The coordinator
                // is guaranteed to reach Settled even on total failure
                // (see ResumeCoordinator.runResume try/finally), so the
                // timeout is a defensive ceiling, not the expected path.
                withTimeoutOrNull(RESTORE_TIMEOUT_MS) {
                    resumeCoordinator.restoreState
                        .filter { it == ResumeCoordinator.RestoreState.Settled }
                        .first()
                }
            } finally {
                isRestoringPanes.set(false)
                runReducer()
            }
        }
    }

    /**
     * Foldable split-terminal Task 6 — runs the pure pane reducer
     * ([resolvePaneAssignments]) against the current [_uiState] snapshot
     * and writes the result back. Called once by the restore-gate
     * `finally` block after the latch clears; downstream tab-mutating
     * paths (openNewTab, closeTab, etc.) will also funnel through this
     * reducer in later tasks of the foldable split series.
     */
    private fun runReducer() {
        _uiState.update { state ->
            val pa = resolvePaneAssignments(
                tabs = state.tabs,
                leftPaneTabId = state.leftPaneTabId,
                rightPaneTabId = state.rightPaneTabId,
                activePaneIndex = state.activePaneIndex,
                activeTabIndex = state.activeTabIndex,
                isRestoringPanes = isRestoringPanes.get(),
            )
            state.copy(
                leftPaneTabId = pa.leftPaneTabId,
                rightPaneTabId = pa.rightPaneTabId,
                activePaneIndex = pa.activePaneIndex,
            )
        }
    }

    /**
     * Full Session Persistence Task 8 — restore observer. Joins live
     * [SessionRegistry.openSessions] with the persisted [TabMemo] list
     * so whenever both streams have a value for the same profile id,
     * the missing PTYs are opened exactly once (per-profile latch). The
     * `distinctUntilChangedBy` keeps the observer from re-firing on
     * unrelated ticks (e.g. grace-timer toggles that preserve the
     * profile set), and the latch set **inside** [restoreMissingTabs]
     * *after* successful completion means a cancelled restore leaves
     * the latch resettable for the next emission (test:
     * `cancelled restore leaves latch resettable`).
     */
    private fun installRestoreObserver() {
        sessionRegistry.openSessions
            .combine(sessionResumePrefs.tabMemos) { sessions, memos -> sessions to memos }
            .distinctUntilChangedBy { (sessions, memos) ->
                sessions.map { it.profileId }.toSet() to memos
            }
            .onEach { (sessions, memos) -> restoreMissingTabs(sessions, memos) }
            .launchIn(viewModelScope)
    }

    /**
     * Full Session Persistence Task 8 — open any missing PTYs per
     * persisted [TabMemo], once per profile. The per-profile latch is
     * checked before any work and flipped to `true` only after
     * [openNewTabInternal] has successfully completed `tabCount -
     * currentPtys` iterations, so a mid-restore failure leaves the
     * latch `false` and the observer retries on the next qualifying
     * emission. Option B: user-opened tabs during restore are additive
     * — the `max(0, ...)` subtraction means the restore path only
     * tops up tabs, never closes anything.
     */
    private suspend fun restoreMissingTabs(sessions: List<Session>, memos: List<TabMemo>) {
        val byProfile = sessions.groupBy { it.profileId }
        for (memo in memos) {
            val sessionsForProfile = byProfile[memo.profileId] ?: continue
            val latch = restoreLatches.computeIfAbsent(memo.profileId) { AtomicBoolean(false) }
            if (latch.get()) continue

            val session = sessionsForProfile.first()
            val currentPtys = _uiState.value.tabs.count { it.profileId == memo.profileId }
            val toOpen = (memo.tabCount - currentPtys).coerceAtLeast(0)
            try {
                repeat(toOpen) {
                    openNewTabInternal(session)
                }
                // Latch ONLY after full completion — if openNewTabInternal
                // threw mid-way, we skip the set() below and the next
                // qualifying emission retries.
                latch.set(true)
            } catch (e: IOException) {
                // Swallow here so the outer observer coroutine stays
                // alive; the latch is intentionally left `false` so
                // the next emission retries the restore. The error
                // state is already surfaced by openNewTabInternal.
                NonFatalErrorLogger.log(
                    category = "terminal-restore",
                    throwable = e,
                    contextNote = "profileId=${memo.profileId}",
                )
            }
        }
    }

    fun onEvent(event: TerminalEvent) {
        when (event) {
            is TerminalEvent.OpenProfilePicker -> _uiState.update { it.copy(showProfilePicker = true) }
            is TerminalEvent.DismissProfilePicker -> _uiState.update { it.copy(showProfilePicker = false) }
            is TerminalEvent.OpenNewTab -> openNewTab(event.profileId)
            is TerminalEvent.CloseTab -> closeTab(event.tabId)
            is TerminalEvent.SelectTab -> selectTab(event.tabId)
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
            is TerminalEvent.ResizeTerminal -> onResizeRequested(event.cols, event.rows)
            is TerminalEvent.ClearError -> clearError()
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

    /**
     * PR 2 Chunk 2 — 1 session → N PTY tabs. Connect (or reuse — the
     * registry's per-profile Deferred is idempotent) a session, cancel
     * any grace-timer that a previous "all tabs closed" state armed,
     * open a fresh shell channel on that session, and append a local
     * tab that carries the session id so [closeTab] can identify the
     * last tab for a session and arm the grace-timer again.
     */
    fun openNewTab(profileId: Long) {
        // No explicit Dispatchers.IO here: `SessionRegistry.connect` and
        // `SshClient.openShell` both already `withContext(Dispatchers.IO)`
        // for their blocking work, and staying on Main keeps the tab-list
        // state update ordering deterministic (and lets unit tests that
        // use `Dispatchers.setMain(UnconfinedTestDispatcher())` observe
        // the state synchronously after `advanceUntilIdle()`).
        viewModelScope.launch {
            val session = sessionRegistry.connect(profileId).getOrElse { cause ->
                NonFatalErrorLogger.log(
                    category = "terminal-connect",
                    throwable = cause,
                    contextNote = "profileId=$profileId",
                )
                _uiState.update { it.copy(error = "Failed to connect: ${cause.message}") }
                return@launch
            }

            // Opening a second tab on an already-connected session should
            // not leave the grace timer armed from a previous "all tabs
            // closed" state. Cancel defensively; no-op if nothing was
            // scheduled.
            sessionRegistry.cancelGraceDisconnect(session.id)

            try {
                openNewTabInternal(session)
            } catch (_: IOException) {
                // Error state already set inside openNewTabInternal.
                // Swallowing keeps the user-facing openNewTab coroutine
                // from failing its parent scope.
            }
        }
    }

    /**
     * Full Session Persistence Task 8 — shared PTY-adding core used by
     * both the user-facing [openNewTab] (after it has resolved the
     * [Session] via the registry) and the restore observer (which
     * already has an open [Session] in hand from
     * [SessionRegistry.openSessions]). Keeps the SSH `openShell`,
     * emulator construction, state-update, reader-coroutine, and
     * snapshot-write in one place so restore and user paths cannot
     * drift apart.
     *
     * On `openShell` failure the error is surfaced via
     * [TerminalUiState.error] *and* rethrown as [IOException] so the
     * restore observer's `repeat(toOpen)` loop aborts before flipping
     * the per-profile latch. User-facing [openNewTab] wraps this call
     * in a try/catch so the exception does not tear down the launched
     * coroutine (and therefore doesn't bubble to the uncaught handler).
     */
    private suspend fun openNewTabInternal(session: Session) {
        val shellHandle = try {
            sshClient.openShell(session.id)
        } catch (e: IOException) {
            NonFatalErrorLogger.log(
                category = "terminal-open-shell",
                throwable = e,
                contextNote = "sessionId=${session.id}",
            )
            _uiState.update { it.copy(error = "Failed to open shell: ${e.message}") }
            throw e
        }

        val tabId = UUID.randomUUID().toString()
        codeBlockDetectors[tabId] = CodeBlockDetector()
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
            val newTab = TerminalTabState(
                id = tabId,
                sessionId = session.id,
                profileId = session.profileId,
                serverName = session.profileName,
                isConnected = true,
                shellId = shellHandle.shellId,
            )
            state.copy(
                tabs = state.tabs + newTab,
                activeTabIndex = state.tabs.size,
            )
        }

        startReaderCoroutine(tabId, shellHandle)
        scheduleTabMemoSnapshot()
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

    /**
     * PR 2 Chunk 2 — tear the PTY only. If this was the last tab for
     * the session, arm the registry's 5-s grace timer so the session
     * either auto-disconnects (Files never used it) or stays alive
     * (Files has called [SessionRegistry.markFilesUsed]).
     */
    private fun closeTab(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        shellHandles[tabId]?.onClose?.invoke()
        shellHandles.remove(tabId)
        terminalEmulators.remove(tabId)
        codeBlockDetectors.remove(tabId)

        val remainingForSession = _uiState.value.tabs.count {
            it.sessionId == tab.sessionId && it.id != tabId
        }
        _uiState.update { state ->
            val newTabs = state.tabs.filterNot { it.id == tabId }
            val newIndex = if (state.activeTabIndex >= newTabs.size) {
                (newTabs.size - 1).coerceAtLeast(0)
            } else {
                state.activeTabIndex
            }
            state.copy(tabs = newTabs, activeTabIndex = newIndex)
        }
        if (remainingForSession == 0) {
            sessionRegistry.scheduleGraceDisconnect(tab.sessionId)
        }
        scheduleTabMemoSnapshot()
    }

    /**
     * Full Session Persistence Task 8 — tear down every PTY for
     * [profileId] locally and trigger the snapshot writer so the memo
     * for that profile does not survive as an orphan. The actual SSH
     * session teardown is the caller's responsibility (Connections
     * surface invokes `SessionRegistry.disconnect` separately); this
     * method is limited to the local tab list + snapshot refresh so it
     * can be unit-tested without the registry fake.
     */
    fun disconnectProfile(profileId: Long) {
        val toClose = _uiState.value.tabs.filter { it.profileId == profileId }.map { it.id }
        toClose.forEach { closeTab(it) }
        // closeTab already schedules the snapshot, but call here defensively
        // in case [toClose] was empty and the caller still wants the write
        // (e.g. to flush an in-flight memo from a different path).
        scheduleTabMemoSnapshot()
        restoreLatches.remove(profileId)
    }

    /**
     * PR 2 Chunk 2 — focus a tab by id and notify the registry so Files
     * (and the future remote-pane header) can track the active session.
     * Phase 14 Task 14.3 — clear modifiers on tab switch so a latched
     * Ctrl/Alt on tab A does not bleed into tab B. Sticky is preserved
     * (it is a user preference, not per-tab state).
     */
    private fun selectTab(tabId: String) {
        val idx = _uiState.value.tabs.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        val sessionId = _uiState.value.tabs[idx].sessionId
        sessionRegistry.focus(sessionId)
        _uiState.update {
            it.copy(
                activeTabIndex = idx,
                modifierState = it.modifierState.copy(ctrl = false, alt = false),
            )
        }
        scheduleTabMemoSnapshot()
    }

    /**
     * Full Session Persistence Task 8 — debounced snapshot writer. On
     * every tab-mutating event this is called; the 1 s debounce
     * collapses bursts into a single write. The snapshot is recomputed
     * from the **live** tab list (not from an incremental delta), so
     * orphan memos from a previous run cannot survive a profile
     * disconnect — the recomputed list simply does not contain the
     * disconnected profile's entry.
     *
     * `focusedWithinProfile` is computed as the active tab's index
     * within its own profile's tab list (not the global
     * [TerminalUiState.activeTabIndex]), per the [TabMemo] contract.
     */
    private fun scheduleTabMemoSnapshot() {
        tabMemoWriteJob?.cancel()
        tabMemoWriteJob = viewModelScope.launch {
            delay(TAB_MEMO_DEBOUNCE_MS)
            val state = _uiState.value
            val focusedTabId = state.tabs.getOrNull(state.activeTabIndex)?.id
            val snapshot = state.tabs
                .groupBy { it.profileId }
                .map { (profileId, profileTabs) ->
                    val focusedIdx = profileTabs.indexOfFirst { it.id == focusedTabId }
                        .coerceAtLeast(0)
                    TabMemo(
                        profileId = profileId,
                        tabCount = profileTabs.size,
                        focusedWithinProfile = focusedIdx,
                    )
                }
            sessionResumePrefs.setTabMemos(snapshot)
            sessionResumePrefs.setFocusedProfileId(
                state.tabs.firstOrNull { it.id == focusedTabId }?.profileId,
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

    /**
     * Phase 14 Task 14.5 — drop resize requests onto the debounced
     * flow instead of calling [resizeTerminal] synchronously. Every
     * emoji-sheet open, IME layout switch, and voice-input pop-in
     * would otherwise fire a `window-change` SSH packet and flicker
     * running TUIs on slow links.
     */
    private fun onResizeRequested(cols: Int, rows: Int) {
        resizeRequests.tryEmit(cols to rows)
    }

    private fun resizeTerminal(cols: Int, rows: Int) {
        val activeTab = getActiveTab() ?: return
        terminalEmulators[activeTab.id]?.resize(cols, rows)
        // The emulator's onResize callback will propagate to shellHandle.onResize
    }

    private fun collectKeyboardMode() {
        viewModelScope.launch {
            keyboardPreferences.keyboardModeFlow.collect { mode ->
                _uiState.update { it.copy(keyboardMode = mode) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun collectDebouncedResizes() {
        viewModelScope.launch {
            debouncedResizes(resizeRequests).collect { (cols, rows) ->
                resizeTerminal(cols, rows)
            }
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
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
        private const val PROFILES_SHARING_TIMEOUT_MS = 5_000L

        /**
         * Full Session Persistence Task 8 — debounce window for the
         * tab-memo snapshot writer. Picked so a burst of "open three
         * tabs, then switch between them" coalesces into a single
         * DataStore commit (one commit ≈ one disk sync).
         */
        private const val TAB_MEMO_DEBOUNCE_MS = 1_000L

        /**
         * Foldable split-terminal Task 6 — restore-gate ceiling. If the
         * [ResumeCoordinator] has not reached [ResumeCoordinator.RestoreState.Settled]
         * within 60 s, the latch clears anyway and the reducer runs with
         * whatever tabs have arrived. Picked as "worst-case cold start
         * on cellular with 3 slow profiles" — well above the coordinator's
         * internal 30 s TOFU-prompt timeout so we do not cut off an
         * in-flight host-key dialog.
         */
        private const val RESTORE_TIMEOUT_MS = 60_000L

        /**
         * Phase 14 Task 14.5 — ring-buffer for resize events between the
         * Compose onSizeChanged callback and the debounce collector.
         * Small but non-zero so rapid IME-open/close bursts do not drop
         * the tail event before the debounce window closes.
         */
        internal const val BUFFER_CAPACITY = 16
    }
}

/**
 * Phase 14 Task 14.5 — pure, testable resize-debounce pipeline.
 *
 * Extracted as a top-level `internal` function so [ResizeDebounceTest]
 * can exercise it without instantiating a [TerminalViewModel] (which
 * needs 12+ mocked collaborators). The contract:
 *
 * 1. Debounce by [DEBOUNCE_MILLIS] of stability. If five height
 *    changes land within 100 ms, only the last pair reaches the
 *    downstream collector after the window closes.
 * 2. Drop resizes whose `rows < [MIN_ROWS_FLOOR]`. A 2-row resize
 *    would clobber a running TUI worse than not resizing — better to
 *    let the shell keep its last-known dimensions until the IME
 *    settles.
 *
 * Order of operations matters: filter BEFORE debounce so a trailing
 * "tiny" resize does not win the debounce race and swallow a
 * preceding healthy resize.
 */
@OptIn(FlowPreview::class)
internal fun debouncedResizes(input: Flow<Pair<Int, Int>>): Flow<Pair<Int, Int>> =
    input
        .filter { (_, rows) -> rows >= MIN_ROWS_FLOOR }
        .debounce(DEBOUNCE_MILLIS.milliseconds)

internal const val DEBOUNCE_MILLIS = 200L
internal const val MIN_ROWS_FLOOR = 5

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
    // Empty input + Alt latched: still emit ESC. Some extra-keys callers
    // (Task 14.4) use Alt with the next-key affordance independent of
    // any text payload, and the xterm Meta convention is "ESC then char";
    // emitting a lone ESC keeps that path live.
    if (text.isEmpty()) {
        return if (modifierState.alt) byteArrayOf(ESC_BYTE) else ByteArray(0)
    }

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
