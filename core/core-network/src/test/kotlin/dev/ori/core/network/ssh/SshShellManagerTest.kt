package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Shell
import net.schmizz.sshj.transport.TransportException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SshShellManagerTest {

    private val manager = SshShellManager()

    @Test
    fun `closeShell nonExistentId does not throw`() {
        // Should complete without exception
        manager.closeShell("non-existent-id")
    }

    @Test
    fun `getSession nonExistentId returns null`() {
        val session = manager.getSession("non-existent-id")
        assertThat(session).isNull()
    }

    @Test
    fun `isShellOpen nonExistentId returns false`() {
        val isOpen = manager.isShellOpen("non-existent-id")
        assertThat(isOpen).isFalse()
    }

    // --- Bug K — retry triggers on IllegalStateException("Not connected") ---

    @Test
    fun `openShell IllegalStateException then success retries once and returns ShellHandle`() {
        // Bug K — race window: SSHJ's `client.startSession()` throws
        // IllegalStateException("Not connected") synchronously when the
        // Reader thread tore down the transport between the upstream
        // `isConnected` check and our call. The single-retry policy must
        // tolerate that race the same way it tolerates ConnectionException
        // / TransportException for the Synology DSM channel-open quirk.
        val shellSession = stubShellSession()
        val client = mockk<SSHClient>(relaxed = true)
        var callCount = 0
        every { client.startSession() } answers {
            callCount++
            if (callCount == 1) {
                throw IllegalStateException("Not connected")
            } else {
                shellSession.parent
            }
        }

        val handle = manager.openShell(client, retryDelayMillis = 0L)

        assertThat(handle).isNotNull()
        verify(exactly = 2) { client.startSession() }
    }

    @Test
    fun `openShell IllegalStateException twice rethrows after one retry`() {
        // Bug K — when both attempts fail with `IllegalStateException`, the
        // manager rethrows the second exception with the first as suppressed.
        // SshSftpClientImpl.openShell then translates the rethrow to
        // IOException so the Worker auto-reconnect path (Bug J) handles it.
        val client = mockk<SSHClient>(relaxed = true)
        var callCount = 0
        every { client.startSession() } answers {
            callCount++
            // Distinct instances so `Throwable.addSuppressed` actually records
            // the first attempt — JVM rejects self-suppression.
            throw IllegalStateException("Not connected attempt $callCount")
        }

        val ex = assertThrows(IllegalStateException::class.java) {
            manager.openShell(client, retryDelayMillis = 0L)
        }
        assertThat(ex.message).contains("Not connected attempt 2")
        // First attempt is preserved as a suppressed exception so the
        // crash log retains the full timeline.
        assertThat(ex.suppressed).hasLength(1)
        assertThat(ex.suppressed[0]).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex.suppressed[0].message).contains("attempt 1")
        verify(exactly = 2) { client.startSession() }
    }

    @Test
    fun `openShell ConnectionException then IllegalStateException rethrows IllegalStateException`() {
        // Defense-in-depth: if the server first throws ConnectionException
        // and the retry hits the IllegalStateException race, we surface the
        // second exception (the most-recent) and keep the original as
        // suppressed. Symmetrical to the existing
        // ConnectionException-then-ConnectionException coverage.
        val client = mockk<SSHClient>(relaxed = true)
        var callCount = 0
        every { client.startSession() } answers {
            callCount++
            if (callCount == 1) {
                throw ConnectionException("first failure")
            } else {
                throw IllegalStateException("Not connected")
            }
        }

        val ex = assertThrows(IllegalStateException::class.java) {
            manager.openShell(client, retryDelayMillis = 0L)
        }
        assertThat(ex.message).contains("Not connected")
        assertThat(ex.suppressed).hasLength(1)
        assertThat(ex.suppressed[0]).isInstanceOf(ConnectionException::class.java)
    }

    @Test
    fun `openShell TransportException retries once then succeeds`() {
        // Coverage parity: the existing implementation already retries on
        // TransportException, but no test pinned the behaviour. Add one
        // alongside the new IllegalStateException coverage so future
        // regressions surface immediately.
        val shellSession = stubShellSession()
        val client = mockk<SSHClient>(relaxed = true)
        var callCount = 0
        every { client.startSession() } answers {
            callCount++
            if (callCount == 1) {
                throw TransportException("first failure")
            } else {
                shellSession.parent
            }
        }

        val handle = manager.openShell(client, retryDelayMillis = 0L)

        assertThat(handle).isNotNull()
        verify(exactly = 2) { client.startSession() }
    }

    /**
     * Builds a relaxed SSHJ [Session] mock whose `startShell()` returns a
     * relaxed [Shell] mock — both have `inputStream` / `outputStream`
     * pre-stubbed so [SshShellManager.openShellSessionOnce] succeeds without
     * any real network I/O.
     */
    private fun stubShellSession(): StubShellSession {
        val parent = mockk<Session>(relaxed = true)
        val shell = mockk<Shell>(relaxed = true)
        every { parent.startShell() } returns shell
        every { shell.inputStream } returns java.io.ByteArrayInputStream(ByteArray(0))
        every { shell.outputStream } returns java.io.ByteArrayOutputStream()
        return StubShellSession(parent, shell)
    }

    private data class StubShellSession(val parent: Session, val shell: Shell)
}
