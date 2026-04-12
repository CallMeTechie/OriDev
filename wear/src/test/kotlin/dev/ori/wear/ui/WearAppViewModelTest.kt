package dev.ori.wear.ui

import com.google.common.truth.Truth.assertThat
import dev.ori.domain.model.WearTwoFactorRequest
import dev.ori.wear.sync.WearDataSyncClient
import dev.ori.wear.sync.WearState
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearAppViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var syncClient: WearDataSyncClient
    private lateinit var wearState: WearState
    private lateinit var viewModel: WearAppViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        syncClient = mockk(relaxed = true)
        wearState = WearState()
        viewModel = WearAppViewModel(syncClient, wearState)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewmodel exposes wearState flows directly`() {
        assertThat(viewModel.connections).isSameInstanceAs(wearState.connections)
        assertThat(viewModel.transfers).isSameInstanceAs(wearState.transfers)
        assertThat(viewModel.snippets).isSameInstanceAs(wearState.snippets)
        assertThat(viewModel.phoneReachable).isSameInstanceAs(wearState.isPhoneReachable)
        assertThat(viewModel.lastCommandOutput).isSameInstanceAs(wearState.lastCommandOutput)
        assertThat(viewModel.pending2Fa).isSameInstanceAs(wearState.pending2Fa)
    }

    @Test
    fun `respondTo2Fa calls sync client and clears pending2Fa`() = runTest(dispatcher) {
        wearState.set2FaRequest(
            WearTwoFactorRequest(
                requestId = "req-1",
                profileId = 1L,
                serverName = "Prod",
                host = "10.0.0.1",
                expiresAtMillis = 0L,
            ),
        )

        viewModel.respondTo2Fa("req-1", approved = true)
        advanceUntilIdle()

        coVerify { syncClient.sendTwoFactorResponse("req-1", true) }
        assertThat(wearState.pending2Fa.value).isNull()
    }

    @Test
    fun `sendCommand delegates to sync client`() = runTest(dispatcher) {
        viewModel.sendCommand(profileId = 5L, command = "uptime")
        advanceUntilIdle()

        coVerify { syncClient.sendCommand(5L, "uptime") }
    }

    @Test
    fun `sendPanicDisconnect delegates to sync client`() = runTest(dispatcher) {
        viewModel.sendPanicDisconnect()
        advanceUntilIdle()

        coVerify { syncClient.sendPanicDisconnect() }
    }
}
