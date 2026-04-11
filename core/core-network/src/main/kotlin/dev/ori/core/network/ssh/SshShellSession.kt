package dev.ori.core.network.ssh

import net.schmizz.sshj.connection.channel.direct.Session
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class SshShellSession(
    private val session: Session,
    private val shell: Session.Shell,
) {
    val inputStream: InputStream get() = shell.inputStream
    val outputStream: OutputStream get() = shell.outputStream

    private val closed = AtomicBoolean(false)

    val isOpen: Boolean get() = !closed.get() && shell.isOpen

    fun write(data: ByteArray) {
        if (isOpen) {
            runCatching {
                outputStream.write(data)
                outputStream.flush()
            }.onFailure { close() } // Connection dropped
        }
    }

    fun resize(cols: Int, rows: Int) {
        if (isOpen) {
            runCatching {
                shell.changeWindowDimensions(cols, rows, 0, 0)
            } // Silently ignore resize failures (non-fatal)
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { shell.close() }
            runCatching { session.close() }
        }
    }
}
