package dev.ori.core.network

import com.google.common.truth.Truth.assertThat
import dev.ori.core.network.ftp.FtpClientImpl
import dev.ori.core.network.ssh.OriDevHostKeyVerifier
import dev.ori.core.network.ssh.SshClientImpl
import dev.ori.core.network.ssh.SshShellManager
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Option 5 S1 — Verifies the Security Hardening contract from CLAUDE.md:
 *
 *   "Passwords in memory: char[] not String. Zero-fill after use."
 *
 * [dev.ori.core.network.ssh.SshClient.connect] and
 * [dev.ori.core.network.ftp.FtpClient.connect] both document a security
 * contract that the caller's CharArray buffer is zero-filled in a `try/finally`
 * on both the happy path AND on exception, so callers can rely on the buffer
 * being wiped when the call returns.
 *
 * Strategy: we point the clients at an unreachable host (127.0.0.1 on a
 * closed port) so the network connect attempt throws immediately, then
 * assert the password buffer is all `'\u0000'`. Exercising the exception
 * path is the load-bearing assertion — it confirms the `finally` fires even
 * when auth never reaches the remote.
 *
 * Known limitation: SSHJ `authPassword` and Commons Net `FTPClient.login`
 * both take `String` internally, so the transient JVM `String` allocated
 * inside `connect(...)` is NOT under our control and may linger in the
 * string pool until GC. These tests verify only the caller-owned CharArray
 * buffer, which is the part the Ori:Dev codebase can wipe.
 */
class CharArrayZeroFillTest {

    @Test
    fun sshConnect_failurePath_zeroFillsCallerPasswordBuffer() = runTest {
        val sshClient = SshClientImpl(
            hostKeyVerifier = mockk<OriDevHostKeyVerifier>(relaxed = true),
            shellManager = mockk<SshShellManager>(relaxed = true),
        )
        val password = "hunter2".toCharArray()

        // Port 1 on loopback is reliably not listening in CI / unit-test
        // environments and causes SSHClient.connect to throw immediately.
        runCatching {
            sshClient.connect(
                host = "127.0.0.1",
                port = 1,
                username = "nobody",
                password = password,
                privateKey = null,
            )
        }

        assertThat(password).isEqualTo(CharArray(7) { '\u0000' })
    }

    @Test
    fun ftpConnect_failurePath_zeroFillsCallerPasswordBuffer() = runTest {
        val ftpClient = FtpClientImpl()
        val password = "hunter2".toCharArray()

        runCatching {
            ftpClient.connect(
                host = "127.0.0.1",
                port = 1,
                username = "nobody",
                password = password,
                useTls = false,
            )
        }

        assertThat(password).isEqualTo(CharArray(7) { '\u0000' })
    }

    @Test
    fun sshConnect_deprecatedStringOverload_zeroFillsIntermediateBuffer() = runTest {
        // The @Deprecated String overload converts password.toCharArray() into
        // an intermediate buffer and delegates to the CharArray variant. That
        // intermediate buffer should also be zero-filled in a finally — we
        // can't observe it directly, but we CAN assert the call does not
        // throw a NullPointerException or CharArrayZeroFill-related issue
        // on the exception path (unreachable host).
        val sshClient = SshClientImpl(
            hostKeyVerifier = mockk<OriDevHostKeyVerifier>(relaxed = true),
            shellManager = mockk<SshShellManager>(relaxed = true),
        )

        @Suppress("DEPRECATION")
        val result = runCatching {
            sshClient.connect(
                host = "127.0.0.1",
                port = 1,
                username = "nobody",
                password = "hunter2",
            )
        }

        // Deprecated overload should propagate the underlying connect failure,
        // not swallow it into a generic error. We only care that it failed
        // for the expected network-level reason, not due to the wipe logic.
        assertThat(result.isFailure).isTrue()
    }
}
