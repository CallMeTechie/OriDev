package dev.ori.app.service

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.ftp.FtpClient
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.CredentialStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tier 2 T2a — unit tests for [FtpTransferExecutor].
 *
 * Verifies that upload/download resolve the profile + credentials and call
 * the dedicated overloads on [FtpClient] rather than the singleton-backed
 * `uploadFileResumable` / `downloadFileResumable`. Also verifies that the
 * executor zero-fills the credential CharArray after use.
 */
class FtpTransferExecutorTest {

    private val ftpClient = mockk<FtpClient>()
    private val connectionRepository = mockk<ConnectionRepository>()
    private val credentialStore = mockk<CredentialStore>()
    private lateinit var executor: FtpTransferExecutor

    private val noopProgress: suspend (Long, Long) -> Unit = { _, _ -> }

    @BeforeEach
    fun setup() {
        executor = FtpTransferExecutor(ftpClient, connectionRepository, credentialStore)
    }

    @Test
    fun upload_resolvesProfileAndCallsDedicatedOverload() = runTest {
        val profile = makeProfile(id = 42L, protocol = Protocol.FTP, host = "ftp.example", port = 21)
        coEvery { connectionRepository.getProfileById(42L) } returns profile
        coEvery { credentialStore.getPassword("ref-42") } returns "hunter2".toCharArray()
        coEvery {
            ftpClient.uploadFileResumableDedicated(
                host = "ftp.example",
                port = 21,
                username = "user",
                password = any(),
                tls = false,
                localPath = "/local/foo",
                remotePath = "/remote/foo",
                offsetBytes = 0L,
                onProgress = any(),
            )
        } just Runs

        executor.upload("42", "/local/foo", "/remote/foo", 0L, noopProgress)

        coVerify(exactly = 1) {
            ftpClient.uploadFileResumableDedicated(
                host = "ftp.example",
                port = 21,
                username = "user",
                password = any(),
                tls = false,
                localPath = "/local/foo",
                remotePath = "/remote/foo",
                offsetBytes = 0L,
                onProgress = any(),
            )
        }
        coVerify(exactly = 0) {
            ftpClient.uploadFileResumable(any(), any(), any(), any())
        }
    }

    @Test
    fun upload_ftpsProfile_passesTlsTrue_andZeroFillsPassword() = runTest {
        val profile = makeProfile(id = 7L, protocol = Protocol.FTPS, host = "secure.example", port = 990)
        coEvery { connectionRepository.getProfileById(7L) } returns profile
        val creds = "s3cret".toCharArray()
        coEvery { credentialStore.getPassword("ref-7") } returns creds

        val passwordSlot = slot<CharArray>()
        coEvery {
            ftpClient.uploadFileResumableDedicated(
                host = any(), port = any(), username = any(),
                password = capture(passwordSlot), tls = true,
                localPath = any(), remotePath = any(), offsetBytes = any(),
                onProgress = any(),
            )
        } just Runs

        executor.upload("7", "/l", "/r", 1024L, noopProgress)

        // The executor must forward tls=true and — after the call returns — have
        // zero-filled the CharArray it obtained from CredentialStore.
        assertThat(passwordSlot.captured).isEqualTo(CharArray(6) { '\u0000' })
    }

    @Test
    fun download_callsDedicatedDownloadWithProfileDetails() = runTest {
        val profile = makeProfile(id = 13L, protocol = Protocol.FTP, host = "h", port = 2121)
        coEvery { connectionRepository.getProfileById(13L) } returns profile
        coEvery { credentialStore.getPassword("ref-13") } returns "pw".toCharArray()
        coEvery {
            ftpClient.downloadFileResumableDedicated(
                host = "h", port = 2121, username = "user",
                password = any(), tls = false,
                remotePath = "/r/x", localPath = "/l/x", offsetBytes = 500L,
                onProgress = any(),
            )
        } just Runs

        executor.download("13", "/r/x", "/l/x", 500L, noopProgress)

        coVerify(exactly = 1) {
            ftpClient.downloadFileResumableDedicated(
                host = "h", port = 2121, username = "user",
                password = any(), tls = false,
                remotePath = "/r/x", localPath = "/l/x", offsetBytes = 500L,
                onProgress = any(),
            )
        }
        coVerify(exactly = 0) {
            ftpClient.downloadFileResumable(any(), any(), any(), any())
        }
    }

    @Test
    fun upload_unknownProfile_throwsIllegalStateException() = runTest {
        coEvery { connectionRepository.getProfileById(99L) } returns null

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                executor.upload("99", "/l", "/r", 0L, noopProgress)
            }
        }
    }

    @Test
    fun upload_missingCredential_throwsIllegalStateException() = runTest {
        val profile = makeProfile(id = 5L, protocol = Protocol.FTP)
        coEvery { connectionRepository.getProfileById(5L) } returns profile
        coEvery { credentialStore.getPassword("ref-5") } returns null

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                executor.upload("5", "/l", "/r", 0L, noopProgress)
            }
        }
    }

    private fun makeProfile(
        id: Long,
        protocol: Protocol,
        host: String = "host.example",
        port: Int = protocol.defaultPort,
    ): ServerProfile = ServerProfile(
        id = id,
        name = "test-$id",
        host = host,
        port = port,
        protocol = protocol,
        username = "user",
        authMethod = AuthMethod.PASSWORD,
        credentialRef = "ref-$id",
    )
}
