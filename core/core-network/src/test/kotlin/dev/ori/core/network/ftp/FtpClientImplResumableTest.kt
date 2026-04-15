package dev.ori.core.network.ftp

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.writeBytes

/**
 * Tests for the resumable upload/download overloads on [FtpClientImpl].
 *
 * We inject a mocked [FTPClient] into the private `client` field via
 * reflection so we can assert how `storeFile` / `retrieveFile` /
 * `setRestartOffset` are invoked and manipulate the backing streams without
 * touching a real FTP server.
 */
class FtpClientImplResumableTest {

    private lateinit var impl: FtpClientImpl
    private lateinit var ftp: FTPClient

    @BeforeEach
    fun setUp() {
        impl = FtpClientImpl()
        ftp = mockk(relaxed = true)
        val field = FtpClientImpl::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(impl, ftp)
    }

    @Test
    fun uploadFileResumable_fromZeroOffset_uploadsFullFile(@TempDir tmp: Path) = runTest {
        val payload = ByteArray(10_000) { (it % 251).toByte() }
        val localFile = tmp.resolve("upload.bin")
        localFile.writeBytes(payload)

        val captured = ByteArrayOutputStream()
        val streamSlot = slot<InputStream>()
        every { ftp.storeFile(any(), capture(streamSlot)) } answers {
            captured.write(streamSlot.captured.readBytes())
            true
        }

        impl.uploadFileResumable(
            localPath = localFile.toString(),
            remotePath = "/remote/upload.bin",
            offsetBytes = 0L,
        )

        assertThat(captured.toByteArray()).isEqualTo(payload)
        verify(exactly = 0) { ftp.setRestartOffset(any()) }
    }

    @Test
    fun uploadFileResumable_fromMiddleOffset_sendsOnlyRemainder(@TempDir tmp: Path) = runTest {
        val payload = ByteArray(20_000) { (it % 239).toByte() }
        val half = payload.size / 2L
        val localFile = tmp.resolve("upload.bin")
        localFile.writeBytes(payload)

        val captured = ByteArrayOutputStream()
        val streamSlot = slot<InputStream>()
        every { ftp.storeFile(any(), capture(streamSlot)) } answers {
            captured.write(streamSlot.captured.readBytes())
            true
        }

        impl.uploadFileResumable(
            localPath = localFile.toString(),
            remotePath = "/remote/upload.bin",
            offsetBytes = half,
        )

        // Only the second half should have been streamed to storeFile.
        assertThat(captured.toByteArray()).isEqualTo(payload.copyOfRange(half.toInt(), payload.size))
        verify { ftp.setRestartOffset(half) }
    }

    @Test
    fun downloadFileResumable_fromMiddleOffset_appendsRemainder(@TempDir tmp: Path) = runTest {
        val payload = ByteArray(30_000) { (it % 211).toByte() }
        val half = payload.size / 2
        val localFile = tmp.resolve("dl.bin")
        localFile.writeBytes(payload.copyOfRange(0, half))

        val streamSlot = slot<OutputStream>()
        every { ftp.retrieveFile(any(), capture(streamSlot)) } answers {
            streamSlot.captured.write(payload.copyOfRange(half, payload.size))
            streamSlot.captured.flush()
            true
        }

        impl.downloadFileResumable(
            remotePath = "/remote/dl.bin",
            localPath = localFile.toString(),
            offsetBytes = half.toLong(),
        )

        assertThat(java.io.File(localFile.toString()).readBytes()).isEqualTo(payload)
        verify { ftp.setRestartOffset(half.toLong()) }
    }

    @Test
    fun uploadFileResumable_progressCallback_firesIncrementally(@TempDir tmp: Path) = runTest {
        // 96 KiB so the stream sees more than one buffered read.
        val payload = ByteArray(96 * 1024) { (it % 113).toByte() }
        val localFile = tmp.resolve("upload.bin")
        localFile.writeBytes(payload)

        val streamSlot = slot<InputStream>()
        every { ftp.storeFile(any(), capture(streamSlot)) } answers {
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = streamSlot.captured.read(buffer)
                if (n <= 0) break
            }
            true
        }

