package dev.ori.core.network.ssh

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPFileTransfer
import net.schmizz.sshj.xfer.LocalDestFile
import net.schmizz.sshj.xfer.LocalSourceFile
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Bug F regression suite — when the user picks a file via SAF in the file
 * manager (left/local pane) and hits Transfer, the chosen path arrives at
 * the SFTP layer as a `content://` Uri string, NOT a filesystem path.
 *
 * Before the fix, `uploadFileResumable` (the path the TransferWorker uses)
 * blindly wrapped the string in `java.io.File(...)`. Reading from such a
 * file threw `FileNotFoundException`, which propagated up to the worker
 * coroutine and crashed the app.
 *
 * These tests exercise the SAF detour: the impl now sniffs the
 * `content://` prefix, routes through `ContentResolver` + `SafSourceFile`
 * / `SafDestFile`, and rejects `offsetBytes > 0` because SAF streams are
 * non-seekable. Robolectric is required for `Uri.parse` and friends — the
 * sibling [LocalFileAdaptersTest] uses the same machinery.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SshSftpClientImplSafTest {

    private lateinit var sessionStore: SshSessionStore
    private lateinit var sshClient: SshSftpClientImpl
    private lateinit var sshNetworkClient: SSHClient
    private lateinit var sftp: SFTPClient
    private lateinit var fileTransfer: SFTPFileTransfer
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private val sessionId = "saf-session"

    @Before
    fun setUp() {
        sessionStore = SshSessionStore(mockk(relaxed = true))
        context = mockk(relaxed = true)
        resolver = mockk(relaxed = true)
        every { context.contentResolver } returns resolver
        sshClient = SshSftpClientImpl(sessionStore, context)
        sshNetworkClient = mockk(relaxed = true)
        sftp = mockk(relaxed = true)
        fileTransfer = mockk(relaxed = true)
        every { sshNetworkClient.isConnected } returns true
        every { sshNetworkClient.newSFTPClient() } returns sftp
        every { sftp.fileTransfer } returns fileTransfer
        injectLive(sessionStore, sessionId, sshNetworkClient)
    }

    @Test
    fun uploadFileResumable_contentUri_offsetZero_routesThroughSafSourceFile() = runBlocking {
        val uri = Uri.parse("content://com.android.providers.downloads.documents/document/123")
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        every { pfd.statSize } returns 4096L
        every { resolver.openFileDescriptor(uri, "r") } returns pfd

        val captured = slot<LocalSourceFile>()
        every { fileTransfer.upload(capture(captured), "/remote/file.bin") } returns Unit

        sshClient.uploadFileResumable(
            sessionId = sessionId,
            localPath = uri.toString(),
            remotePath = "/remote/file.bin",
            offsetBytes = 0L,
        )

        // SAF-routing assertion: the `LocalSourceFile` actually fed to SSHJ is the
        // `SafSourceFile` adapter (which reads via ContentResolver), NOT a
        // `FileSystemFile` wrapping `java.io.File("content://...")`.
        assertThat(captured.captured).isInstanceOf(SafSourceFile::class.java)
        assertThat(captured.captured.length).isEqualTo(4096L)
        verify { fileTransfer.upload(any<SafSourceFile>(), "/remote/file.bin") }
    }

    @Test
    fun uploadFileResumable_contentUri_offsetZero_emitsBracketedProgress() = runBlocking {
        val uri = Uri.parse("content://com.android.providers.media.documents/document/abc")
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        every { pfd.statSize } returns 1024L
        every { resolver.openFileDescriptor(uri, "r") } returns pfd
        every { fileTransfer.upload(any<LocalSourceFile>(), any()) } returns Unit

        val updates = mutableListOf<Pair<Long, Long>>()
        sshClient.uploadFileResumable(
            sessionId = sessionId,
            localPath = uri.toString(),
            remotePath = "/remote/file.bin",
            offsetBytes = 0L,
            onProgress = { t, total -> updates.add(t to total) },
        )

        // Spec: emit (0,total) at the start and (total,total) at the end.
        // Exact byte-counts are NOT available because SSHJ's SafSourceFile
        // path doesn't expose intra-call progress.
        assertThat(updates).containsExactly(0L to 1024L, 1024L to 1024L).inOrder()
    }

    @Test
    fun uploadFileResumable_contentUri_offsetNonZero_throwsIOException() = runBlocking {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AFoo")

        val ex = assertThrows(IOException::class.java) {
            runBlocking {
                sshClient.uploadFileResumable(
                    sessionId = sessionId,
                    localPath = uri.toString(),
                    remotePath = "/remote/file.bin",
                    offsetBytes = 100L,
                )
            }
        }
        assertThat(ex.message).contains("Resumable upload from content:// URI is not supported")
        assertThat(ex.message).contains("offsetBytes=100")

        // The contract demands a fast-fail: SSHJ's upload entry-point must NOT
        // be invoked once we detect a non-zero offset on a SAF Uri.
        verify(exactly = 0) { fileTransfer.upload(any<LocalSourceFile>(), any()) }
    }

    @Test
    fun uploadFileResumable_regularPath_doesNotRouteThroughSaf() = runBlocking {
        // Behaviour-preservation: a plain filesystem path must keep using the
        // existing positional-write path. This is verified by the existing
        // [SshSftpClientImplTest.uploadFileResumable_fromZeroOffset_uploadsFullFile]
        // — re-asserting here keeps the SAF/regular split honest from this
        // suite's perspective: we make sure NO SAF-side calls fire.
        val tmpFile = java.io.File.createTempFile("ori-bug-f", ".bin")
        try {
            tmpFile.writeBytes(ByteArray(1024) { it.toByte() })
            val fakeRemote = mockk<net.schmizz.sshj.sftp.RemoteFile>(relaxed = true)
            every {
                sftp.open(any<String>(), any<Set<net.schmizz.sshj.sftp.OpenMode>>())
            } returns fakeRemote
            every { sftp.stat(any<String>()) } returns mockk<net.schmizz.sshj.sftp.FileAttributes>(relaxed = true) {
                every { size } returns 0L
            }

            sshClient.uploadFileResumable(
                sessionId = sessionId,
                localPath = tmpFile.absolutePath,
                remotePath = "/remote/file.bin",
                offsetBytes = 0L,
            )

            // The SAF code-path must not be touched for filesystem paths.
            verify(exactly = 0) { fileTransfer.upload(any<LocalSourceFile>(), any()) }
            verify(exactly = 0) { resolver.openFileDescriptor(any(), any()) }
            verify { fakeRemote.close() }
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun downloadFileResumable_contentUri_offsetZero_routesThroughSafDestFile() = runBlocking {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABar")
        every { sftp.stat("/remote/file.bin") } returns mockk<net.schmizz.sshj.sftp.FileAttributes>(relaxed = true) {
            every { size } returns 2048L
        }
        val captured = slot<LocalDestFile>()
        every { fileTransfer.download("/remote/file.bin", capture(captured)) } returns Unit

        sshClient.downloadFileResumable(
            sessionId = sessionId,
            remotePath = "/remote/file.bin",
            localPath = uri.toString(),
            offsetBytes = 0L,
        )

        // SAF-routing assertion: the `LocalDestFile` actually fed to SSHJ is the
        // `SafDestFile` adapter (which writes via ContentResolver), NOT a
        // `FileSystemFile` wrapping `java.io.File("content://...")`.
        assertThat(captured.captured).isInstanceOf(SafDestFile::class.java)
        verify { fileTransfer.download("/remote/file.bin", any<SafDestFile>()) }
    }

    @Test
    fun downloadFileResumable_contentUri_offsetZero_emitsBracketedProgress() = runBlocking {
        val uri = Uri.parse("content://com.android.providers.media.documents/document/xyz")
        every { sftp.stat("/remote/file.bin") } returns mockk<net.schmizz.sshj.sftp.FileAttributes>(relaxed = true) {
            every { size } returns 9999L
        }
        every { fileTransfer.download(any(), any<LocalDestFile>()) } returns Unit

        val updates = mutableListOf<Pair<Long, Long>>()
        sshClient.downloadFileResumable(
            sessionId = sessionId,
            remotePath = "/remote/file.bin",
            localPath = uri.toString(),
            offsetBytes = 0L,
            onProgress = { t, total -> updates.add(t to total) },
        )

        assertThat(updates).containsExactly(0L to 9999L, 9999L to 9999L).inOrder()
    }

    @Test
    fun downloadFileResumable_contentUri_offsetNonZero_throwsIOException() = runBlocking {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABaz")

        val ex = assertThrows(IOException::class.java) {
            runBlocking {
                sshClient.downloadFileResumable(
                    sessionId = sessionId,
                    remotePath = "/remote/file.bin",
                    localPath = uri.toString(),
                    offsetBytes = 50L,
                )
            }
        }
        assertThat(ex.message).contains("Resumable download to content:// URI is not supported")
        assertThat(ex.message).contains("offsetBytes=50")

        verify(exactly = 0) { fileTransfer.download(any(), any<LocalDestFile>()) }
    }

    @Test
    fun downloadFileResumable_regularPath_keepsPositionalReadPath() = runBlocking {
        val tmpFile = java.io.File.createTempFile("ori-bug-f-dl", ".bin")
        try {
            val fakeRemote = mockk<net.schmizz.sshj.sftp.RemoteFile>(relaxed = true)
            every { fakeRemote.length() } returns 0L
            every { sftp.open(any<String>()) } returns fakeRemote

            sshClient.downloadFileResumable(
                sessionId = sessionId,
                remotePath = "/remote/file.bin",
                localPath = tmpFile.absolutePath,
                offsetBytes = 0L,
            )

            // Regular path must NOT touch SAF helpers.
            verify(exactly = 0) { fileTransfer.download(any(), any<LocalDestFile>()) }
            verify(exactly = 0) { resolver.openOutputStream(any(), any()) }
            verify { fakeRemote.close() }
        } finally {
            tmpFile.delete()
        }
    }

    private fun injectLive(store: SshSessionStore, id: String, c: SSHClient) {
        val f = SshSessionStore::class.java.getDeclaredField("sessions")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (f.get(store) as ConcurrentHashMap<String, LiveSession>)[id] =
            LiveSession(c, Protocol.SFTP, false, AtomicReference(NameCache.empty()))
    }
}
