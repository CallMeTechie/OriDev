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
     *
     * Bug K fix — extends the retry trigger set to [IllegalStateException]
     * with message "Not connected". SSHJ's `SSHClient.startSession()`
     * throws this synchronously when the Reader thread tore down the
     * transport between `SshSessionStore.getSession`'s `isConnected`
     * check and our `client.startSession()` call (race observed in
     * oridev-crash-2026-04-27-20-28-01.txt). Treating it as retryable
     * gives the Worker auto-reconnect path a chance to recover; the
     * second attempt sees the disconnected client and re-throws which
     * `SshSftpClientImpl.openShell` then translates to IOException.
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
        } catch (first: IllegalStateException) {
            // Bug K — race between getSession's `isConnected` check and
            // `startSession()`'s `checkConnected()` invocation. Tag the
            // retry with the same delay so the upstream auto-reconnect
            // path has a moment to refresh the client reference.
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
            rethrowSuppressing(second, first)
        } catch (second: TransportException) {
            rethrowSuppressing(second, first)
        } catch (second: IllegalStateException) {
            rethrowSuppressing(second, first)
        }
    }

    /**
     * Attaches [first] as a suppressed exception of [second] and rethrows.
     * Declared `Nothing`-returning so callers can use it directly in `catch`
     * branches without exceeding the detekt ThrowsCount limit on
     * [retryOrRethrow].
     */
    private fun rethrowSuppressing(second: Exception, first: Exception): Nothing {
        second.addSuppressed(first)
        throw second
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
