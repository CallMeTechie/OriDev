package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Command
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ShellInvocationTest {

    @Test fun bashAvailable_invokesBashNoProfileNoRc() {
        val captured = runCapturing(stdout = "ok\n", stderr = "") {
            ShellInvocation.run(it, "ls /tmp", bashAvailable = true)
        }
        assertThat(captured).isEqualTo("bash --noprofile --norc -c 'ls /tmp'")
    }

    @Test fun bashUnavailable_invokesShDashC() {
        val captured = runCapturing(stdout = "", stderr = "") {
            ShellInvocation.run(it, "ls /tmp", bashAvailable = false)
        }
        assertThat(captured).startsWith("sh -c '")
    }

    @Test fun innerSingleQuoteEscaped() {
        val captured = runCapturing(stdout = "", stderr = "") {
            ShellInvocation.run(it, "echo 'hi'", bashAvailable = false)
        }
        assertThat(captured).isEqualTo("sh -c 'echo '\\''hi'\\'''")
    }

    @Test fun nonZeroExitReturnedAsResult() {
        var result: ShellResult? = null
        runCapturing(stdout = "", stderr = "Permission denied\n", exit = 1) {
            result = ShellInvocation.run(it, "x", bashAvailable = false)
        }
        assertThat(result!!.exitCode).isEqualTo(1)
        assertThat(result!!.stderr).isEqualTo("Permission denied\n")
    }

    private inline fun runCapturing(
        stdout: String,
        stderr: String,
        exit: Int = 0,
        block: (SSHClient) -> Unit,
    ): String {
        val client = mockk<SSHClient>(relaxed = true)
        val session = mockk<Session>(relaxed = true)
        val command = mockk<Command>(relaxed = true)
        val captured = slot<String>()
        every { client.startSession() } returns session
        every { session.exec(capture(captured)) } returns command
        every { command.inputStream } returns ByteArrayInputStream(stdout.toByteArray())
        every { command.errorStream } returns ByteArrayInputStream(stderr.toByteArray())
        every { command.exitStatus } returns exit
        block(client)
        return captured.captured
    }
}
