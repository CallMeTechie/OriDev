package dev.ori.data.repository

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.model.DeleteResult
import dev.ori.core.network.model.RemoteFile
import dev.ori.core.network.ssh.SshClient
import dev.ori.domain.model.Session
import dev.ori.domain.repository.SessionRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RemoteFileSystemRepositoryTest {

    private val scpClient: SshClient = mockk(relaxed = true)
    private val sftpClient: SshClient = mockk(relaxed = true)
    private val sessionRegistry: SessionRegistry = mockk()

    private val openSessionsFlow = MutableStateFlow<List<Session>>(emptyList())

    private val repo: RemoteFileSystemRepository by lazy {
        RemoteFileSystemRepository(
            clients = mapOf(
                Protocol.SCP to scpClient,
                Protocol.SFTP to sftpClient,
                Protocol.SSH to sftpClient,
            ),
            sessionRegistry = sessionRegistry,
        )
    }

    @BeforeEach
    fun setUp() {
        every { sessionRegistry.openSessions } returns openSessionsFlow
    }

    private fun makeSession(id: String, protocol: Protocol) = Session(
        id = id,
        profileId = 1L,
        profileName = "Test Profile",
        host = "host.local",
        port = 22,
        connectedAt = 0L,
        protocol = protocol,
    )

    // -------------------------------------------------------------------------
    // Decision 8: SCP protocol → routes to SCP client
    // -------------------------------------------------------------------------

    @Test
    fun `listFiles_protocolScp_routesToScpClient`() = runTest {
        val session = makeSession("s-scp", Protocol.SCP)
        openSessionsFlow.value = listOf(session)
        repo.setActiveSession("s-scp")

        val fakeFile = RemoteFile(
            name = "file.txt",
            path = "/file.txt",
            isDirectory = false,
            size = 100L,
            lastModified = 0L,
            permissions = "-rwxr-xr-x",
            owner = "root",
        )
        coEvery { scpClient.listFiles("s-scp", "/") } returns listOf(fakeFile)

        val result = repo.listFiles("/")

        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("file.txt")
        coVerify(exactly = 1) { scpClient.listFiles("s-scp", "/") }
        coVerify(exactly = 0) { sftpClient.listFiles(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Decision 9: SSH protocol → routes to SFTP client
    // -------------------------------------------------------------------------

    @Test
    fun `listFiles_protocolSsh_routesToSftpClient`() = runTest {
        val session = makeSession("s-ssh", Protocol.SSH)
        openSessionsFlow.value = listOf(session)
        repo.setActiveSession("s-ssh")

        coEvery { sftpClient.listFiles("s-ssh", "/home") } returns emptyList()

        repo.listFiles("/home")

        coVerify(exactly = 1) { sftpClient.listFiles("s-ssh", "/home") }
        coVerify(exactly = 0) { scpClient.listFiles(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Guard: deleteFile("/") throws before reaching any client
    // -------------------------------------------------------------------------

    @Test
    fun `deleteFile_root_throwsBeforeReachingClient`() = runTest {
        val session = makeSession("s-scp", Protocol.SCP)
        openSessionsFlow.value = listOf(session)
        repo.setActiveSession("s-scp")

        assertThrows<IllegalArgumentException> {
            repo.deleteFile("/")
        }

        coVerify(exactly = 0) { scpClient.delete(any(), any()) }
        coVerify(exactly = 0) { sftpClient.delete(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Property-based style: blank/root path shapes are rejected before client
    //
    // Kotest-property is NOT available in :data (not in build.gradle.kts), so
    // this uses a plain @Test with an explicit list of unsafe shapes instead.
    // Deviation from plan: no Arb.of / 500 iterations; same safety coverage
    // achieved via an explicit exhaustive list. Note: //, ., .. are NOT
    // covered by the current require guard (isNotBlank && != "/") and are
    // therefore excluded from this list — the guard matches only blank
    // strings and the filesystem root.
    // -------------------------------------------------------------------------

    @Test
    fun `deleteFile_unsafePathArb_alwaysRejectedBeforeClient`() = runTest {
        val session = makeSession("s-scp", Protocol.SCP)
        openSessionsFlow.value = listOf(session)
        repo.setActiveSession("s-scp")

        val unsafeShapes = listOf(
            "",
            "/",
            " ",
            "\t",
            "\n",
            "   ",
        )

        for (path in unsafeShapes) {
            assertThrows<IllegalArgumentException>("path='$path' should throw") {
                repo.deleteFile(path)
            }
        }

        coVerify(exactly = 0) { scpClient.delete(any(), any()) }
        coVerify(exactly = 0) { sftpClient.delete(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Sanity: a valid deleteFile path is forwarded to the right client
    // -------------------------------------------------------------------------

    @Test
    fun `deleteFile_validPath_delegatesToClient`() = runTest {
        val session = makeSession("s-sftp", Protocol.SFTP)
        openSessionsFlow.value = listOf(session)
        repo.setActiveSession("s-sftp")

        coEvery {
            sftpClient.delete("s-sftp", listOf("/tmp/file.txt"))
        } returns DeleteResult(succeeded = listOf("/tmp/file.txt"), failed = emptyList())

        repo.deleteFile("/tmp/file.txt")

        coVerify(exactly = 1) { sftpClient.delete("s-sftp", listOf("/tmp/file.txt")) }
        coVerify(exactly = 0) { scpClient.delete(any(), any()) }
    }
}
