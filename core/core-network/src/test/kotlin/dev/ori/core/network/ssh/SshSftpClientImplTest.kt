package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.writeBytes

/**
 * Unit tests for file-op overloads on [SshSftpClientImpl].
 *
 * Strategy: inject a real [SshSessionStore] with a mock verifier, then place a
 * mocked [SSHClient] into its `sessions` map via [injectLive]. The
 * `newSFTPClient()` call on that mock returns a mocked [SFTPClient] so we can
 * assert on SFTP-level behaviour without any network I/O.
 *
 * Transport-level tests (connect, disconnect, getSession guards, disconnect
 * listener) live in [SshSessionStoreTest].
 */
class SshSftpClientImplTest {

    private lateinit var sessionStore: SshSessionStore
    private lateinit var sshClient: SshSftpClientImpl
    private lateinit var sshNetworkClient: SSHClient
    private lateinit var sftp: SFTPClient
    private val sessionId = "test-session"

    @BeforeEach
    fun setUp() {
        sessionStore = SshSessionStore(mockk(relaxed = true))
        sshClient = SshSftpClientImpl(sessionStore)
        sshNetworkClient = mockk(relaxed = true)
        sftp = mockk(relaxed = true)
        every { sshNetworkClient.isConnected } returns true
        every { sshNetworkClient.newSFTPClient() } returns sftp
        injectLive(sessionStore, sessionId, sshNetworkClient)
    }

    @Test
    fun uploadFileResumable_fromZeroOffset_uploadsFullFile(@TempDir tmp: Path) = runTest {
        val payload = ByteArray(80_000) { (it % 251).toByte() }
        val localFile = tmp.resolve("local.bin")
        localFile.writeBytes(payload)

        val fakeRemote = InMemoryRemoteFile()
        every { sftp.open(any<String>(), any<Set<OpenMode>>()) } returns fakeRemote.mock
        every { sftp.stat(any<String>()) } returns mockk<FileAttributes>(relaxed = true) {
            every { size } returns 0L
        }

        sshClient.uploadFileResumable(
            sessionId = sessionId,
            localPath = localFile.toString(),
            remotePath = "/remote/file.bin",
            offsetBytes = 0L,
        )

        assertThat(fakeRemote.buffer.toByteArray()).isEqualTo(payload)
        verify { fakeRemote.mock.close() }
    }

    @Test
    fun uploadFileResumable_fromMiddleOffset_appendsRemainder(@TempDir tmp: Path) = runTest {
        val payload = ByteArray(60_000) { (it % 239).toByte() }
        val localFile = tmp.resolve("local.bin")
        localFile.writeBytes(payload)

        val halfSize = (payload.size / 2).toLong()
        val fakeRemote = InMemoryRemoteFile(initial = payload.copyOfRange(0, halfSize.toInt()))
        every { sftp.open(any<String>(), any<Set<OpenMode>>()) } returns fakeRemote.mock
        every { sftp.stat(any<String>()) } returns mockk<FileAttributes>(relaxed = true) {
            every { size } returns halfSize
        }

        sshClient.uploadFileResumable(
            sessionId = sessionId,
            localPath = localFile.toString(),
            remotePath = "/remote/file.bin",
            offsetBytes = halfSize,
        )

        assertThat(fakeRemote.buffer.toByteArray()).isEqualTo(payload)
    }

    @Test
    fun downloadFileResumable_fromMiddleOffset_appendsRemainder(@TempDir tmp: Path) = runTest {
        val payload = ByteArray(50_000) { (it % 211).toByte() }
        val halfSize = payload.size / 2
        val localFile = tmp.resolve("local.bin")
        localFile.writeBytes(payload.copyOfRange(0, halfSize))

        val fakeRemote = InMemoryRemoteFile(initial = payload)
        every { sftp.open(any<String>()) } returns fakeRemote.mock

        sshClient.downloadFileResumable(
            sessionId = sessionId,
            remotePath = "/remote/file.bin",
            localPath = localFile.toString(),
            offsetBytes = halfSize.toLong(),
        )

        assertThat(java.io.File(localFile.toString()).readBytes()).isEqualTo(payload)
    }

    @Test
    fun uploadFileResumable_progressCallback_firesIncrementally(@TempDir tmp: Path) = runTest {
        val payload = ByteArray(96 * 1024) { (it % 113).toByte() }
        val localFile = tmp.resolve("local.bin")
        localFile.writeBytes(payload)

        val fakeRemote = InMemoryRemoteFile()
        every { sftp.open(any<String>(), any<Set<OpenMode>>()) } returns fakeRemote.mock
        every { sftp.stat(any<String>()) } returns mockk<FileAttributes>(relaxed = true) {
            every { size } returns 0L
        }

        val progressUpdates = mutableListOf<Pair<Long, Long>>()
        sshClient.uploadFileResumable(
            sessionId = sessionId,
            localPath = localFile.toString(),
            remotePath = "/remote/file.bin",
            offsetBytes = 0L,
            onProgress = { t, total -> progressUpdates.add(t to total) },
        )

        assertThat(progressUpdates.size).isAtLeast(2)
        assertThat(progressUpdates.last().first).isEqualTo(payload.size.toLong())
        assertThat(progressUpdates.last().second).isEqualTo(payload.size.toLong())
    }

