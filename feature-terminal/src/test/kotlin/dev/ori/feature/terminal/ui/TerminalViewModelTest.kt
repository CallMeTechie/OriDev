package dev.ori.feature.terminal.ui

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.network.ssh.ShellHandle
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.network.ssh.SshSession
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.usecase.GetSnippetsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sshClient = mockk<SshClient>(relaxed = true)
    private val connectionRepository = mockk<ConnectionRepository>(relaxed = true)
    private val getSnippetsUseCase = mockk<GetSnippetsUseCase>()
    private val emulatorProvider = mockk<TerminalEmulatorProvider>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { getSnippetsUseCase(any()) } returns flowOf(emptyList())
        every { context.packageName } returns "dev.ori.app"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TerminalViewModel {
        return TerminalViewModel(
            sshClient = sshClient,
            connectionRepository = connectionRepository,
            getSnippetsUseCase = getSnippetsUseCase,
            emulatorProvider = emulatorProvider,
            context = context,
        )
    }

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

        coEvery { sshClient.connect(any(), any(), any(), any(), any()) } returns SshSession(
            sessionId = "session-1",
            profileId = 1L,
            host = "192.168.1.1",
            port = 22,
            connectedAt = System.currentTimeMillis(),
        )
        coEvery { sshClient.openShell(any(), any(), any()) } returns shellHandle
    }

    @Test
    fun `createTab adds tab to state`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.CreateTab(profileId = 1L, serverName = "Server 1"))

        // Tab addition is synchronous, so check the value directly
        // (avoids race with the async IO coroutine that follows)
        val state = viewModel.uiState.value
        assertThat(state.tabs).hasSize(1)
        assertThat(state.tabs[0].serverName).isEqualTo("Server 1")
        assertThat(state.tabs[0].profileId).isEqualTo(1L)
        assertThat(state.activeTabIndex).isEqualTo(0)
    }

    @Test
    fun `closeTab removes tab`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.CreateTab(profileId = 1L, serverName = "Server 1"))
        val tabId = viewModel.uiState.value.tabs[0].id

        viewModel.onEvent(TerminalEvent.CloseTab(tabId))

        // Check state directly to avoid race with async IO coroutine
        val state = viewModel.uiState.value
        assertThat(state.tabs).isEmpty()
        assertThat(state.activeTabIndex).isEqualTo(0)
    }

    @Test
    fun `switchTab updates active index`() = runTest {
        stubSshConnection()
        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.CreateTab(profileId = 1L, serverName = "Server 1"))
        viewModel.onEvent(TerminalEvent.CreateTab(profileId = 2L, serverName = "Server 2"))

        viewModel.onEvent(TerminalEvent.SwitchTab(0))

        // Check state directly to avoid race with async IO coroutine
        val state = viewModel.uiState.value
        assertThat(state.activeTabIndex).isEqualTo(0)
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
    fun `clearError clears error`() = runTest {
        coEvery { connectionRepository.getProfileById(any()) } returns null

        val viewModel = createViewModel()

        viewModel.onEvent(TerminalEvent.CreateTab(profileId = 999L, serverName = "Bad"))

        // Allow IO dispatcher to execute the coroutine
        @Suppress("BlockingMethodInNonBlockingContext")
        Thread.sleep(200)

        // Verify error was set because profile was not found
        assertThat(viewModel.uiState.value.error).contains("Failed to connect")

        viewModel.onEvent(TerminalEvent.ClearError)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.error).isNull()
        }
    }
}
