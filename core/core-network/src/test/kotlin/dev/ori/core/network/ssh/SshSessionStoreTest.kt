package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.transport.DisconnectListener
import net.schmizz.sshj.transport.Transport
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SshSessionStoreTest {
    private val verifier = mockk<OriDevHostKeyVerifier>(relaxed = true)
    private val store = SshSessionStore(verifier)

    @Test
    fun getSession_unknownId_throwsIOException() {
        val ex = assertThrows(IOException::class.java) { store.getSession("nope") }
        assertThat(ex.message).contains("No active SSH session: nope")
    }

    @Test
    fun getSession_disconnected_removesAndThrows() {
        val client = mockk<SSHClient>(relaxed = true)
        every { client.isConnected } returns false
        injectLive(store, "id1", client, Protocol.SFTP)
        val ex = assertThrows(IOException::class.java) { store.getSession("id1") }
        assertThat(ex.message).contains("SSH session terminated")
        assertThat(assertThrows(IOException::class.java) { store.getSession("id1") }.message)
            .contains("No active")
    }

    @Test
    fun disconnectListener_firesOnEof_removes() {
        val client = mockk<SSHClient>(relaxed = true)
        val transport = mockk<Transport>(relaxed = true)
        every { client.transport } returns transport
        val listener = slot<DisconnectListener>()
        every { transport.disconnectListener = capture(listener) } answers { }
        injectLive(store, "id2", client, Protocol.SCP)
        invokeRegister(store, "id2", client)
        listener.captured.notifyDisconnect(net.schmizz.sshj.common.DisconnectReason.CONNECTION_LOST, "EOF")
        assertThrows(IOException::class.java) { store.getSession("id2") }
    }

    @Test
    fun getSession_returnsProtocol() {
        val client = mockk<SSHClient>(relaxed = true)
        every { client.isConnected } returns true
        injectLive(store, "id3", client, Protocol.SCP)
        val live = store.getSession("id3")
        assertThat(live.protocol).isEqualTo(Protocol.SCP)
        assertThat(live.client).isSameInstanceAs(client)
    }

    private fun injectLive(store: SshSessionStore, id: String, c: SSHClient, p: Protocol) {
        val f = SshSessionStore::class.java.getDeclaredField("sessions")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (f.get(store) as ConcurrentHashMap<String, LiveSession>)[id] =
            LiveSession(c, p, false, java.util.concurrent.atomic.AtomicReference(NameCache.empty()))
    }

    private fun invokeRegister(store: SshSessionStore, id: String, c: SSHClient) {
        SshSessionStore::class.java
            .getDeclaredMethod("registerDisconnectCleanup", String::class.java, SSHClient::class.java)
            .apply { isAccessible = true }.invoke(store, id, c)
    }

    @Test fun probe_sentinelInStdout_returnsTrue() {
        val client = mockBenign(stdout = "BASH_OK\n", stderr = "", exit = 0)
        assertThat(invokeProbe(store, client)).isTrue()
    }

    @Test fun probe_noSentinel_returnsFalse() {
        val client = mockBenign(stdout = "something else\n", stderr = "", exit = 0)
        assertThat(invokeProbe(store, client)).isFalse()
    }

    @Test fun probe_channelOpenFails_returnsFalseFailSafe() {
        val client = mockk<SSHClient>(relaxed = true)
        every { client.startSession() } throws IOException("Channel open failure: MaxSessions exceeded")
        assertThat(invokeProbe(store, client)).isFalse()
    }

    @Test fun probe_forcedCommand_throwsSpecificIOException() {
        val client = mockBenign(
            stdout = "",
            stderr = "This account is restricted to running 'svnserve -t'\n",
            exit = 1,
        )
        val ex = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            invokeProbe(store, client)
        }
        val cause = ex.targetException as IOException
        assertThat(cause.message).contains("forced-command")
    }

    private fun mockBenign(stdout: String, stderr: String, exit: Int): SSHClient {
        val c = mockk<SSHClient>(relaxed = true)
        val s = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
        val cmd = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
        every { c.startSession() } returns s
        every { s.exec(any()) } returns cmd
        every { cmd.inputStream } returns java.io.ByteArrayInputStream(stdout.toByteArray())
        every { cmd.errorStream } returns java.io.ByteArrayInputStream(stderr.toByteArray())
        every { cmd.exitStatus } returns exit
        return c
    }

    private fun invokeProbe(store: SshSessionStore, client: SSHClient): Boolean =
        SshSessionStore::class.java
            .getDeclaredMethod("probeBash", SSHClient::class.java)
            .apply { isAccessible = true }.invoke(store, client) as Boolean

    // --- Migrated from SshClientImplTest (T9 transport-test migration) ---

    @Test
    fun listFiles_clientHasDisconnected_removesSessionFromMapAndThrows() {
        // Regression: SSHJ's Reader thread fires `TransportImpl.die()` on TCP
        // EOF / server timeout, which leaves the SSHClient in `sessions` with
        // `isConnected == false`. The next `getSession` call must throw IOException
        // and remove the stale entry so retries can succeed with a fresh connect().
        // Reproduced 3x in oridev-error-listfiles-right-2026-04-25-22-03/04*.
        val client = mockk<SSHClient>(relaxed = true)
        every { client.isConnected } returns false
        injectLive(store, "stale-id", client, Protocol.SFTP)

        assertThrows(IOException::class.java) { store.getSession("stale-id") }

        // After the throw, the stale entry must be gone from the map so a
        // subsequent getSession("stale-id") gets "No active SSH session" not
        // "SSH session terminated".
        val ex = assertThrows(IOException::class.java) { store.getSession("stale-id") }
        assertThat(ex.message).contains("No active SSH session")
    }

    @Test
    fun openShell_unknownSession_throwsIOExceptionNotIllegalStateException() {
        // Direct regression for oridev-crash-2026-04-26-19-19-53.txt:
        // TerminalViewModel.openNewTab catches `IOException` only, so a
        // getSession throw from a session that was never connected must surface
        // as IOException — otherwise the ViewModel coroutine fails its parent
        // scope and the app crashes on Main with "No active session with id: <UUID>".
        val ex = assertThrows(IOException::class.java) {
            store.getSession("this-session-id-was-never-connected")
        }
        assertThat(ex).isInstanceOf(IOException::class.java)
    }

    @Test
    fun disconnectListener_notifiesOnTransportEof_removesSessionFromMap() {
        // Belt-and-suspenders: even if a caller never reaches getSession again,
        // SSHJ's DisconnectListener should reactively prune the map the moment
        // the Reader thread observes EOF — so a fresh connect() doesn't have to
        // wait for the GC of the old SSHClient to free up resources.
        val client = mockk<SSHClient>(relaxed = true)
        val transport = mockk<Transport>(relaxed = true)
        every { client.transport } returns transport
        val listenerSlot = slot<DisconnectListener>()
        every { transport.disconnectListener = capture(listenerSlot) } answers { }
        injectLive(store, "eof-id", client, Protocol.SFTP)
        invokeRegister(store, "eof-id", client)

        listenerSlot.captured.notifyDisconnect(DisconnectReason.CONNECTION_LOST, "EOF")

        assertThrows(IOException::class.java) { store.getSession("eof-id") }
    }

    @Test
    fun disconnectListener_firesOnTransportEof_alsoNotifiesUpstreamListener() {
        val client = mockk<SSHClient>(relaxed = true)
        val transport = mockk<Transport>(relaxed = true)
        every { client.transport } returns transport
        val listenerSlot = slot<DisconnectListener>()
        every { transport.disconnectListener = capture(listenerSlot) } answers { }
        val notified = AtomicBoolean(false)
        val capturedSessionId = AtomicReference<String?>(null)
        store.setDisconnectListener { sid ->
            notified.set(true)
            capturedSessionId.set(sid)
        }
        injectLive(store, "id-eof", client, Protocol.SCP)
        invokeRegister(store, "id-eof", client)

        listenerSlot.captured.notifyDisconnect(DisconnectReason.CONNECTION_LOST, "EOF")

        assertThat(notified.get()).isTrue()
        assertThat(capturedSessionId.get()).isEqualTo("id-eof")
    }

    @Test
    fun disconnect_explicit_alsoNotifiesUpstreamListener() = kotlinx.coroutines.test.runTest {
        val notified = AtomicBoolean(false)
        val capturedSessionId = AtomicReference<String?>(null)
        store.setDisconnectListener { sid ->
            notified.set(true)
            capturedSessionId.set(sid)
        }
        val client = mockk<SSHClient>(relaxed = true)
        every { client.isConnected } returns true
        injectLive(store, "id-explicit", client, Protocol.SFTP)

        store.disconnect("id-explicit")

        assertThat(notified.get()).isTrue()
        assertThat(capturedSessionId.get()).isEqualTo("id-explicit")
    }

    @Test fun ensureNameCache_calledTwice_runsGetentOnlyOnce() = kotlinx.coroutines.test.runTest {
        // Decision 6 contract: once-per-session lookup. Drives `SshSessionStore` directly
        // (not through the SCP impl) so the cache logic is locked in independently of
        // listFiles' call site.
        val realStore = SshSessionStore(mockk(relaxed = true))
        val client = mockk<SSHClient>(relaxed = true)
        val getentCommandsSeen = mutableListOf<String>()
        every { client.isConnected } returns true
        every { client.startSession() } answers {
            val s = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
            val c = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
            every { s.exec(any()) } answers {
                getentCommandsSeen += firstArg<String>()
                c
            }
            every { c.inputStream } returns java.io.ByteArrayInputStream(
                "marc:x:1000:1000::/home/marc:/bin/bash\n".toByteArray(),
            )
            every { c.errorStream } returns java.io.ByteArrayInputStream(ByteArray(0))
            every { c.exitStatus } returns 0
            s
        }
        val sessionsField = SshSessionStore::class.java.getDeclaredField("sessions")
        sessionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (sessionsField.get(realStore) as java.util.concurrent.ConcurrentHashMap<String, LiveSession>)["s1"] =
            LiveSession(client, Protocol.SCP, false, java.util.concurrent.atomic.AtomicReference(null))

        realStore.ensureNameCache("s1")
        realStore.ensureNameCache("s1")

        // First call runs `getent passwd …` and `getent group …`. Second call hits the cache.
        // We expect exactly TWO exec invocations across both ensureNameCache calls (one for
        // passwd, one for group), not four.
        val getentCount = getentCommandsSeen.count { it.contains("getent") }
        assertThat(getentCount).isEqualTo(2)
    }

    @Test fun ensureNameCache_failedFetch_doesNotRetryOnSecondCall() = kotlinx.coroutines.test.runTest {
        // Decision 6 contract: failed fetch caches an empty NameCache; subsequent listFiles
        // do not re-attempt. Without this guarantee, every listFiles on a server with no
        // /etc/passwd read access pays a /etc/passwd-attempt + /etc/group-attempt cost.
        val realStore = SshSessionStore(mockk(relaxed = true))
        val client = mockk<SSHClient>(relaxed = true)
        every { client.isConnected } returns true
        var execCount = 0
        every { client.startSession() } answers {
            execCount++
            throw java.io.IOException("Channel open failed")
        }
        val sessionsField = SshSessionStore::class.java.getDeclaredField("sessions")
        sessionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (sessionsField.get(realStore) as java.util.concurrent.ConcurrentHashMap<String, LiveSession>)["s1"] =
            LiveSession(client, Protocol.SCP, false, java.util.concurrent.atomic.AtomicReference(null))

        val first = realStore.ensureNameCache("s1")
        val second = realStore.ensureNameCache("s1")

        assertThat(first).isEqualTo(NameCache.empty())
        assertThat(second).isEqualTo(NameCache.empty())
        // First call attempted (and failed) once. Second call must not attempt again.
        assertThat(execCount).isAtMost(2) // tolerate one passwd attempt + one group attempt on first call
    }
}
