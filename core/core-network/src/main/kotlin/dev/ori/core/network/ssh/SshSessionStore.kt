package dev.ori.core.network.ssh

import dev.ori.core.common.model.Protocol
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.DisconnectListener
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal data class LiveSession(
    val client: SSHClient,
    val protocol: Protocol,
    val bashAvailable: Boolean,
    val cacheRef: java.util.concurrent.atomic.AtomicReference<NameCache?> =
        java.util.concurrent.atomic.AtomicReference(null),
)

@Singleton
@Suppress("UnusedPrivateProperty") // hostKeyVerifier used in T7 connect()
internal class SshSessionStore @Inject constructor(
    private val hostKeyVerifier: OriDevHostKeyVerifier,
) {
    private val sessions = ConcurrentHashMap<String, LiveSession>()

    fun getSession(sessionId: String): LiveSession {
        val live = sessions[sessionId] ?: throw IOException("No active SSH session: $sessionId")
        if (!live.client.isConnected) {
            sessions.remove(sessionId, live)
            runCatching { live.client.close() }
            throw IOException("SSH session terminated: $sessionId")
        }
        return live
    }

    suspend fun disconnect(sessionId: String) { sessions.remove(sessionId)?.client?.close() }

    fun isConnected(sessionId: String): Boolean = sessions[sessionId]?.client?.isConnected == true

    @Suppress("UnusedPrivateMember") // called from T7 connect()
    private fun registerDisconnectCleanup(sessionId: String, client: SSHClient) {
        client.transport.disconnectListener = DisconnectListener { _, _ -> sessions.remove(sessionId) }
    }
    // Task 7 will add `connect(...)` and `probeBash(...)`. Task 11 adds `ensureNameCache(...)`.
}
