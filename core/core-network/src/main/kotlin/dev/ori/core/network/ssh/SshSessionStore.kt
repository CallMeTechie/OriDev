package dev.ori.core.network.ssh

import dev.ori.core.common.model.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.DisconnectListener
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
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
internal open class SshSessionStore @Inject constructor(
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

    @Suppress("LongParameterList")
    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        password: CharArray?,
        privateKey: ByteArray?,
        protocol: Protocol,
    ): SshSession = withContext(Dispatchers.IO) {
        val client = openTransport(host, port, username, password, privateKey)
        val bashAvailable = probeBash(client)
        val sessionId = UUID.randomUUID().toString()
        // `cacheRef` defaults to AtomicReference(null) — the lazy-populate sentinel.
        // DO NOT pass NameCache.empty() here; that would pre-populate the cache and
        // defeat the once-per-session getent fetch in T11.
        sessions[sessionId] = LiveSession(client, protocol, bashAvailable)
        registerDisconnectCleanup(sessionId, client)
        SshSession(
            sessionId = sessionId,
            profileId = 0L,
            host = host,
            port = port,
            connectedAt = System.currentTimeMillis(),
        )
    }

    internal open fun openTransport(
        host: String,
        port: Int,
        username: String,
        password: CharArray?,
        privateKey: ByteArray?,
    ): SSHClient {
        val client = SSHClient()
        client.addHostKeyVerifier(hostKeyVerifier)
        client.connect(host, port)
        try {
            when {
                privateKey != null -> {
                    val kp = PKCS8KeyFile()
                    kp.init(InputStreamReader(ByteArrayInputStream(privateKey)))
                    client.authPublickey(username, kp)
                }
                password != null -> client.authPassword(username, String(password))
                else -> throw IllegalArgumentException("password or privateKey required")
            }
            client.connection.keepAlive.keepAliveInterval = KEEPALIVE_INTERVAL_SECONDS
            return client
        } catch (e: Exception) {
            client.close()
            throw e
        } finally {
            password?.fill(' ')
        }
    }

    private fun probeBash(client: SSHClient): Boolean {
        val result = try {
            ShellInvocation.run(client, "echo $BASH_PROBE_SENTINEL", bashAvailable = true)
        } catch (_: Exception) {
            return false
        }
        if (FORCED_COMMAND_PATTERN.containsMatchIn(result.stderr)) {
            throw IOException(
                "Server appears to use a forced-command authorized_keys configuration; " +
                    "SCP requires unrestricted shell access.",
            )
        }
        return result.stdout.trim() == BASH_PROBE_SENTINEL
    }

    private fun registerDisconnectCleanup(sessionId: String, client: SSHClient) {
        client.transport.disconnectListener = DisconnectListener { _, _ -> sessions.remove(sessionId) }
    }
    // Task 11 adds `ensureNameCache(...)`.

    companion object {
        private const val KEEPALIVE_INTERVAL_SECONDS = 15
        private const val BASH_PROBE_SENTINEL = "BASH_OK"
        private val FORCED_COMMAND_PATTERN = Regex(
            """This account is restricted|forced[- ]command|command="""",
            RegexOption.IGNORE_CASE,
        )
    }
}
