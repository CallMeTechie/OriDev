package dev.ori.core.network.ssh

import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.transport.TransportException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshShellManager @Inject constructor() {

    private val shells = ConcurrentHashMap<String, SshShellSession>()

    /**
     * Opens a PTY shell channel on [client]. Synology DSM 7.2 (and a handful
     * of embedded SSH stacks) intermittently report a transient
     * [ConnectionException]/[TransportException] when a second channel is
     * opened immediately after the first one was closed — typical when the
     * `probeBash` channel in [SshSessionStore.connect] is closed milliseconds
     * before the terminal opens its shell. The single-retry policy below
     * tolerates that race without surfacing a "Broken transport" / "Request
     * failed" crash to the user. Crashes were reproduced in Bug E
     * (Pixel Fold + Synology DSM 7.2): we retry exactly once after a short
     * delay so the server has time to free the slot.
     */
    fun openShell(
        client: net.schmizz.sshj.SSHClient,
        cols: Int = 80,
        rows: Int = 24,
        term: String = "xterm-256color",
        retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
    ): ShellHandle {
        val shellSession = openShellSessionWithRetry(client, cols, rows, term, retryDelayMillis)
        val shellId = UUID.randomUUID().toString()
        shells[shellId] = shellSession

        return ShellHandle(
            shellId = shellId,
            inputStream = shellSession.inputStream,
            outputStream = shellSession.outputStream,
            onResize = { c, r -> shellSession.resize(c, r) },
            onClose = { closeShell(shellId) },
        )
    }

    private fun openShellSessionWithRetry(
        client: net.schmizz.sshj.SSHClient,
        cols: Int,
        rows: Int,
        term: String,
        retryDelayMillis: Long,
    ): SshShellSession {
        return try {
            openShellSessionOnce(client, cols, rows, term)
        } catch (first: ConnectionException) {
            sleepBeforeRetry(retryDelayMillis)
            retryOrRethrow(client, cols, rows, term, first)
        } catch (first: TransportException) {
            sleepBeforeRetry(retryDelayMillis)
            retryOrRethrow(client, cols, rows, term, first)
        }
    }

    private fun retryOrRethrow(
        client: net.schmizz.sshj.SSHClient,
        cols: Int,
        rows: Int,
        term: String,
        first: Exception,
    ): SshShellSession {
        return try {
            openShellSessionOnce(client, cols, rows, term)
        } catch (second: ConnectionException) {
            second.addSuppressed(first)
            throw second
        } catch (second: TransportException) {
            second.addSuppressed(first)
            throw second
        }
    }

    private fun openShellSessionOnce(
        client: net.schmizz.sshj.SSHClient,
        cols: Int,
        rows: Int,
        term: String,
    ): SshShellSession {
        val session = client.startSession()
        try {
            session.allocatePTY(term, cols, rows, 0, 0, emptyMap())
            val shell = session.startShell()
            return SshShellSession(session, shell)
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            // If allocatePTY/startShell fails, the underlying SSHJ Session must be
            // closed so the channel is freed before any retry — otherwise the retry
            // path doubles the open-channel pressure on quirky servers (Synology
            // DSM 7.2 already chokes on two channels in flight, see Bug E). We
            // intentionally catch Throwable so even unchecked exceptions don't
            // leak the channel.
            runCatching { session.close() }
            throw t
        }
    }

    private fun sleepBeforeRetry(retryDelayMillis: Long) {
        if (retryDelayMillis <= 0L) return
        try {
            Thread.sleep(retryDelayMillis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun getSession(shellId: String): SshShellSession? = shells[shellId]

    fun closeShell(shellId: String) {
        shells.remove(shellId)?.close()
    }

    fun closeAllShells() {
        shells.keys.toList().forEach { closeShell(it) }
    }

    fun isShellOpen(shellId: String): Boolean =
        shells[shellId]?.isOpen == true

    companion object {
        /**
         * 200 ms gives Synology DSM 7.2 enough headroom to free the channel
         * slot freed by `probeBash`'s session close (~50–150 ms in field
         * captures); under a real test dispatcher we override this to 0.
         */
        const val DEFAULT_RETRY_DELAY_MILLIS: Long = 200L
    }
}
