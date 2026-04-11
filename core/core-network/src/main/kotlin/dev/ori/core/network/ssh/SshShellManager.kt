package dev.ori.core.network.ssh

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshShellManager @Inject constructor() {

    private val shells = ConcurrentHashMap<String, SshShellSession>()

    fun openShell(
        client: net.schmizz.sshj.SSHClient,
        cols: Int = 80,
        rows: Int = 24,
        term: String = "xterm-256color",
    ): ShellHandle {
        val session = client.startSession()
        session.allocatePTY(term, cols, rows, 0, 0, emptyMap())
        val shell = session.startShell()
        val shellSession = SshShellSession(session, shell)
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

    fun getSession(shellId: String): SshShellSession? = shells[shellId]

    fun closeShell(shellId: String) {
        shells.remove(shellId)?.close()
    }

    fun closeAllShells() {
        shells.keys.toList().forEach { closeShell(it) }
    }

    fun isShellOpen(shellId: String): Boolean =
        shells[shellId]?.isOpen == true
}
