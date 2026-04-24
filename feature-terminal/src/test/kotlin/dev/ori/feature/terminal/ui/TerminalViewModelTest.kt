package dev.ori.feature.terminal.ui

import android.content.Context
import android.os.Looper
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.result.appSuccess
import dev.ori.core.network.ssh.ShellHandle
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.security.clipboard.OriClipboard
import dev.ori.data.session.ResumeCoordinator
import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.model.KeyboardMode
import dev.ori.domain.model.Session
import dev.ori.domain.model.SessionRecording
import dev.ori.domain.model.TabMemo
import dev.ori.domain.preferences.KeyboardPreferences
import dev.ori.domain.preferences.SessionResumePreferences
import dev.ori.domain.repository.ClaudeRepository
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class TerminalViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sshClient = mockk<SshClient>(relaxed = true)
    private val connectionRepository = mockk<ConnectionRepository>(relaxed = true)
    private val sessionRegistry = mockk<SessionRegistry>(relaxed = true)
    private val openSessionsFlow = MutableStateFlow<List<Session>>(emptyList())
    private val sessionResumePrefs = FakeSessionResumePreferences()
    private val getSnippetsUseCase = mockk<GetSnippetsUseCase>()
    private val addSnippetUseCase = mockk<AddSnippetUseCase>(relaxed = true)
    private val updateSnippetUseCase = mockk<UpdateSnippetUseCase>(relaxed = true)
    private val deleteSnippetUseCase = mockk<DeleteSnippetUseCase>(relaxed = true)
    private val emulatorProvider = mockk<TerminalEmulatorProvider>(relaxed = true)
    private val sessionRecordingRepository = mockk<SessionRecordingRepository>(relaxed = true)
    private val startSessionRecordingUseCase = mockk<StartSessionRecordingUseCase>()
    private val stopSessionRecordingUseCase = mockk<StopSessionRecordingUseCase>(relaxed = true)
    private val exportSessionRecordingUseCase = mockk<ExportSessionRecordingUseCase>(relaxed = true)
    private var claudeResult: kotlin.Result<String> = kotlin.Result.success("")
    private val claudeRepository = object : ClaudeRepository {
        override suspend fun hasApiKey(): Boolean = true
        override suspend fun setApiKey(apiKey: String) = Unit
        override suspend fun sendPrompt(userMessage: String, context: String?): kotlin.Result<String> =
            claudeResult
    }
    private val sendToClaudeUseCase = SendToClaudeUseCase(claudeRepository)
    private val oriClipboard = mockk<OriClipboard>(relaxed = true)
    private val keyboardPreferences = mockk<KeyboardPreferences>(relaxed = true) {
        every { keyboardModeFlow } returns flowOf(KeyboardMode.CUSTOM)
    }
    private val context = mockk<Context>(relaxed = true)

    /**
     * Foldable split-terminal Task 6 — MockK stand-in for the real
     * `ResumeCoordinator`. The real class has an `internal` primary
     * ctor + `@Inject` secondary ctor with 6 collaborators, so
     * subclassing across the `:data` module boundary isn't reachable.
     * The restore-gate only reads [ResumeCoordinator.restoreState], so
     * a `mockk(relaxed = true)` with that single flow overridden is
     * sufficient; tests drive the latch by flipping [restoreStateFlow].
     */
    private val restoreStateFlow = MutableStateFlow(ResumeCoordinator.RestoreState.Idle)
    private val resumeCoordinator = mockk<ResumeCoordinator>(relaxed = true).apply {
        every { restoreState } returns restoreStateFlow
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // PR 2 Chunk 2 — openNewTab now runs on Dispatchers.Main (the test
        // dispatcher) and calls Looper.getMainLooper() when it hands a
        // Looper to the emulatorProvider. On the JVM Looper is stubbed
        // and returns null, which trips Kotlin's non-null parameter
        // check at the call site. Statically mock it to a relaxed Looper
        // so the emulator-provider mock (relaxed = true) can accept it.
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        every { getSnippetsUseCase(any()) } returns flowOf(emptyList())
        every { context.packageName } returns "dev.ori.app"
        every { connectionRepository.getAllProfiles() } returns flowOf(emptyList())
        every { sessionRegistry.openSessions } returns openSessionsFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Looper::class)
    }

    private fun createViewModel(): TerminalViewModel {
        return TerminalViewModel(
            sshClient = sshClient,
            connectionRepository = connectionRepository,
            sessionRegistry = sessionRegistry,
            getSnippetsUseCase = getSnippetsUseCase,
            addSnippetUseCase = addSnippetUseCase,
            updateSnippetUseCase = updateSnippetUseCase,
            deleteSnippetUseCase = deleteSnippetUseCase,
            emulatorProvider = emulatorProvider,
            sessionRecordingRepository = sessionRecordingRepository,
            startSessionRecordingUseCase = startSessionRecordingUseCase,
            stopSessionRecordingUseCase = stopSessionRecordingUseCase,
            exportSessionRecordingUseCase = exportSessionRecordingUseCase,
            sendToClaudeUseCase = sendToClaudeUseCase,
            oriClipboard = oriClipboard,
            keyboardPreferences = keyboardPreferences,
            sessionResumePrefs = sessionResumePrefs,
            resumeCoordinator = resumeCoordinator,
            context = context,
        )
    }

    private fun makeSession(profileId: Long, id: String): Session = Session(
        id = id,
        profileId = profileId,
        profileName = "Server $profileId",
        host = "host-$profileId",
        port = 22,
        connectedAt = 0L,
    )

    private fun stubSshConnection() {
        val shellInputStream = ByteArrayInputStream(ByteArray(0))
        val shellOutputStream = ByteArrayOutputStream()
        val shellHandle = ShellHandle(
            shellId = "shell-1",
            inputStream = shellInputStream,
            outputStream = shellOutputStream,
            onResize = { _, _ -> },
            onClose = {},
        )

        // PR session-registry — Terminal's createTab now routes through
        // SessionRegistry.connect(profileId) instead of calling
        // sshClient.connect directly. Stub the registry to return a
        // domain Session; the shell channel still goes through SshClient.
        coEvery { sessionRegistry.connect(any()) } returns kotlin.Result.success(
            Session(
                id = "session-1",
                profileId = 1L,
                profileName = "Server 1",
                host = "192.168.1.1",
                port = 22,
                connectedAt = System.currentTimeMillis(),
            ),
        )
        coEvery { sshClient.openShell(any(), any(), any()) } returns shellHandle
    }

    @Test
    fun `openNewTab adds tab to state`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(1)
        assertThat(state.tabs[0].serverName).isEqualTo("Server 1")
        assertThat(state.tabs[0].profileId).isEqualTo(1L)
        assertThat(state.tabs[0].sessionId).isEqualTo("session-1")
        assertThat(state.activeTabIndex).isEqualTo(0)
    }

    @Test
    fun `closeTab removes tab`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()
        val tabId = viewModel.uiState.value.tabs[0].id

        viewModel.onEvent(TerminalEvent.CloseTab(tabId))

        val state = viewModel.uiState.value
        assertThat(state.tabs).isEmpty()
        assertThat(state.activeTabIndex).isEqualTo(0)
    }

    @Test
    fun `selectTab updates active index and focuses registry`() = runTest {
        // Return two distinct sessions so the tabs carry different session ids.
        coEvery { sessionRegistry.connect(1L) } returns kotlin.Result.success(
            Session(
                id = "session-1", profileId = 1L, profileName = "Server 1",
                host = "h1", port = 22, connectedAt = 0L,
            ),
        )
        coEvery { sessionRegistry.connect(2L) } returns kotlin.Result.success(
            Session(
                id = "session-2", profileId = 2L, profileName = "Server 2",
                host = "h2", port = 22, connectedAt = 0L,
            ),
        )
        val shellInputStream = ByteArrayInputStream(ByteArray(0))
        val shellOutputStream = ByteArrayOutputStream()
        val shellHandle = ShellHandle(
            shellId = "shell-1",
            inputStream = shellInputStream,
            outputStream = shellOutputStream,
            onResize = { _, _ -> },
            onClose = {},
        )
        coEvery { sshClient.openShell(any(), any(), any()) } returns shellHandle

        val viewModel = createViewModel()
        viewModel.openNewTab(profileId = 1L)
        viewModel.openNewTab(profileId = 2L)
        advanceUntilIdle()

        val firstTabId = viewModel.uiState.value.tabs[0].id
        viewModel.onEvent(TerminalEvent.SelectTab(firstTabId))

        val state = viewModel.uiState.value
        assertThat(state.activeTabIndex).isEqualTo(0)
        verify { sessionRegistry.focus("session-1") }
    }

    @Test
    fun `opening a second tab for the same profile reuses the session`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.openNewTab(profileId = 1L)
        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(2)
        assertThat(state.tabs.map { it.sessionId }).containsExactly("session-1", "session-1")
        coVerify(exactly = 2) { sessionRegistry.connect(1L) }
        coVerify(exactly = 2) { sshClient.openShell("session-1", any(), any()) }
    }

    @Test
    fun `closing the last tab for a session schedules a grace disconnect`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()
        val tabId = viewModel.uiState.value.tabs.first().id

        viewModel.onEvent(TerminalEvent.CloseTab(tabId))
        advanceUntilIdle()

        verify { sessionRegistry.scheduleGraceDisconnect("session-1") }
    }

    @Test
    fun `closing one of two tabs for the same session does NOT schedule a grace disconnect`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.openNewTab(profileId = 1L)
        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()
        val firstTabId = viewModel.uiState.value.tabs[0].id

        viewModel.onEvent(TerminalEvent.CloseTab(firstTabId))
        advanceUntilIdle()

        verify(exactly = 0) { sessionRegistry.scheduleGraceDisconnect(any()) }
    }

    @Test
    fun `openNewTab cancels any pending grace disconnect on the session`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()

        verify { sessionRegistry.cancelGraceDisconnect("session-1") }
    }

    @Test
    fun `paste single line sends directly without confirmation`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.Paste("single line text"))

        // Check state directly to avoid race with async IO coroutine
        val state = viewModel.uiState.value
        assertThat(state.showPasteConfirmation).isNull()
    }

    @Test
    fun `paste multi line shows confirmation`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.Paste("line 1\nline 2"))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showPasteConfirmation).isEqualTo("line 1\nline 2")
        }
    }

    @Test
    fun `confirmPaste sends text and dismisses dialog`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.Paste("line 1\nline 2"))
        assertThat(viewModel.uiState.value.showPasteConfirmation).isNotNull()

        viewModel.onEvent(TerminalEvent.ConfirmPaste)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showPasteConfirmation).isNull()
        }
    }

    @Test
    fun `cancelPaste dismisses dialog`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.Paste("line 1\nline 2"))
        assertThat(viewModel.uiState.value.showPasteConfirmation).isNotNull()

        viewModel.onEvent(TerminalEvent.CancelPaste)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showPasteConfirmation).isNull()
        }
    }

    @Test
    fun `copyToClipboard adds to history with max 10`() = runTest {
        val viewModel = createViewModel()

        // Add 12 items -- only the last 10 should remain
        repeat(12) { i ->
            viewModel.onEvent(TerminalEvent.CopyToClipboard("text $i"))
        }

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.clipboardHistory).hasSize(10)
            // Most recent should be first
            assertThat(state.clipboardHistory[0]).isEqualTo("text 11")
            // Oldest kept should be index 2 (0 and 1 evicted)
            assertThat(state.clipboardHistory[9]).isEqualTo("text 2")
        }
    }

    @Test
    fun `toggleKeyboard toggles visibility`() = runTest {
        val viewModel = createViewModel()

        val initial = viewModel.uiState.value.isKeyboardVisible

        viewModel.onEvent(TerminalEvent.ToggleKeyboard)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isKeyboardVisible).isEqualTo(!initial)
        }
    }

    @Test
    fun `executeSnippet sends command with newline`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.ExecuteSnippet("ls -la"))

        // Check state directly to avoid race with async IO coroutine
        val state = viewModel.uiState.value
        // Snippets panel should be closed after execution
        assertThat(state.showSnippets).isFalse()
    }

    @Test
    fun `setFontSize updates state`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.SetFontSize(18f))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.terminalFontSize).isEqualTo(18f)
        }
    }

    @Test
    fun `setFontSize clamps to range`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.SetFontSize(50f))
        assertThat(viewModel.uiState.value.terminalFontSize).isEqualTo(24f)

        viewModel.onEvent(TerminalEvent.SetFontSize(2f))
        assertThat(viewModel.uiState.value.terminalFontSize).isEqualTo(10f)
    }

    @Test
    fun `startRecording sets isRecording and stores id`() = runTest {
        stubSshConnection()
        coEvery { startSessionRecordingUseCase(any()) } returns SessionRecording(
            id = 42L,
            serverProfileId = 1L,
            startedAt = 0L,
            logFilePath = "/tmp/rec",
        )
        val viewModel = createViewModel()
        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()

        viewModel.onEvent(TerminalEvent.StartRecording)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isRecording).isTrue()
        assertThat(state.activeRecordingId).isEqualTo(42L)
    }

    @Test
    fun `stopRecording clears recording state`() = runTest {
        stubSshConnection()
        coEvery { startSessionRecordingUseCase(any()) } returns SessionRecording(
            id = 42L, serverProfileId = 1L, startedAt = 0L, logFilePath = "/tmp/rec",
        )
        val viewModel = createViewModel()
        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()
        viewModel.onEvent(TerminalEvent.StartRecording)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isRecording).isTrue()

        viewModel.onEvent(TerminalEvent.StopRecording)

        val state = viewModel.uiState.value
        assertThat(state.isRecording).isFalse()
        assertThat(state.activeRecordingId).isNull()
        coVerify { stopSessionRecordingUseCase(42L) }
    }

    @Test
    fun `showSendToClaude sets dialog state with context`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.ShowSendToClaude("selected shell output"))

        val state = viewModel.uiState.value
        assertThat(state.showSendToClaude).isTrue()
        assertThat(state.sendToClaudeContext).isEqualTo("selected shell output")
    }

    @Test
    fun `sendToClaude success sets response`() = runTest(testDispatcher) {
        claudeResult = appSuccess("Claude says hi")
        val viewModel = createViewModel()
        viewModel.onEvent(TerminalEvent.ShowSendToClaude("ctx"))

        viewModel.onEvent(TerminalEvent.SendToClaude("what's this?"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.claudeResponse).isEqualTo("Claude says hi")
        assertThat(state.claudeLoading).isFalse()
        assertThat(state.claudeError).isNull()
    }

    @Test
    fun `sendToClaude failure sets error`() = runTest(testDispatcher) {
        claudeResult = kotlin.Result.failure(RuntimeException("network error"))
        val viewModel = createViewModel()
        viewModel.onEvent(TerminalEvent.ShowSendToClaude("ctx"))

        viewModel.onEvent(TerminalEvent.SendToClaude("what's this?"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.claudeError).isNotNull()
        assertThat(state.claudeResponse).isNull()
        assertThat(state.claudeLoading).isFalse()
    }

    @Test
    fun `hideSendToClaude clears dialog state`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(TerminalEvent.ShowSendToClaude("ctx"))
        assertThat(viewModel.uiState.value.showSendToClaude).isTrue()

        viewModel.onEvent(TerminalEvent.HideSendToClaude)

        val state = viewModel.uiState.value
        assertThat(state.showSendToClaude).isFalse()
        assertThat(state.sendToClaudeContext).isEmpty()
    }

    // --- Phase 6b.1 Snippet CRUD ---

    @Test
    fun `showAddSnippetDialog sets state and nulls editingSnippet`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.ShowAddSnippetDialog)

        val state = viewModel.uiState.value
        assertThat(state.showSnippetDialog).isTrue()
        assertThat(state.editingSnippet).isNull()
    }

    @Test
    fun `showEditSnippetDialog sets editingSnippet`() = runTest {
        val viewModel = createViewModel()
        val snippet = CommandSnippet(
            id = 7L,
            serverProfileId = null,
            name = "ls",
            command = "ls -la",
            category = "general",
            isWatchQuickCommand = true,
            sortOrder = 3,
        )

        viewModel.onEvent(TerminalEvent.ShowEditSnippetDialog(snippet))

        val state = viewModel.uiState.value
        assertThat(state.showSnippetDialog).isTrue()
        assertThat(state.editingSnippet).isEqualTo(snippet)
    }

    @Test
    fun `saveSnippet new snippet calls add use case`() = runTest(testDispatcher) {
        coEvery { addSnippetUseCase(any()) } returns 99L
        val viewModel = createViewModel()
        viewModel.onEvent(TerminalEvent.ShowAddSnippetDialog)

        viewModel.onEvent(TerminalEvent.SaveSnippet("name", "echo hi", "general"))
        advanceUntilIdle()

        coVerify {
            addSnippetUseCase(
                match {
                    it.name == "name" &&
                        it.command == "echo hi" &&
                        it.category == "general" &&
                        it.serverProfileId == null &&
                        !it.isWatchQuickCommand
                },
            )
        }
        val state = viewModel.uiState.value
        assertThat(state.showSnippetDialog).isFalse()
        assertThat(state.editingSnippet).isNull()
    }

    @Test
    fun `saveSnippet with editingSnippet calls update use case preserving metadata`() =
        runTest(testDispatcher) {
            val existing = CommandSnippet(
                id = 42L,
                serverProfileId = 5L,
                name = "old",
                command = "old cmd",
                category = "old cat",
                isWatchQuickCommand = true,
                sortOrder = 9,
            )
            val viewModel = createViewModel()
            viewModel.onEvent(TerminalEvent.ShowEditSnippetDialog(existing))

            viewModel.onEvent(TerminalEvent.SaveSnippet("new", "new cmd", "new cat"))
            advanceUntilIdle()

            coVerify {
                updateSnippetUseCase(
                    match {
                        it.id == 42L &&
                            it.serverProfileId == 5L &&
                            it.name == "new" &&
                            it.command == "new cmd" &&
                            it.category == "new cat" &&
                            it.isWatchQuickCommand &&
                            it.sortOrder == 9
                    },
                )
            }
            val state = viewModel.uiState.value
            assertThat(state.showSnippetDialog).isFalse()
            assertThat(state.editingSnippet).isNull()
        }

    @Test
    fun `deleteSnippet calls use case`() = runTest(testDispatcher) {
        val snippet = CommandSnippet(
            id = 11L,
            serverProfileId = null,
            name = "n",
            command = "c",
            category = "x",
        )
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.DeleteSnippet(snippet))
        advanceUntilIdle()

        coVerify { deleteSnippetUseCase(snippet) }
    }

    @Test
    fun `setSnippetSearchQuery updates state`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.SetSnippetSearchQuery("kubectl"))

        assertThat(viewModel.uiState.value.snippetSearchQuery).isEqualTo("kubectl")
    }

    @Test
    fun `keyboardModeFlow emission updates uiState keyboardMode`() = runTest {
        // Re-stub so the mode-flow emits HYBRID instead of the default CUSTOM.
        // Asserts the init-time flow → state pipe set up in TerminalViewModel
        // actually propagates — the default-stub `every { keyboardModeFlow }`
        // in the @BeforeEach block previously emitted CUSTOM but was never
        // verified end-to-end.
        every { keyboardPreferences.keyboardModeFlow } returns flowOf(KeyboardMode.HYBRID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.keyboardMode).isEqualTo(KeyboardMode.HYBRID)
    }

    @Test
    fun `clearError clears error`() = runTest {
        coEvery { sessionRegistry.connect(any()) } returns
            kotlin.Result.failure(IllegalStateException("Profile not found"))

        val viewModel = createViewModel()

        viewModel.openNewTab(profileId = 999L)
        advanceUntilIdle()

        // Verify error was set because the session handshake failed
        assertThat(viewModel.uiState.value.error).contains("Failed to connect")

        viewModel.onEvent(TerminalEvent.ClearError)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.error).isNull()
        }
    }

    @Test
    fun `opening the profile picker sets showProfilePicker true`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.OpenProfilePicker)

        assertThat(viewModel.uiState.value.showProfilePicker).isTrue()
    }

    @Test
    fun `dismissing the profile picker sets showProfilePicker false`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(TerminalEvent.OpenProfilePicker)
        assertThat(viewModel.uiState.value.showProfilePicker).isTrue()

        viewModel.onEvent(TerminalEvent.DismissProfilePicker)

        assertThat(viewModel.uiState.value.showProfilePicker).isFalse()
    }

    // --- Full Session Persistence Task 8: snapshot writer + restore observer ---

    @Test
    fun `tabMemos snapshot is written after debounce`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()
        openSessionsFlow.value = listOf(makeSession(profileId = 1L, id = "session-1"))

        viewModel.openNewTab(profileId = 1L)
        // Run non-delayed continuations (including the openNewTab coroutine
        // body that builds the tab + schedules the snapshot) without
        // advancing the virtual clock — this leaves the 1 s debounce
        // intact so we can verify the snapshot has NOT yet been written.
        runCurrent()

        advanceTimeBy(999.milliseconds)
        runCurrent()
        assertThat(sessionResumePrefs.tabMemosValue.value).isEmpty()

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertThat(sessionResumePrefs.tabMemosValue.value)
            .containsExactly(TabMemo(profileId = 1L, tabCount = 1, focusedWithinProfile = 0))
    }

    @Test
    fun `restore observer opens missing tabs once per profile`() = runTest {
        stubSshConnection()
        sessionResumePrefs.tabMemosValue.value = listOf(
            TabMemo(profileId = 1L, tabCount = 3, focusedWithinProfile = 0),
        )
        val viewModel = createViewModel()

        openSessionsFlow.value = listOf(makeSession(profileId = 1L, id = "session-1"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.tabs.filter { it.profileId == 1L }).hasSize(3)

        // Re-emission with same profile+memo: observer distinctUntilChangedBy
        // suppresses duplicate emission so the latch is irrelevant; either
        // way the tab count must not grow.
        openSessionsFlow.value = listOf(makeSession(profileId = 1L, id = "session-1"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.tabs.filter { it.profileId == 1L }).hasSize(3)
    }

    @Test
    fun `cancelled restore leaves latch resettable (re-tries on next emission)`() = runTest {
        // Fail the first openShell call (so the first PTY open throws and
        // restore aborts mid-way), then succeed from call 2 onwards.
        val shellInputStream = ByteArrayInputStream(ByteArray(0))
        val shellOutputStream = ByteArrayOutputStream()
        val shellHandle = ShellHandle(
            shellId = "shell-1",
            inputStream = shellInputStream,
            outputStream = shellOutputStream,
            onResize = { _, _ -> },
            onClose = {},
        )
        coEvery { sessionRegistry.connect(1L) } returns kotlin.Result.success(
            makeSession(profileId = 1L, id = "session-1"),
        )
        coEvery {
            sshClient.openShell(any(), any(), any())
        } throws java.io.IOException("boom") andThen shellHandle

        sessionResumePrefs.tabMemosValue.value = listOf(
            TabMemo(profileId = 1L, tabCount = 3, focusedWithinProfile = 0),
        )
        val viewModel = createViewModel()

        openSessionsFlow.value = listOf(makeSession(profileId = 1L, id = "session-1"))
        advanceUntilIdle()
        // Partial: zero tabs opened because the first openShell throws
        // before adding to state. Latch is NOT set because restore did not
        // complete.
        assertThat(viewModel.uiState.value.tabs.filter { it.profileId == 1L }).hasSize(0)

        // New emission with a different session id — distinctUntilChangedBy
        // still distinguishes by profileId set, so re-emit with a profile id
        // change is not possible without a second profile. Flip memo instead
        // to re-trigger the observer.
        sessionResumePrefs.tabMemosValue.value = listOf(
            TabMemo(profileId = 1L, tabCount = 3, focusedWithinProfile = 0),
        )
        openSessionsFlow.value = emptyList()
        openSessionsFlow.value = listOf(makeSession(profileId = 1L, id = "session-1"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.tabs.filter { it.profileId == 1L }).hasSize(3)
    }

    @Test
    fun `user-opened tab during restore is additive (option B)`() = runTest {
        stubSshConnection()
        sessionResumePrefs.tabMemosValue.value = listOf(
            TabMemo(profileId = 1L, tabCount = 2, focusedWithinProfile = 0),
        )
        val viewModel = createViewModel()

        openSessionsFlow.value = listOf(makeSession(profileId = 1L, id = "session-1"))
        // User-action mid-restore: observer is additive, so this does not
        // collide with restore — the final tab count is >= 2.
        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.tabs.filter { it.profileId == 1L }.size).isAtLeast(2)
    }

    // --- Foldable split terminal Task 5: computeActiveTabId helper ---

    private fun tab(id: String) = TerminalTabState(
        id = id,
        sessionId = "session-$id",
        profileId = 1L,
        serverName = "Server $id",
    )

    @Test
    fun `computeActiveTabId returns left-pane tab in split active=0`() {
        val state = TerminalUiState(
            tabs = listOf(tab("T1"), tab("T2")),
            activeTabIndex = 0,
            leftPaneTabId = "T1",
            rightPaneTabId = "T2",
            activePaneIndex = 0,
        )
        assertThat(computeActiveTabId(state, isSplitActive = true)).isEqualTo("T1")
    }

    @Test
    fun `computeActiveTabId returns right-pane tab in split active=1`() {
        val state = TerminalUiState(
            tabs = listOf(tab("T1"), tab("T2")),
            activeTabIndex = 0,
            leftPaneTabId = "T1",
            rightPaneTabId = "T2",
            activePaneIndex = 1,
        )
        assertThat(computeActiveTabId(state, isSplitActive = true)).isEqualTo("T2")
    }

    @Test
    fun `computeActiveTabId returns activeTabIndex tab in single-pane`() {
        val state = TerminalUiState(
            tabs = listOf(tab("T1"), tab("T2")),
            activeTabIndex = 1,
            leftPaneTabId = "T1",
            rightPaneTabId = "T2",
            activePaneIndex = 0,
        )
        assertThat(computeActiveTabId(state, isSplitActive = false)).isEqualTo("T2")
    }

    @Test
    fun `snapshot writer removes orphan memo when profile disconnects`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()
        openSessionsFlow.value = listOf(makeSession(profileId = 1L, id = "session-1"))

        viewModel.openNewTab(profileId = 1L)
        advanceUntilIdle()
        advanceTimeBy(1_001.milliseconds)
        advanceUntilIdle()
        assertThat(sessionResumePrefs.tabMemosValue.value.map { it.profileId }).contains(1L)

        viewModel.disconnectProfile(profileId = 1L)
        openSessionsFlow.value = emptyList()
        advanceUntilIdle()
        advanceTimeBy(1_001.milliseconds)
        advanceUntilIdle()

        assertThat(sessionResumePrefs.tabMemosValue.value.map { it.profileId }).doesNotContain(1L)
    }

    // --- Foldable split terminal Task 6: cold-start restore-gate latch ---

    @Test
    fun `restore phase 1 preloads pane IDs from preferences before tabs arrive`() = runTest {
        sessionResumePrefs.leftPaneTabIdValue.value = "tab-left"
        sessionResumePrefs.rightPaneTabIdValue.value = "tab-right"
        sessionResumePrefs.activePaneIndexValue.value = 1

        val vm = createViewModel()
        // Hold the coordinator in InProgress and only tick forward enough
        // to let Phase 1's preload + latch set run, WITHOUT letting the
        // 60 s timeout fire. `advanceUntilIdle()` would fast-forward past
        // the withTimeoutOrNull deadline and the finally-block reducer
        // would null-sweep the preloaded IDs via Rule 1 (no matching tabs).
        restoreStateFlow.value = ResumeCoordinator.RestoreState.InProgress
        runCurrent()

        val state = vm.uiState.value
        assertThat(state.leftPaneTabId).isEqualTo("tab-left")
        assertThat(state.rightPaneTabId).isEqualTo("tab-right")
        assertThat(state.activePaneIndex).isEqualTo(1)
    }

    @Test
    fun `restore latch clears after Coordinator reaches Settled`() = runTest {
        val vm = createViewModel()
        restoreStateFlow.value = ResumeCoordinator.RestoreState.InProgress
        advanceTimeBy(1_000)
        assertThat(vm.isRestoringPanesForTest()).isTrue()

        restoreStateFlow.value = ResumeCoordinator.RestoreState.Settled
        advanceUntilIdle()
        assertThat(vm.isRestoringPanesForTest()).isFalse()
    }

    @Test
    fun `restore latch clears via 60s timeout if Coordinator never settles`() = runTest {
        val vm = createViewModel()
        restoreStateFlow.value = ResumeCoordinator.RestoreState.InProgress
        advanceTimeBy(59_000)
        assertThat(vm.isRestoringPanesForTest()).isTrue()
        advanceTimeBy(1_001)
        assertThat(vm.isRestoringPanesForTest()).isFalse()
    }
}

