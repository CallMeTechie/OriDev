package dev.ori.app.service

import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
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
 * Phase 12 P12.5 catch-up — unit tests for [RoutingTransferExecutor].
 *
 * Verifies the routing table from `sessionId -> ServerProfile.protocol ->
 * (ssh|ftp) executor`, plus the two error paths (Proxmox hard reject,
 * unknown profile id).
 */
class RoutingTransferExecutorTest {

    private val sshExecutor = mockk<SshTransferExecutor>()
    private val ftpExecutor = mockk<FtpTransferExecutor>()
    private val connectionRepository = mockk<ConnectionRepository>()
    private lateinit var router: RoutingTransferExecutor

    private val noopProgress: suspend (Long, Long) -> Unit = { _, _ -> }

    @BeforeEach
    fun setup() {
        router = RoutingTransferExecutor(sshExecutor, ftpExecutor, connectionRepository)
    }

    @Test
    fun upload_sshProtocol_delegatesToSshExecutor() = runTest {
        coEvery { connectionRepository.getProfileById(42L) } returns
            makeProfile(id = 42, protocol = Protocol.SSH)
        coEvery {
            sshExecutor.upload("42", "/local/foo", "/remote/foo", 0L, any())
        } just Runs

        router.upload("42", "/local/foo", "/remote/foo", 0L, noopProgress)

        coVerify(exactly = 1) {
            sshExecutor.upload("42", "/local/foo", "/remote/foo", 0L, any())
        }
        coVerify(exactly = 0) { ftpExecutor.upload(any(), any(), any(), any(), any()) }
    }

    @Test
    fun upload_sftpProtocol_delegatesToSshExecutor() = runTest {
        coEvery { connectionRepository.getProfileById(7L) } returns
            makeProfile(id = 7, protocol = Protocol.SFTP)
        coEvery { sshExecutor.upload(any(), any(), any(), any(), any()) } just Runs

        router.upload("7", "/l", "/r", 100L, noopProgress)

        coVerify(exactly = 1) { sshExecutor.upload("7", "/l", "/r", 100L, any()) }
        coVerify(exactly = 0) { ftpExecutor.upload(any(), any(), any(), any(), any()) }
    }

    @Test
    fun upload_ftpProtocol_delegatesToFtpExecutor() = runTest {
        coEvery { connectionRepository.getProfileById(9L) } returns
            makeProfile(id = 9, protocol = Protocol.FTP)
        coEvery { ftpExecutor.upload(any(), any(), any(), any(), any()) } just Runs

        router.upload("9", "/l", "/r", 0L, noopProgress)

        coVerify(exactly = 1) { ftpExecutor.upload("9", "/l", "/r", 0L, any()) }
        coVerify(exactly = 0) { sshExecutor.upload(any(), any(), any(), any(), any()) }
    }

    @Test
    fun upload_proxmoxProtocol_throwsUnsupported() = runTest {
        coEvery { connectionRepository.getProfileById(3L) } returns
            makeProfile(id = 3, protocol = Protocol.PROXMOX)

        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking {
                router.upload("3", "/l", "/r", 0L, noopProgress)
            }
        }
        coVerify(exactly = 0) { sshExecutor.upload(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { ftpExecutor.upload(any(), any(), any(), any(), any()) }
    }

    @Test
    fun upload_unknownProfile_throwsIllegalState() = runTest {
        coEvery { connectionRepository.getProfileById(404L) } returns null

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                router.upload("404", "/l", "/r", 0L, noopProgress)
            }
        }
    }

    @Test
    fun download_sshProtocol_delegatesToSshExecutor() = runTest {
        coEvery { connectionRepository.getProfileById(11L) } returns
            makeProfile(id = 11, protocol = Protocol.SCP)
        coEvery { sshExecutor.download(any(), any(), any(), any(), any()) } just Runs

        router.download("11", "/remote/bar", "/local/bar", 256L, noopProgress)

        coVerify(exactly = 1) {
            sshExecutor.download("11", "/remote/bar", "/local/bar", 256L, any())
        }
        coVerify(exactly = 0) { ftpExecutor.download(any(), any(), any(), any(), any()) }
    }

    @Test
    fun download_ftpProtocol_delegatesToFtpExecutor() = runTest {
        coEvery { connectionRepository.getProfileById(12L) } returns
            makeProfile(id = 12, protocol = Protocol.FTPS)
        coEvery { ftpExecutor.download(any(), any(), any(), any(), any()) } just Runs

        router.download("12", "/remote/baz", "/local/baz", 0L, noopProgress)

        coVerify(exactly = 1) {
            ftpExecutor.download("12", "/remote/baz", "/local/baz", 0L, any())
        }
        coVerify(exactly = 0) { sshExecutor.download(any(), any(), any(), any(), any()) }
    }

    @Test
    fun remoteFileSize_sshProtocol_delegatesToSshExecutor() = runTest {
        coEvery { connectionRepository.getProfileById(21L) } returns
            makeProfile(id = 21, protocol = Protocol.SSH)
        coEvery { sshExecutor.remoteFileSize("21", "/remote/x") } returns 4096L

        val size = router.remoteFileSize("21", "/remote/x")

        org.junit.jupiter.api.Assertions.assertEquals(4096L, size)
        coVerify(exactly = 1) { sshExecutor.remoteFileSize("21", "/remote/x") }
        coVerify(exactly = 0) { ftpExecutor.remoteFileSize(any(), any()) }
    }

    private fun makeProfile(id: Long, protocol: Protocol): ServerProfile = ServerProfile(
        id = id,
        name = "test-$id",
        host = "host.example",
        port = protocol.defaultPort,
        protocol = protocol,
        username = "user",
        authMethod = AuthMethod.PASSWORD,
        credentialRef = "ref-$id",
    )
}
