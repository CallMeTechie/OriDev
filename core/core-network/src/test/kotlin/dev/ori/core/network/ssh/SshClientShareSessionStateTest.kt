package dev.ori.core.network.ssh

import android.content.Context
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Decision 4 contract: [SshSftpClientImpl] and [SshScpClientImpl] must see the SAME
 * sessions when wired with the same [SshSessionStore]. Without this guarantee a future
 * refactor that gives one impl its own private map reintroduces v0.34.2's race condition.
 *
 * Lives here in `core-network/src/test/` (not androidTest) because it only uses pure JVM
 * types ([SshSessionStore], [SshSftpClientImpl], [SshScpClientImpl], MockK, coroutines)
 * and needs no Hilt or Android framework — keeping it in the fast JVM-only suite.
 * Per plan wave-3 revision this test belongs in T18 (both impls must exist post-T10).
 */
class SshClientShareSessionStateTest {

    @Test
    fun bothImpls_shareSessionState_viaSameStore() {
        // Arrange: both impls share a single SshSessionStore instance.
        val verifier = mockk<OriDevHostKeyVerifier>(relaxed = true)
        val sharedStore = SshSessionStore(verifier)
        val client = mockk<SSHClient>(relaxed = true)
        every { client.isConnected } returns true

        // Insert a live session directly via reflection — same helper pattern
        // used throughout SshSessionStoreTest.
        val sessionsField = SshSessionStore::class.java.getDeclaredField("sessions")
        sessionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (sessionsField.get(sharedStore) as ConcurrentHashMap<String, LiveSession>)["shared-id"] =
            LiveSession(client, Protocol.SCP, false, AtomicReference(NameCache.empty()))

        val sftp = SshSftpClientImpl(sharedStore, mockk<Context>(relaxed = true))
        val scp = SshScpClientImpl(sharedStore)

        // Act + Assert: both impls see the session as connected.
        assertThat(runBlocking { sftp.isConnected("shared-id") }).isTrue()
        assertThat(runBlocking { scp.isConnected("shared-id") }).isTrue()

        // Act: disconnect via the SCP impl.
        runBlocking { scp.disconnect("shared-id") }

        // Assert: SFTP impl also sees the session as gone.
        assertThat(runBlocking { sftp.isConnected("shared-id") }).isFalse()
    }
}