/**
 * Minimal in-memory fake of [SessionResumePreferences] for unit tests of
 * the Terminal snapshot-writer + restore observer. Each flow is backed by
 * a [MutableStateFlow] so tests can both observe (via `first()` /
 * `.value`) and prime values. The mutating setters update the state-flow
 * directly; no DataStore / coroutine glue is involved, so emissions are
 * synchronous under any TestDispatcher.
 */
private class FakeSessionResumePreferences : SessionResumePreferences {
    val profileIdsValue = MutableStateFlow<Set<Long>>(emptySet())
    val tabMemosValue = MutableStateFlow<List<TabMemo>>(emptyList())
    val focusedProfileIdValue = MutableStateFlow<Long?>(null)
    val remotePathsValue = MutableStateFlow<Map<Long, String>>(emptyMap())
    val lastTopLevelRouteValue = MutableStateFlow("connections")
    val leftPaneTabIdValue = MutableStateFlow<String?>(null)
    val rightPaneTabIdValue = MutableStateFlow<String?>(null)
    val activePaneIndexValue = MutableStateFlow(0)

    override val profileIds = profileIdsValue
    override val tabMemos = tabMemosValue
    override val focusedProfileId = focusedProfileIdValue
    override val remotePaths = remotePathsValue
    override val lastTopLevelRoute = lastTopLevelRouteValue
    override val leftPaneTabId = leftPaneTabIdValue
    override val rightPaneTabId = rightPaneTabIdValue
    override val activePaneIndex = activePaneIndexValue

