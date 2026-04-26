package dev.ori.core.network.ssh

import net.schmizz.sshj.SSHClient

internal data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

internal object ShellInvocation {
    fun run(client: SSHClient, innerCommand: String, bashAvailable: Boolean): ShellResult {
        val wrapped = wrap(innerCommand, bashAvailable)
        val session = client.startSession()
        try {
            val cmd = session.exec(wrapped)
            val out = cmd.inputStream.bufferedReader().readText()
            val err = cmd.errorStream.bufferedReader().readText()
            cmd.join()
            return ShellResult(cmd.exitStatus ?: -1, out, err)
        } finally { session.close() }
    }

    internal fun wrap(inner: String, bashAvailable: Boolean): String {
        val escaped = "'" + inner.replace("'", "'\\''") + "'"
        return if (bashAvailable) "bash --noprofile --norc -c $escaped" else "sh -c $escaped"
    }
}