    @Test
    fun fileSize_existingFile_returnsSize() = runTest {
        every { sftp.stat("/remote/foo") } returns mockk<FileAttributes>(relaxed = true) {
            every { size } returns 12_345L
        }

        val result = sshClient.fileSize(sessionId, "/remote/foo")

        assertThat(result).isEqualTo(12_345L)
    }

    @Test
    fun fileSize_fileDoesNotExist_returnsNull() = runTest {
        every { sftp.stat("/remote/missing") } throws SFTPException("No such file")

        val result = sshClient.fileSize(sessionId, "/remote/missing")

        assertThat(result).isNull()
    }

    @Test
    fun listFiles_invokedFromCallerThread_runsSftpCallOnIoDispatcher() {
        // Regression: `withSftpClient` previously ran network I/O on the
        // caller's thread, which on Android caused NetworkOnMainThreadException
        // when FileManagerViewModel called listFiles from viewModelScope
        // (Dispatchers.Main). The fix routes through Dispatchers.IO.
        val callerThread = Thread.currentThread()
        val sftpThread = AtomicReference<Thread?>()
        every { sftp.ls("/") } answers {
            sftpThread.set(Thread.currentThread())
            emptyList<RemoteResourceInfo>()
        }

        runBlocking { sshClient.listFiles(sessionId, "/") }

        val captured = checkNotNull(sftpThread.get()) { "sftp.ls was never called" }
        assertThat(captured).isNotSameInstanceAs(callerThread)
    }

    @Test
    fun fileSize_noActiveSession_throwsIOException() = runTest {
        // `withSftpClient` calls `sessionStore.getSession`, which throws for unknown sessionIds.
        // The try/catch inside `fileSize` only wraps `sftp.stat`, so the
        // IOException propagates to the caller.
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                sshClient.fileSize("no-such-session", "/remote/foo")
            }
        }
    }

    @Test
    fun delete_oneOfThreeFails_returnsDeleteResultWithBoth() = runTest {
        every { sftp.rm("/a") } just runs
        every { sftp.rm("/b") } throws SFTPException("Permission denied")
        every { sftp.rm("/c") } just runs
        val result = sshClient.delete(sessionId, listOf("/a", "/b", "/c"))
        assertThat(result.succeeded).containsExactly("/a", "/c").inOrder()
        assertThat(result.failed).hasSize(1)
        assertThat(result.failed[0].first).isEqualTo("/b")
        assertThat(result.failed[0].second).contains("Permission denied")
    }

    private fun injectLive(store: SshSessionStore, id: String, c: SSHClient) {
        val f = SshSessionStore::class.java.getDeclaredField("sessions")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (f.get(store) as ConcurrentHashMap<String, LiveSession>)[id] =
            LiveSession(c, Protocol.SFTP, false, AtomicReference(NameCache.empty()))
    }

    /**
     * MockK-backed [RemoteFile] that records positional writes into an
     * in-memory [java.io.ByteArrayOutputStream] and serves reads from it.
     */
    private class InMemoryRemoteFile(initial: ByteArray = ByteArray(0)) {
        val buffer = GrowableBuffer(initial)
        val mock: RemoteFile = mockk<RemoteFile>(relaxed = true).also { rf ->
            val offsetSlot = slot<Long>()
            val dataSlot = slot<ByteArray>()
            val rOffSlot = slot<Int>()
            val rLenSlot = slot<Int>()
            every {
                rf.write(capture(offsetSlot), capture(dataSlot), capture(rOffSlot), capture(rLenSlot))
            } answers {
                buffer.writeAt(offsetSlot.captured, dataSlot.captured, rOffSlot.captured, rLenSlot.captured)
            }

            val readOffsetSlot = slot<Long>()
            val readBufSlot = slot<ByteArray>()
            val readBufOffSlot = slot<Int>()
            val readLenSlot = slot<Int>()
            every {
                rf.read(capture(readOffsetSlot), capture(readBufSlot), capture(readBufOffSlot), capture(readLenSlot))
            } answers {
                buffer.readAt(
                    readOffsetSlot.captured,
                    readBufSlot.captured,
                    readBufOffSlot.captured,
                    readLenSlot.captured,
                )
            }
            every { rf.length() } answers { buffer.size.toLong() }
        }
    }

    private class GrowableBuffer(initial: ByteArray) {
        private var data: ByteArray = initial.copyOf()
        val size: Int get() = data.size

        fun writeAt(offset: Long, src: ByteArray, srcOff: Int, len: Int) {
            val required = (offset + len).toInt()
            if (required > data.size) {
                data = data.copyOf(required)
            }
            System.arraycopy(src, srcOff, data, offset.toInt(), len)
        }

        fun readAt(offset: Long, dst: ByteArray, dstOff: Int, len: Int): Int {
            if (offset >= data.size) return -1
            val available = (data.size - offset).toInt().coerceAtMost(len)
            System.arraycopy(data, offset.toInt(), dst, dstOff, available)
            return available
        }

        fun toByteArray(): ByteArray = data.copyOf()
    }
}
