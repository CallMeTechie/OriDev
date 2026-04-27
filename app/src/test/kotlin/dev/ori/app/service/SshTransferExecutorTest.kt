package dev.ori.app.service

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.ssh.SshClient
import dev.ori.domain.model.Session
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.SessionRegistry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Bug J — auto-reconnect for [SshTransferExecutor].
 *
 * When the SSH session has been torn down between queueing a transfer
 * and the user pressing "Resume", the executor must transparently
 * reconnect through [SessionRegistry] instead of throwing
 * "no active SSH session for profile=$id" at the user.
 *
 * The three scenarios covered here mirror the three branches in the new
 * [SshTransferExecutor.resolveSessionId]:
 *  1. session already active -> use it directly, no reconnect.
 *  2. session gone, reconnect succeeds -> use the freshly-issued sessionId.
 *  3. session gone, reconnect fails    -> throw IllegalStateException
 *     with the friendlier "reconnect failed for profile=$id" message.
 */
class SshTransferExecutorTest {

    private val sshClient = mockk<SshClient>()
    private val connectionRepository = mockk<ConnectionRepository>()
    private val sessionRegistry = mockk<SessionRegistry>()
    private lateinit var executor: SshTransferExecutor

    private val noopProgress: suspend (Long, Long) -> Unit = { _, _ -> }

    @BeforeEach
    fun setup() {
        executor = SshTransferExecutor(sshClient, connectionRepository, sessionRegistry)
    }

    @Test
    fun upload_activeSessionPresent_usesItDirectlyWithoutReconnect() = runTest {
        coEvery { connectionRepository.getActiveSessionId(2L) } returns "ssh-existing"
        coEvery {
            sshClient.uploadFileResumable(
                sessionId = "ssh-existing",
                localPath = "/local/foo",
                remotePath = "/remote/foo",
                offsetBytes = 0L,
                onProgress = any(),
            )
        } just Runs

        executor.upload("2", "/local/foo", "/remote/foo", 0L, noopProgress)

        coVerify(exactly = 1) {
            sshClient.uploadFileResumable(
                sessionId = "ssh-existing",
                localPath = "/local/foo",
                remotePath = "/remote/foo",
                offsetBytes = 0L,
                onProgress = any(),
            )
        }
        coVerify(exactly = 0) { sessionRegistry.connect(any()) }
    }

    @Test
    fun upload_noActiveSession_reconnectSucceeds_usesFreshSessionId() = runTest {
        coEvery { connectionRepository.getActiveSessionId(2L) } returns null
        coEvery { sessionRegistry.connect(2L) } returns Result.success(
            makeSession(id = "ssh-fresh", profileId = 2L),
        )
        coEvery {
            sshClient.uploadFileResumable(
                sessionId = "ssh-fresh",
                localPath = "/local/foo",
                remotePath = "/remote/foo",
                offsetBytes = 1024L,
                onProgress = any(),
            )
        } just Runs

        executor.upload("2", "/local/foo", "/remote/foo", 1024L, noopProgress)

        coVerify(exactly = 1) { sessionRegistry.connect(2L) }
        coVerify(exactly = 1) {
            sshClient.uploadFileResumable(
                sessionId = "ssh-fresh",
                localPath = "/local/foo",
                remotePath = "/remote/foo",
                offsetBytes = 1024L,
                onProgress = any(),
            )
        }
    }

    @Test
    fun download_noActiveSession_reconnectSucceeds_usesFreshSessionId() = runTest {
        coEvery { connectionRepository.getActiveSessionId(7L) } returns null
        coEvery { sessionRegistry.connect(7L) } returns Result.success(
            makeSession(id = "ssh-reborn", profileId = 7L),
        )
        coEvery {
            sshClient.downloadFileResumable(
                sessionId = "ssh-reborn",
                remotePath = "/remote/x",
                localPath = "/local/x",
                offsetBytes = 0L,
                onProgress = any(),
            )
        } just Runs

        executor.download("7", "/remote/x", "/local/x", 0L, noopProgress)

        coVerify(exactly = 1) { sessionRegistry.connect(7L) }
        coVerify(exactly = 1) {
            sshClient.downloadFileResumable(
                sessionId = "ssh-reborn",
                remotePath = "/remote/x",
                localPath = "/local/x",
                offsetBytes = 0L,
                onProgress = any(),
            )
        }
    }

    @Test
    fun upload_noActiveSession_reconnectFails_throwsWithFriendlyMessage() = runTest {
        coEvery { connectionRepository.getActiveSessionId(2L) } returns null
        coEvery { sessionRegistry.connect(2L) } returns Result.failure(
            IllegalStateException("auth refused"),
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                executor.upload("2", "/l", "/r", 0L, noopProgress)
            }
        }
        assertThat(ex.message).contains("reconnect failed for profile=2")
        assertThat(ex.message).contains("auth refused")
        coVerify(exactly = 0) {
            sshClient.uploadFileResumable(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun upload_invalidSessionId_throwsWithoutTouchingRegistry() = runTest {
        val ex = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                executor.upload("not-a-number", "/l", "/r", 0L, noopProgress)
            }
        }
        assertThat(ex.message).contains("invalid sessionId=not-a-number")
        coVerify(exactly = 0) { sessionRegistry.connect(any()) }
        coVerify(exactly = 0) { connectionRepository.getActiveSessionId(any()) }
    }

    @Test
    fun remoteFileSize_noActiveSession_returnsNull_doesNotReconnect() = runTest {
        // resolveActiveSessionIdOrNull stays strict by design — no reconnect
        // for the metadata lookup path. Test guards that behaviour.
        coEvery { connectionRepository.getActiveSessionId(2L) } returns null

        val size = executor.remoteFileSize("2", "/remote/x")

        assertThat(size).isNull()
        coVerify(exactly = 0) { sessionRegistry.connect(any()) }
        coVerify(exactly = 0) { sshClient.fileSize(any(), any()) }
    }

    @Test
    fun remoteFileSize_activeSessionPresent_delegatesToSshClient() = runTest {
        coEvery { connectionRepository.getActiveSessionId(2L) } returns "ssh-existing"
        coEvery { sshClient.fileSize("ssh-existing", "/remote/x") } returns 8192L

        val size = executor.remoteFileSize("2", "/remote/x")

        assertThat(size).isEqualTo(8192L)
        coVerify(exactly = 0) { sessionRegistry.connect(any()) }
    }

    private fun makeSession(id: String, profileId: Long): Session = Session(
        id = id,
        profileId = profileId,
        profileName = "Profile $profileId",
        host = "host.example",
        port = 22,
        connectedAt = 0L,
        protocol = Protocol.SSH,
    )
}
