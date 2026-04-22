package dev.ori.data.session

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.network.ssh.SshSession
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.mapper.toEntity
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.CredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRegistryImplTest {

    private val sshClient = mockk<SshClient>(relaxed = true)
    private val credentialStore = mockk<CredentialStore>(relaxed = true)
    private val serverProfileDao = mockk<ServerProfileDao>(relaxed = true)

    private val testProfile = ServerProfile(
        id = 1L,
        name = "NAS",
        host = "nas.local",
        port = 22,
        protocol = Protocol.SSH,
        username = "admin",
        authMethod = AuthMethod.PASSWORD,
        credentialRef = "kref_test",
    )

    private fun registry() = SessionRegistryImpl(sshClient, credentialStore, serverProfileDao)

    private fun stubProfileA() {
        coEvery { serverProfileDao.getById(1L) } returns testProfile.toEntity()
        coEvery { credentialStore.getPassword("kref_test") } returns "pw".toCharArray()
        coEvery {
            sshClient.connect(
                host = "nas.local",
                port = any(),
                username = any(),
                password = any<CharArray>(),
                privateKey = any(),
            )
        } returns SshSession("s-A", 1L, "nas.local", 22, 1_000L)
    }

    private fun stubProfileB() {
        val profileB = testProfile.copy(id = 2L, name = "dev-vm", host = "dev.local")
        coEvery { serverProfileDao.getById(2L) } returns profileB.toEntity()
        coEvery { credentialStore.getPassword("kref_test") } returns "pw".toCharArray()
        coEvery {
            sshClient.connect(
                host = "dev.local",
                port = any(),
                username = any(),
                password = any<CharArray>(),
                privateKey = any(),
            )
        } returns SshSession("s-B", 2L, "dev.local", 22, 2_000L)
    }

    @Test
    fun `connect establishes session and emits on openSessions`() = runTest(UnconfinedTestDispatcher()) {
        stubProfileA()
        val registry = registry()

        registry.openSessions.test {
            assertThat(awaitItem()).isEmpty()

            val result = registry.connect(1L)
            assertThat(result.isSuccess).isTrue()

            val emitted = awaitItem()
            assertThat(emitted).hasSize(1)
            assertThat(emitted[0].id).isEqualTo("s-A")
            assertThat(emitted[0].profileId).isEqualTo(1L)
            assertThat(emitted[0].profileName).isEqualTo("NAS")
            assertThat(emitted[0].host).isEqualTo("nas.local")
            assertThat(emitted[0].port).isEqualTo(22)
            assertThat(emitted[0].connectedAt).isEqualTo(1_000L)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connect is idempotent — same profile returns existing session without re-handshake`() =
        runTest(UnconfinedTestDispatcher()) {
            stubProfileA()
            val registry = registry()

            val first = registry.connect(1L).getOrThrow()
            val second = registry.connect(1L).getOrThrow()

            assertThat(second.id).isEqualTo(first.id)
            assertThat(registry.openSessions.value).hasSize(1)
            coVerify(exactly = 1) {
                sshClient.connect(any(), any(), any(), any<CharArray>(), any())
            }
        }

    @Test
    fun `focus emits new value when switching between two open sessions`() =
        runTest(UnconfinedTestDispatcher()) {
            stubProfileA()
            stubProfileB()
            val registry = registry()

            registry.connect(1L).getOrThrow()
            registry.connect(2L).getOrThrow()

            registry.focusedSessionId.test {
                // Most recent connect (profile 2 -> s-B) is focused after both connects.
                assertThat(awaitItem()).isEqualTo("s-B")

                registry.focus("s-A")
                assertThat(awaitItem()).isEqualTo("s-A")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `focus is no-op for unknown session id`() = runTest(UnconfinedTestDispatcher()) {
        val registry = registry()

        val before = registry.focusedSessionId.value
        assertThat(before).isNull()

        registry.focus("does-not-exist")

        assertThat(registry.focusedSessionId.value).isEqualTo(before)
    }

    @Test
    fun `disconnect removes session and promotes next as focused`() =
        runTest(UnconfinedTestDispatcher()) {
            stubProfileA()
            stubProfileB()
            val registry = registry()

            registry.connect(1L).getOrThrow()
            registry.connect(2L).getOrThrow()

            assertThat(registry.focusedSessionId.value).isEqualTo("s-B")

            registry.disconnect("s-B")

            assertThat(registry.openSessions.value.map { it.id }).containsExactly("s-A")
            assertThat(registry.focusedSessionId.value).isEqualTo("s-A")
            coVerify { sshClient.disconnect("s-B") }
        }

    @Test
    fun `disconnect of unknown id is a no-op`() = runTest(UnconfinedTestDispatcher()) {
        val registry = registry()

        registry.disconnect("nope")

        assertThat(registry.openSessions.value).isEmpty()
        coVerify(exactly = 0) { sshClient.disconnect(any()) }
    }

    @Test
    fun `cancelConnect cancels in-flight handshake and leaves openSessions empty`() = runTest {
        coEvery { serverProfileDao.getById(1L) } returns testProfile.toEntity()
        coEvery { credentialStore.getPassword("kref_test") } returns "secret".toCharArray()
        // Handshake blocks forever until cancelled — simulates an in-flight SSH connect.
        coEvery {
            sshClient.connect(any(), any(), any(), any<CharArray>(), any())
        } coAnswers {
            awaitCancellation()
        }

        val registry = registry()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            registry.connect(1L)
        }

        registry.cancelConnect(1L)
        testScheduler.advanceUntilIdle()

        assertThat(registry.openSessions.value).isEmpty()
        assertThat(registry.focusedSessionId.value).isNull()
        // Handshake never landed, so sshClient.disconnect is never invoked —
        // SSHJ closes its own socket via the cancelled connect coroutine's
        // internal try/finally (not our concern at the registry layer).
        coVerify(exactly = 0) { sshClient.disconnect(any()) }
        // The pending Deferred should now be either cancelled or completed
        // with a CancellationException — either way, awaiting it inside a
        // try/catch must not surface a regular Exception.
        try {
            pending.await()
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `disconnect with unknown sessionId during in-flight connect is a no-op`() = runTest {
        coEvery { serverProfileDao.getById(1L) } returns testProfile.toEntity()
        coEvery { credentialStore.getPassword("kref_test") } returns "secret".toCharArray()
        coEvery {
            sshClient.connect(any(), any(), any(), any<CharArray>(), any())
        } coAnswers { awaitCancellation() }

        val registry = registry()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { registry.connect(1L) }

        // Caller hasn't yet received a sessionId — there is no valid id
        // to pass to disconnect. A stale one must not throw and must not
        // affect the in-flight handshake.
        registry.disconnect("no-such-session")
        assertThat(registry.openSessions.value).isEmpty()
        coVerify(exactly = 0) { sshClient.disconnect(any()) }

        // Clean up the still-pending handshake so the test scope settles.
        registry.cancelConnect(1L)
        testScheduler.advanceUntilIdle()
        try { pending.await() } catch (_: CancellationException) { /* ok */ }
    }
}
