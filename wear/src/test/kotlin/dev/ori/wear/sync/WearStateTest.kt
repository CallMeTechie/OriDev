package dev.ori.wear.sync

import com.google.common.truth.Truth.assertThat
import dev.ori.domain.model.WearConnectionPayload
import dev.ori.domain.model.WearSnippetPayload
import dev.ori.domain.model.WearTransferPayload
import dev.ori.domain.model.WearTwoFactorRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WearStateTest {

    private lateinit var state: WearState

    @BeforeEach
    fun setup() {
        state = WearState()
    }

    @Test
    fun `initial state has empty flows and null defaults`() {
        assertThat(state.connections.value).isEmpty()
        assertThat(state.transfers.value).isEmpty()
        assertThat(state.snippets.value).isEmpty()
        assertThat(state.isPhoneReachable.value).isFalse()
        assertThat(state.lastCommandOutput.value).isNull()
        assertThat(state.pending2Fa.value).isNull()
    }

    @Test
    fun `updateConnections updates connections flow`() {
        val payload = WearConnectionPayload(
            profileId = 1L,
            serverName = "Prod",
            host = "10.0.0.1",
            status = "CONNECTED",
            connectedSinceMillis = 1000L,
        )

        state.updateConnections(listOf(payload))

        assertThat(state.connections.value).containsExactly(payload)
    }

    @Test
    fun `updateTransfers updates transfers flow`() {
        val payload = WearTransferPayload(
            transferId = 42L,
            sourcePath = "/src/file.txt",
            destinationPath = "/dest/file.txt",
            direction = "UPLOAD",
            status = "RUNNING",
            totalBytes = 1024L,
            transferredBytes = 256L,
            filesTransferred = 0,
            fileCount = 1,
        )

        state.updateTransfers(listOf(payload))

        assertThat(state.transfers.value).containsExactly(payload)
    }

    @Test
    fun `updateSnippets updates snippets flow`() {
        val payload = WearSnippetPayload(
            id = 7L,
            name = "uptime",
            command = "uptime",
            category = "system",
            serverProfileId = null,
        )

        state.updateSnippets(listOf(payload))

        assertThat(state.snippets.value).containsExactly(payload)
    }

    @Test
    fun `set2FaRequest sets and clears pending2Fa`() {
        val request = WearTwoFactorRequest(
            requestId = "req-1",
            profileId = 1L,
            serverName = "Prod",
            host = "10.0.0.1",
            expiresAtMillis = System.currentTimeMillis() + 30_000L,
        )

        state.set2FaRequest(request)
        assertThat(state.pending2Fa.value).isEqualTo(request)

        state.set2FaRequest(null)
        assertThat(state.pending2Fa.value).isNull()
    }

    @Test
    fun `updatePhoneReachable and setCommandOutput update flows`() {
        state.updatePhoneReachable(true)
        assertThat(state.isPhoneReachable.value).isTrue()

        state.setCommandOutput("hello")
        assertThat(state.lastCommandOutput.value).isEqualTo("hello")
    }
}
