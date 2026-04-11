package dev.ori.feature.connections.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.usecase.ConnectUseCase
import dev.ori.domain.usecase.DeleteProfileUseCase
import dev.ori.domain.usecase.DisconnectUseCase
import dev.ori.domain.usecase.GetConnectionsUseCase
import dev.ori.domain.usecase.GetFavoriteConnectionsUseCase
import dev.ori.domain.usecase.SaveProfileUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val getConnections = mockk<GetConnectionsUseCase>()
    private val getFavorites = mockk<GetFavoriteConnectionsUseCase>()
    private val connectUseCase = mockk<ConnectUseCase>()
    private val disconnectUseCase = mockk<DisconnectUseCase>()
    private val deleteProfileUseCase = mockk<DeleteProfileUseCase>()
    private val saveProfileUseCase = mockk<SaveProfileUseCase>()

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
}