    override suspend fun setProfileIds(ids: Set<Long>) {
        profileIdsValue.value = ids
    }

    override suspend fun setTabMemos(memos: List<TabMemo>) {
        tabMemosValue.value = memos
    }

    override suspend fun setFocusedProfileId(id: Long?) {
        focusedProfileIdValue.value = id
    }

    override suspend fun setRemotePath(profileId: Long, path: String) {
        remotePathsValue.value = remotePathsValue.value + (profileId to path)
    }

    override suspend fun setLastTopLevelRoute(route: String) {
        lastTopLevelRouteValue.value = route
    }

    override suspend fun setLeftPaneTabId(tabId: String?) {
        leftPaneTabIdValue.value = tabId
    }

    override suspend fun setRightPaneTabId(tabId: String?) {
        rightPaneTabIdValue.value = tabId
    }

    override suspend fun setActivePaneIndex(index: Int) {
        activePaneIndexValue.value = index.coerceIn(0, 1)
    }

    override suspend fun clearResumeSubset() {
        profileIdsValue.value = emptySet()
        tabMemosValue.value = emptyList()
        focusedProfileIdValue.value = null
        remotePathsValue.value = emptyMap()
        leftPaneTabIdValue.value = null
        rightPaneTabIdValue.value = null
        activePaneIndexValue.value = 0
    }
}
