package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SshSessionTest {

    @Test
    fun `SshSession stores all fields correctly`() {
        val session = SshSession(
            sessionId = "test-id",
            profileId = 42L,
            host = "example.com",
            port = 22,
            connectedAt = 1000L
        )

        assertThat(session.sessionId).isEqualTo("test-id")
        assertThat(session.profileId).isEqualTo(42L)
        assertThat(session.host).isEqualTo("example.com")
        assertThat(session.port).isEqualTo(22)
        assertThat(session.connectedAt).isEqualTo(1000L)
    }

    @Test
    fun `SshSession copy works correctly`() {
        val original = SshSession("id", 1L, "host1", 22, 100L)
        val copied = original.copy(host = "host2", port = 2222)

        assertThat(copied.host).isEqualTo("host2")
        assertThat(copied.port).isEqualTo(2222)
        assertThat(copied.sessionId).isEqualTo("id")
    }

    @Test
    fun `SshSession equals and hashCode work correctly`() {
        val session1 = SshSession("id", 1L, "host", 22, 100L)
        val session2 = SshSession("id", 1L, "host", 22, 100L)

        assertThat(session1).isEqualTo(session2)
        assertThat(session1.hashCode()).isEqualTo(session2.hashCode())
    }

    @Test
    fun `CommandResult stores all fields correctly`() {
        val result = CommandResult(
            exitCode = 0,
            stdout = "hello world",
            stderr = ""
        )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(result.stdout).isEqualTo("hello world")
        assertThat(result.stderr).isEmpty()
    }

    @Test
    fun `CommandResult with non-zero exit code`() {
        val result = CommandResult(
            exitCode = 1,
            stdout = "",
            stderr = "command not found"
        )

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.stderr).isEqualTo("command not found")
    }

    @Test
    fun `CommandResult equals and hashCode work correctly`() {
        val result1 = CommandResult(0, "out", "err")
        val result2 = CommandResult(0, "out", "err")

        assertThat(result1).isEqualTo(result2)
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode())
    }
}
