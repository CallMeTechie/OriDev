package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.DisconnectListener
import net.schmizz.sshj.transport.Transport
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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
}
