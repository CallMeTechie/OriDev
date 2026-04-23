package dev.ori.feature.connections.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.result.appSuccess
import dev.ori.core.security.biometric.CredentialUnlockGate
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ConnectionListViewModel {
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
}