        val progress = mutableListOf<Pair<Long, Long>>()
        impl.uploadFileResumable(
            localPath = localFile.toString(),
            remotePath = "/remote/upload.bin",
            offsetBytes = 0L,
            onProgress = { t, total -> progress.add(t to total) },
        )

        assertThat(progress.size).isAtLeast(2)
        assertThat(progress.last().first).isEqualTo(payload.size.toLong())
    }

    @Test
    fun fileSize_existingFile_returnsSize() = runTest {
        val file = mockk<FTPFile>(relaxed = true) {
            every { isFile } returns true
            every { size } returns 67_890L
        }
        every { ftp.listFiles("/remote/foo") } returns arrayOf(file)

        val result = impl.fileSize("/remote/foo")

        assertThat(result).isEqualTo(67_890L)
    }

    @Test
    fun fileSize_listReturnsDirectory_returnsNull() = runTest {
        val dir = mockk<FTPFile>(relaxed = true) {
            every { isFile } returns false
            every { isDirectory } returns true
        }
        every { ftp.listFiles("/remote/dir") } returns arrayOf(dir)

        val result = impl.fileSize("/remote/dir")

        assertThat(result).isNull()
    }

    @Test
    fun fileSize_listReturnsEmpty_returnsNull() = runTest {
        every { ftp.listFiles("/remote/missing") } returns emptyArray()

        val result = impl.fileSize("/remote/missing")

        assertThat(result).isNull()
    }

    @Test
    fun uploadFileResumableDedicated_unconnectedImpl_doesNotTouchSingletonField(@TempDir tmp: Path) = runTest {
        // Fresh impl with NO `client` field set — the dedicated overload
        // must not route through `requireClient()`. It should attempt its
        // own FTPClient connect and fail with IOException (not ISE) when it
        // can't reach the bogus host, and the singleton field must remain null.
        val fresh = FtpClientImpl()
        val payload = tmp.resolve("dedicated.bin")
        payload.writeBytes(ByteArray(16) { 1 })

        val password = "pw".toCharArray()
        val thrown = runCatching {
            fresh.uploadFileResumableDedicated(
                host = "127.0.0.1",
                // Port 1 is almost certainly closed on test hosts — Commons Net
                // surfaces that as an IOException, which is what we want to see.
                port = 1,
                username = "anon",
                password = password,
                tls = false,
                localPath = payload.toString(),
                remotePath = "/remote/x",
                offsetBytes = 0L,
            )
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(java.io.IOException::class.java)

        // Crucially: the singleton-backed field must NOT have been populated
        // by the dedicated call — otherwise two parallel dedicated transfers
        // would still race each other.
        val field = FtpClientImpl::class.java.getDeclaredField("client")
        field.isAccessible = true
        assertThat(field.get(fresh)).isNull()

        // Caller owns zero-fill — executor does this, not the impl.
        password.fill('\u0000')
    }

    @Test
    fun downloadFileResumableDedicated_unconnectedImpl_failsWithIOException(@TempDir tmp: Path) = runTest {
        val fresh = FtpClientImpl()
        val sink = tmp.resolve("dl.bin")
        sink.writeBytes(ByteArray(0))

        val thrown = runCatching {
            fresh.downloadFileResumableDedicated(
                host = "127.0.0.1",
                port = 1,
                username = "anon",
                password = "pw".toCharArray(),
                tls = false,
                remotePath = "/remote/x",
                localPath = sink.toString(),
                offsetBytes = 0L,
            )
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(java.io.IOException::class.java)
    }

    @Test
    fun fileSize_notConnected_throwsIllegalStateException() = runTest {
        // `requireClient()` sits outside the try/catch in `fileSize`, so a
        // null client surfaces as an IllegalStateException to the caller.
        val unconnected = FtpClientImpl()

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                unconnected.fileSize("/remote/foo")
            }
        }
    }
}
