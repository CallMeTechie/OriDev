package dev.ori.feature.connections.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.result.AppErrorException
import dev.ori.core.common.result.appSuccess
import dev.ori.core.security.biometric.CredentialUnlockGate
import dev.ori.data.session.FailedResume
import dev.ori.data.session.FailedResumeRegistry
import dev.ori.data.session.ResumeCoordinator
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.model.Session
import dev.ori.domain.preferences.SessionResumePreferences
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.SessionRegistry
import dev.ori.domain.usecase.ConnectUseCase
import dev.ori.domain.usecase.DeleteProfileUseCase
import dev.ori.domain.usecase.DisconnectUseCase
import dev.ori.domain.usecase.GetConnectionsUseCase
import dev.ori.domain.usecase.GetFavoriteConnectionsUseCase
import dev.ori.domain.usecase.SaveProfileUseCase
import dev.ori.domain.usecase.TrustHostUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ori.data.session.HostKeyPrompt as CoordinatorHostKeyPrompt

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val getConnections = mockk<GetConnectionsUseCase>()
    private val getFavorites = mockk<GetFavoriteConnectionsUseCase>()
    private val connectUseCase = mockk<ConnectUseCase>()
    private val disconnectUseCase = mockk<DisconnectUseCase>()
    private val deleteProfileUseCase = mockk<DeleteProfileUseCase>()
    private val saveProfileUseCase = mockk<SaveProfileUseCase>()
    private val trustHostUseCase = mockk<TrustHostUseCase>(relaxed = true)
    private val credentialUnlockGate = mockk<CredentialUnlockGate>(relaxed = true)
    private val connectionRepository = mockk<ConnectionRepository>(relaxed = true).also {
        every { it.getActiveConnections() } returns flowOf(emptyList())
        every { it.getAllProfiles() } returns flowOf(emptyList())
    }
    private val sessionRegistry = mockk<SessionRegistry>(relaxed = true).also {
        // PR 2 Section 8 — default stub for the two StateFlows the
        // ViewModel collects on `init` (`activeProfiles` combine). Tests
        // that need non-empty flows override these via `every { ... }`
        // before calling `createViewModel()`.
        every { it.openSessions } returns MutableStateFlow(emptyList<Session>()).asStateFlow()
        every { it.focusedSessionId } returns MutableStateFlow<String?>(null).asStateFlow()
        // PR 3 Section 11 — reconnect-banner combine needs a concrete
        // StateFlow on init; default to empty so existing tests stay
        // deterministic. Tests that exercise the banner override this.
        every { it.persistedProfileIds } returns MutableStateFlow(emptySet<Long>()).asStateFlow()
    }
    private val sessionResumePrefs = mockk<SessionResumePreferences>(relaxed = true)

    // Task 15 — real [FailedResumeRegistry] built via the Hilt `@Inject`
    // constructor. The internal test-scope constructor would give us
    // virtual-time control over the openSessions observer, but it is
    // only visible within `:data`, so cross-module tests use the public
    // constructor. That is safe here: our tests drive `add` / `clear`
    // directly on the registry and never depend on the openSessions
    // observer (the default mock emits an empty list, so the observer
    // filters nothing).
    private lateinit var failedRegistry: FailedResumeRegistry

    // Task 15 — `ResumeCoordinator` has no easy test-constructor path
    // (constructor is internal but requires ServerProfileDao, TrustHost,
    // AutoResume prefs etc.), so we mock it with relaxed = true and
    // stub the only two observable surfaces the VM touches:
    // `hostKeyPrompts` (StateFlow) and `respondToPrompt` (fire-and-forget).
    private val resumeCoordinator = mockk<ResumeCoordinator>(relaxed = true)
    private val coordinatorPrompts = MutableStateFlow<CoordinatorHostKeyPrompt?>(null)

    private val testProfile = ServerProfile(
        id = 1L,
        name = "Test Server",
        host = "192.168.1.1",
        port = 22,
        protocol = Protocol.SSH,
        username = "admin",
        authMethod = AuthMethod.PASSWORD,
        credentialRef = "cred_1",
        isFavorite = false,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Coordinator stub: empty prompts flow by default so the init
        // observer does not flip `uiState.hostKeyPrompt` under the
        // feet of tests that exercise the manual-connect path.
        every { resumeCoordinator.hostKeyPrompts } returns coordinatorPrompts.asStateFlow()
        every { resumeCoordinator.snackbarEvents } returns MutableSharedFlow()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ConnectionListViewModel {
        // Registry must be created lazily per-test so each test gets a
        // fresh openSessions-observer Job.
        failedRegistry = FailedResumeRegistry(sessionRegistry)
        return ConnectionListViewModel(
            getConnections = getConnections,
            getFavorites = getFavorites,
            connectUseCase = connectUseCase,
            disconnectUseCase = disconnectUseCase,
            deleteProfileUseCase = deleteProfileUseCase,
            saveProfileUseCase = saveProfileUseCase,
            trustHostUseCase = trustHostUseCase,
            credentialUnlockGate = credentialUnlockGate,
            connectionRepository = connectionRepository,
            sessionRegistry = sessionRegistry,
            sessionResumePrefs = sessionResumePrefs,
            failedRegistry = failedRegistry,
            resumeCoordinator = resumeCoordinator,
        )
    }

    @Test
    fun `init loads profiles and sets loading to false`() = runTest {
        every { getConnections() } returns flowOf(listOf(testProfile))
        every { getFavorites() } returns flowOf(emptyList())

        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.profiles).containsExactly(testProfile)
            assertThat(state.isLoading).isFalse()
        }
    }

    @Test
    fun `onSearch sets search query in state`() = runTest {
        every { getConnections() } returns flowOf(listOf(testProfile))
        every { getFavorites() } returns flowOf(emptyList())

        val viewModel = createViewModel()

        viewModel.onEvent(ConnectionListEvent.Search("test"))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.searchQuery).isEqualTo("test")
        }
    }

    @Test
    fun `onToggleFavorite calls saveProfile with flipped favorite`() = runTest {
        every { getConnections() } returns flowOf(listOf(testProfile))
        every { getFavorites() } returns flowOf(emptyList())
        coEvery { saveProfileUseCase(any()) } returns appSuccess(1L)

        val viewModel = createViewModel()

        viewModel.onEvent(ConnectionListEvent.ToggleFavorite(testProfile))

        coVerify {
            saveProfileUseCase(testProfile.copy(isFavorite = true))
        }
    }

    @Test
    fun `openProfile — success emits effect with sessionId`() = runTest {
        every { getConnections() } returns flowOf(listOf(testProfile))
        every { getFavorites() } returns flowOf(emptyList())
        coEvery { sessionRegistry.connect(testProfile.id) } returns Result.success(
            Session(
                id = "s-A",
                profileId = testProfile.id,
                profileName = testProfile.name,
                host = testProfile.host,
                port = testProfile.port,
                connectedAt = 1_000L,
                protocol = testProfile.protocol,
            ),
        )

        val viewModel = createViewModel()

        viewModel.openEffects.test {
            viewModel.openProfile(testProfile.id, OpenTarget.TERMINAL)
            val effect = awaitItem()
            assertThat(effect.target).isEqualTo(OpenTarget.TERMINAL)
            assertThat(effect.sessionId).isEqualTo("s-A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openProfile — failure surfaces error on uiState and does not emit effect`() = runTest {
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())
        coEvery { sessionRegistry.connect(1L) } returns Result.failure(
            IllegalStateException("boom"),
        )

        val viewModel = createViewModel()
        viewModel.openProfile(1L, OpenTarget.TERMINAL)
        advanceUntilIdle()

        // UnconfinedTestDispatcher conflates the init-block combine
        // emission and the failure-path update into one StateFlow
        // value by the time the test resumes, so assert on the current
        // snapshot rather than walking Turbine.
        assertThat(viewModel.uiState.value.error).isEqualTo("boom")
    }

    @Test
    fun `activeProfiles derives from registry openSessions filtered into profile list`() =
        runTest {
            val profiles = listOf(testProfile, testProfile.copy(id = 2L, name = "dev-vm"))
            every { getConnections() } returns flowOf(profiles)
            every { getFavorites() } returns flowOf(emptyList())
            every { connectionRepository.getAllProfiles() } returns flowOf(profiles)

            val sessions = MutableStateFlow<List<Session>>(emptyList())
            every { sessionRegistry.openSessions } returns sessions.asStateFlow()

            val viewModel = createViewModel()

            viewModel.activeProfiles.test {
                assertThat(awaitItem()).isEmpty()
                sessions.value = listOf(
                    Session(
                        id = "s-A",
                        profileId = 1L,
                        profileName = testProfile.name,
                        host = testProfile.host,
                        port = testProfile.port,
                        connectedAt = 1_000L,
                        protocol = testProfile.protocol,
                    ),
                )
                assertThat(awaitItem().map { it.id }).containsExactly(1L)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `quickDisconnect — matching profile triggers registry disconnect on its session`() =
        runTest {
            every { getConnections() } returns flowOf(listOf(testProfile))
            every { getFavorites() } returns flowOf(emptyList())
            val session = Session(
                id = "s-A",
                profileId = 1L,
                profileName = testProfile.name,
                host = testProfile.host,
                port = testProfile.port,
                connectedAt = 1_000L,
                protocol = testProfile.protocol,
            )
            every { sessionRegistry.openSessions } returns
                MutableStateFlow(listOf(session)).asStateFlow()
            coEvery { sessionRegistry.disconnect("s-A") } just Runs

            val viewModel = createViewModel()
            viewModel.quickDisconnect(1L)
            advanceUntilIdle()

            coVerify { sessionRegistry.disconnect("s-A") }
        }

    @Test
    fun `quickDisconnect — no session for profile is a no-op`() = runTest {
        every { getConnections() } returns flowOf(listOf(testProfile))
        every { getFavorites() } returns flowOf(emptyList())
        every { sessionRegistry.openSessions } returns
            MutableStateFlow(emptyList<Session>()).asStateFlow()

        val viewModel = createViewModel()
        viewModel.quickDisconnect(42L)
        advanceUntilIdle()

        coVerify(exactly = 0) { sessionRegistry.disconnect(any()) }
    }

    @Test
    fun `reconnectBannerProfiles empty when openSessions non-empty`() = runTest {
        every { getConnections() } returns flowOf(listOf(testProfile))
        every { getFavorites() } returns flowOf(emptyList())
        val persisted = MutableStateFlow(setOf(1L, 2L))
        val open = MutableStateFlow(
            listOf(
                Session(
                    id = "s-A",
                    profileId = 1L,
                    profileName = testProfile.name,
                    host = testProfile.host,
                    port = testProfile.port,
                    connectedAt = 1_000L,
                    protocol = testProfile.protocol,
                ),
            ),
        )
        every { sessionRegistry.persistedProfileIds } returns persisted.asStateFlow()
        every { sessionRegistry.openSessions } returns open.asStateFlow()
        every { connectionRepository.getAllProfiles() } returns flowOf(
            listOf(testProfile, testProfile.copy(id = 2L, name = "dev-vm")),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Registry has a live session, so the banner must stay hidden
        // even though persistence remembers two profileIds.
        assertThat(viewModel.reconnectBannerProfiles.value).isEmpty()
    }

    @Test
    fun `reconnectBannerProfiles populated when openSessions empty and persistence non-empty`() =
        runTest {
            every { getConnections() } returns flowOf(listOf(testProfile))
            every { getFavorites() } returns flowOf(emptyList())
            val profile2 = testProfile.copy(id = 2L, name = "dev-vm")
            val persisted = MutableStateFlow(setOf(1L, 2L))
            val open = MutableStateFlow<List<Session>>(emptyList())
            every { sessionRegistry.persistedProfileIds } returns persisted.asStateFlow()
            every { sessionRegistry.openSessions } returns open.asStateFlow()
            every { connectionRepository.getAllProfiles() } returns flowOf(
                listOf(testProfile, profile2),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.reconnectBannerProfiles.value.map { it.id })
                .containsExactly(1L, 2L)
        }

    @Test
    fun `reconnectAll calls sessionRegistry connect for every persisted profile`() = runTest {
        every { getConnections() } returns flowOf(listOf(testProfile))
        every { getFavorites() } returns flowOf(emptyList())
        val profile2 = testProfile.copy(id = 2L, name = "dev-vm")
        val persisted = MutableStateFlow(setOf(1L, 2L))
        val open = MutableStateFlow<List<Session>>(emptyList())
        every { sessionRegistry.persistedProfileIds } returns persisted.asStateFlow()
        every { sessionRegistry.openSessions } returns open.asStateFlow()
        every { connectionRepository.getAllProfiles() } returns flowOf(
            listOf(testProfile, profile2),
        )
        coEvery { sessionRegistry.connect(any()) } returns Result.success(
            Session(
                id = "s-X",
                profileId = 1L,
                profileName = "X",
                host = "x.local",
                port = 22,
                connectedAt = 1L,
                protocol = testProfile.protocol,
            ),
        )

        val viewModel = createViewModel()
        // Let the combine settle so reconnectBannerProfiles is populated.
        advanceUntilIdle()

        viewModel.reconnectAll()
        advanceUntilIdle()

        coVerify { sessionRegistry.connect(1L) }
        coVerify { sessionRegistry.connect(2L) }
    }

    @Test
    fun `dismissReconnectBanner clears persistence`() = runTest {
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())
        every { sessionRegistry.persistedProfileIds } returns
            MutableStateFlow(emptySet<Long>()).asStateFlow()
        every { sessionRegistry.openSessions } returns
            MutableStateFlow<List<Session>>(emptyList()).asStateFlow()
        coEvery { sessionResumePrefs.clearResumeSubset() } just Runs

        val viewModel = createViewModel()
        viewModel.dismissReconnectBanner()
        advanceUntilIdle()

        coVerify { sessionResumePrefs.clearResumeSubset() }
    }

    @Test
    fun `banner state mirrors FailedResumeRegistry`() = runTest {
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        failedRegistry.add(FailedResume(1L, "A", "timeout"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.failedResume).hasSize(1)
        assertThat(viewModel.uiState.value.failedResume.single().profileId).isEqualTo(1L)
    }

    @Test
    fun `dismissFailedResume clears registry`() = runTest {
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        failedRegistry.add(FailedResume(1L, "A", "x"))
        advanceUntilIdle()

        viewModel.dismissFailedResume()
        advanceUntilIdle()

        assertThat(failedRegistry.failed.value).isEmpty()
    }

    @Test
    fun `coordinator hostKeyPrompt surfaces on uiState with coordinatorPromptId`() = runTest {
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        coordinatorPrompts.value = CoordinatorHostKeyPrompt(
            id = "prompt-1",
            profileId = 1L,
            profileName = "A",
            host = "a.local",
            fingerprint = "SHA256:xyz",
        )
        advanceUntilIdle()

        val prompt = viewModel.uiState.value.hostKeyPrompt
        assertThat(prompt).isNotNull()
        assertThat(prompt?.coordinatorPromptId).isEqualTo("prompt-1")
        assertThat(prompt?.profileId).isEqualTo(1L)
    }

    @Test
    fun `acceptHostKey for coordinator prompt routes through respondToPrompt`() = runTest {
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        coordinatorPrompts.value = CoordinatorHostKeyPrompt(
            id = "prompt-2",
            profileId = 1L,
            profileName = "A",
            host = "a.local",
            fingerprint = "SHA256:xyz",
        )
        advanceUntilIdle()

        viewModel.onEvent(ConnectionListEvent.AcceptHostKey)
        advanceUntilIdle()

        verify { resumeCoordinator.respondToPrompt("prompt-2", accept = true) }
        // Manual trust path must NOT fire for coordinator-originated
        // prompts — the coordinator itself calls TrustHostUseCase on
        // accept (see ResumeCoordinator.enqueueHostKeyPrompt).
        coVerify(exactly = 0) { trustHostUseCase(any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.hostKeyPrompt).isNull()
    }

    @Test
    fun `rejectHostKey for coordinator prompt routes through respondToPrompt`() = runTest {
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        coordinatorPrompts.value = CoordinatorHostKeyPrompt(
            id = "prompt-3",
            profileId = 1L,
            profileName = "A",
            host = "a.local",
            fingerprint = "SHA256:xyz",
        )
        advanceUntilIdle()

        viewModel.onEvent(ConnectionListEvent.RejectHostKey)
        advanceUntilIdle()

        verify { resumeCoordinator.respondToPrompt("prompt-3", accept = false) }
        assertThat(viewModel.uiState.value.hostKeyPrompt).isNull()
    }

    @Test
    fun `openProfile — HostKeyUnknown surfaces TOFU prompt instead of error`() = runTest {
        // Bug P regression test: SSHJ wraps the verifier's
        // `AppErrorException(HostKeyUnknown)` in a TransportException
        // chain. The ViewModel must walk that chain (not simply read
        // `cause.message`) so the user sees the TOFU dialog and can
        // pin the fingerprint, instead of an opaque "Unknown host key
        // for X" error toast that loops them into fail2ban territory.
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())
        val hostKeyError = AppError.HostKeyUnknown(
            host = "server.callmetekkie.de",
            fingerprint = "SHA256:abc",
            keyType = "ssh-ed25519",
        )
        // Mirror the production wrapping: SSHJ's TransportException
        // carries an SSHException carries the verifier's
        // AppErrorException as `cause.cause.cause`.
        val sshException = RuntimeException("SSHException", AppErrorException(hostKeyError))
        val transportException = RuntimeException("TransportException wrapper", sshException)
        coEvery { sessionRegistry.connect(7L) } returns Result.failure(transportException)

        val viewModel = createViewModel()
        viewModel.openProfile(7L, OpenTarget.FILES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.hostKeyPrompt).isNotNull()
        val prompt = state.hostKeyPrompt!!
        assertThat(prompt.profileId).isEqualTo(7L)
        assertThat(prompt.host).isEqualTo("server.callmetekkie.de")
        assertThat(prompt.fingerprint).isEqualTo("SHA256:abc")
        assertThat(prompt.keyType).isEqualTo("ssh-ed25519")
        assertThat(prompt.expectedFingerprint).isNull()
        assertThat(prompt.coordinatorPromptId).isNull()
        assertThat(prompt.pendingOpenTarget).isEqualTo(OpenTarget.FILES)
    }

    @Test
    fun `openProfile — HostKeyMismatch surfaces TOFU prompt with expectedFingerprint`() = runTest {
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())
        val mismatch = AppError.HostKeyMismatch(
            host = "server.callmetekkie.de",
            expectedFingerprint = "SHA256:old",
            actualFingerprint = "SHA256:new",
        )
        val wrapped = RuntimeException("transport", AppErrorException(mismatch))
        coEvery { sessionRegistry.connect(8L) } returns Result.failure(wrapped)

        val viewModel = createViewModel()
        viewModel.openProfile(8L, OpenTarget.TERMINAL)
        advanceUntilIdle()

        val prompt = viewModel.uiState.value.hostKeyPrompt
        assertThat(prompt).isNotNull()
        assertThat(prompt?.fingerprint).isEqualTo("SHA256:new")
        assertThat(prompt?.expectedFingerprint).isEqualTo("SHA256:old")
        assertThat(prompt?.pendingOpenTarget).isEqualTo(OpenTarget.TERMINAL)
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `acceptHostKey for openProfile-originated prompt trusts host and retries openProfile`() =
        runTest {
            // The accept handler must (a) call TrustHostUseCase and
            // (b) re-run `openProfile` so the OpenProfileEffect fires.
            // If we routed through `connect(profileId)` instead, the
            // session would open but the screen would never know to
            // navigate to Terminal/Files.
            every { getConnections() } returns flowOf(emptyList())
            every { getFavorites() } returns flowOf(emptyList())
            val hostKeyError = AppError.HostKeyUnknown(
                host = "server.callmetekkie.de",
                fingerprint = "SHA256:abc",
                keyType = "ssh-ed25519",
            )
            // First call (openProfile triggers TOFU) → failure.
            // Second call (after acceptHostKey re-runs openProfile) → success.
            val successSession = Session(
                id = "s-Z",
                profileId = 9L,
                profileName = "test",
                host = "server.callmetekkie.de",
                port = 22,
                connectedAt = 1_000L,
                protocol = Protocol.SSH,
            )
            coEvery { sessionRegistry.connect(9L) } returnsMany listOf(
                Result.failure(RuntimeException("transport", AppErrorException(hostKeyError))),
                Result.success(successSession),
            )
            coEvery {
                trustHostUseCase("server.callmetekkie.de", any(), "ssh-ed25519", "SHA256:abc")
            } returns appSuccess(Unit)

            val viewModel = createViewModel()

            viewModel.openEffects.test {
                viewModel.openProfile(9L, OpenTarget.TERMINAL)
                advanceUntilIdle()

                // Prompt is now visible — accept it.
                assertThat(viewModel.uiState.value.hostKeyPrompt).isNotNull()
                viewModel.onEvent(ConnectionListEvent.AcceptHostKey)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect.target).isEqualTo(OpenTarget.TERMINAL)
                assertThat(effect.sessionId).isEqualTo("s-Z")
                cancelAndIgnoreRemainingEvents()
            }

            coVerify {
                trustHostUseCase(
                    "server.callmetekkie.de",
                    any(),
                    "ssh-ed25519",
                    "SHA256:abc",
                )
            }
            // Coordinator path must NOT fire for openProfile-originated prompts.
            verify(exactly = 0) { resumeCoordinator.respondToPrompt(any(), any()) }
            assertThat(viewModel.uiState.value.hostKeyPrompt).isNull()
        }

    @Test
    fun `rejectHostKey for openProfile-originated prompt clears prompt without trusting`() =
        runTest {
            every { getConnections() } returns flowOf(emptyList())
            every { getFavorites() } returns flowOf(emptyList())
            val hostKeyError = AppError.HostKeyUnknown(
                host = "server.callmetekkie.de",
                fingerprint = "SHA256:abc",
                keyType = "ssh-ed25519",
            )
            coEvery { sessionRegistry.connect(11L) } returns Result.failure(
                RuntimeException("transport", AppErrorException(hostKeyError)),
            )

            val viewModel = createViewModel()
            viewModel.openProfile(11L, OpenTarget.FILES)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.hostKeyPrompt).isNotNull()

            viewModel.onEvent(ConnectionListEvent.RejectHostKey)
            advanceUntilIdle()

            // No trust + no coordinator ack — pure dismissal.
            coVerify(exactly = 0) { trustHostUseCase(any(), any(), any(), any()) }
            verify(exactly = 0) { resumeCoordinator.respondToPrompt(any(), any()) }
            assertThat(viewModel.uiState.value.hostKeyPrompt).isNull()
            // openProfile emits no effect because the user declined.
            // (No new connect call past the first failed one is expected.)
            coVerify(exactly = 1) { sessionRegistry.connect(11L) }
        }

    @Test
    fun `openProfile — non-host-key failure still routes to error snackbar`() = runTest {
        // Guard against over-eager TOFU: a generic IO error must NOT be
        // mis-routed into the host-key prompt. The chain walk only
        // matches when an AppErrorException with a HostKey* error is
        // actually present in the cause chain.
        every { getConnections() } returns flowOf(emptyList())
        every { getFavorites() } returns flowOf(emptyList())
        coEvery { sessionRegistry.connect(12L) } returns Result.failure(
            RuntimeException("network unreachable"),
        )

        val viewModel = createViewModel()
        viewModel.openProfile(12L, OpenTarget.TERMINAL)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hostKeyPrompt).isNull()
        assertThat(viewModel.uiState.value.error).isEqualTo("network unreachable")
    }
}
