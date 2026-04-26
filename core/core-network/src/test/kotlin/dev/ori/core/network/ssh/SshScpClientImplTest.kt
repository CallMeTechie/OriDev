package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SshScpClientImplTest {
    val store = mockk<SshSessionStore>(relaxed = true)
    val sshClient = SshScpClientImpl(store)

    @Test
    fun isConnected_delegates() = kotlinx.coroutines.test.runTest {
        every { store.isConnected("s1") } returns true
        assertThat(sshClient.isConnected("s1")).isTrue()
    }

    @Test
    fun uploadFileResumable_throws() = kotlinx.coroutines.test.runTest {
        val ex = assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { sshClient.uploadFileResumable("s1", "/local", "/remote", 0L) }
        }
        assertThat(ex.message).contains("SCP does not support resume")
    }

    @Test
    fun downloadFileResumable_throws() = kotlinx.coroutines.test.runTest {
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { sshClient.downloadFileResumable("s1", "/remote", "/local", 0L) }
        }
    }

    @Test fun listFiles_runsAndParses() = kotlinx.coroutines.test.runTest {
        val client = mockk<net.schmizz.sshj.SSHClient>(relaxed = true)
        val cachedNames = NameCache(mapOf(1000 to "marc"), mapOf(1000 to "marc"))
        val live = LiveSession(
            client,
            Protocol.SCP,
            true,
            java.util.concurrent.atomic.AtomicReference(cachedNames),
        )
        every { store.getSession("s1") } returns live
        coEvery { store.ensureNameCache("s1") } returns cachedNames
        val session = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
        val command = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
        every { client.startSession() } returns session
        every { session.exec(any()) } returns command
        every { command.inputStream } returns java.io.ByteArrayInputStream(
            "-rw-r--r-- 1 1000 1000 5 2026-04-26T19:25:00 hello\n".toByteArray(),
        )
        every { command.errorStream } returns java.io.ByteArrayInputStream(ByteArray(0))
        every { command.exitStatus } returns 0
        val files = sshClient.listFiles("s1", "/home/marc")
        assertThat(files).hasSize(1)
        assertThat(files[0].name).isEqualTo("hello")
        assertThat(files[0].owner).isEqualTo("marc")
    }

    @Test fun listFiles_lsExitNonZero_throwsWithStderr() = kotlinx.coroutines.test.runTest {
        val client = mockk<net.schmizz.sshj.SSHClient>(relaxed = true)
        val live = LiveSession(
            client,
            Protocol.SCP,
            false,
            java.util.concurrent.atomic.AtomicReference(NameCache.empty()),
        )
        every { store.getSession("s1") } returns live
        coEvery { store.ensureNameCache("s1") } returns NameCache.empty()
        val session = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
        val command = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
        every { client.startSession() } returns session
        every { session.exec(any()) } returns command
        every { command.inputStream } returns java.io.ByteArrayInputStream(ByteArray(0))
        every { command.errorStream } returns java.io.ByteArrayInputStream(
            "ls: cannot open directory '/root': Permission denied\n".toByteArray(),
        )
        every { command.exitStatus } returns 2
        val ex = assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { sshClient.listFiles("s1", "/root") }
        }
        assertThat(ex.message).contains("Permission denied")
    }

    @Test fun uploadFile_path_callsScpFileTransferUpload() = kotlinx.coroutines.test.runTest {
        val client = mockk<net.schmizz.sshj.SSHClient>(relaxed = true)
        val transfer = mockk<net.schmizz.sshj.xfer.scp.SCPFileTransfer>(relaxed = true)
        every { client.newSCPFileTransfer() } returns transfer
        every { store.getSession("s1") } returns LiveSession(
            client,
            Protocol.SCP,
            true,
            java.util.concurrent.atomic.AtomicReference(NameCache.empty()),
        )
        sshClient.uploadFile("s1", "/local/file", "/remote/file") { _, _ -> }
        io.mockk.verify { transfer.upload(any<net.schmizz.sshj.xfer.LocalSourceFile>(), "/remote/file") }
    }

    @Test fun uploadFile_safUri_streamsViaSafSourceFile() = kotlinx.coroutines.test.runTest {
        val client = mockk<net.schmizz.sshj.SSHClient>(relaxed = true)
        val transfer = mockk<net.schmizz.sshj.xfer.scp.SCPFileTransfer>(relaxed = true)
        every { client.newSCPFileTransfer() } returns transfer
        every { store.getSession("s1") } returns LiveSession(
            client,
            Protocol.SCP,
            true,
            java.util.concurrent.atomic.AtomicReference(NameCache.empty()),
        )
        val resolver = mockk<android.content.ContentResolver>()
        val uri = mockk<android.net.Uri>()
        every { uri.lastPathSegment } returns "abc"
        every { resolver.openFileDescriptor(uri, "r") } returns null
        every { resolver.openInputStream(uri) } returns java.io.ByteArrayInputStream("data".toByteArray())
        sshClient.uploadFile("s1", uri, "/remote/file", resolver) { _, _ -> }
        io.mockk.verify {
            transfer.upload(
                match<net.schmizz.sshj.xfer.LocalSourceFile> { it is SafSourceFile },
                "/remote/file",
            )
        }
    }

    @Test fun uploadFile_safUri_doesNotMaterialiseTempFile() = kotlinx.coroutines.test.runTest {
        // Decision 10 contract: SCP path streams from SAF directly, no temp-file hop.
        // Catches the regression where a future refactor reintroduces `File.createTempFile`.
        io.mockk.mockkStatic(java.io.File::class)
        try {
            val client = mockk<net.schmizz.sshj.SSHClient>(relaxed = true)
            val transfer = mockk<net.schmizz.sshj.xfer.scp.SCPFileTransfer>(relaxed = true)
            every { client.newSCPFileTransfer() } returns transfer
            every { store.getSession("s1") } returns LiveSession(
                client,
                Protocol.SCP,
                true,
                java.util.concurrent.atomic.AtomicReference(NameCache.empty()),
            )
            val resolver = mockk<android.content.ContentResolver>()
            val uri = mockk<android.net.Uri>()
            every { uri.lastPathSegment } returns "abc"
            every { resolver.openFileDescriptor(uri, "r") } returns null
            every { resolver.openInputStream(uri) } returns java.io.ByteArrayInputStream("data".toByteArray())

            sshClient.uploadFile("s1", uri, "/remote", resolver) { _, _ -> }

            io.mockk.verify(exactly = 0) { java.io.File.createTempFile(any(), any()) }
            io.mockk.verify(exactly = 0) { java.io.File.createTempFile(any(), any(), any()) }
        } finally {
            io.mockk.unmockkStatic(java.io.File::class)
        }
    }
}
