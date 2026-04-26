package dev.ori.core.network.ssh

import android.content.ContentResolver
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalFileAdaptersTest {
    private val uri: Uri = Uri.parse("content://example/abc")

    @Test
    fun safSource_inputStream_usesResolver() {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream("hi".toByteArray())
        val src = SafSourceFile(uri, resolver, length = 2L, name = "a.txt")
        assertThat(src.inputStream.bufferedReader().readText()).isEqualTo("hi")
    }

    @Test
    fun safSource_resolverNull_throws() {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        val src = SafSourceFile(uri, resolver, 0L, "a.txt")
        assertThrows(IOException::class.java) { src.inputStream }
    }

    @Test
    fun safSource_lengthAndName() {
        val src = SafSourceFile(uri, mockk(), 42L, "foo")
        assertThat(src.length).isEqualTo(42L)
        assertThat(src.name).isEqualTo("foo")
    }

    @Test
    fun safDest_outputStream_usesResolver() {
        val resolver = mockk<ContentResolver>()
        val sink = ByteArrayOutputStream()
        every { resolver.openOutputStream(uri, "wt") } returns sink
        val dst = SafDestFile(uri, resolver, "a.txt")
        dst.getOutputStream(append = false).bufferedWriter().use { it.write("hi") }
        assertThat(String(sink.toByteArray())).isEqualTo("hi")
    }

    @Test
    fun safDest_appendThrows() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SafDestFile(uri, mockk(), "a.txt").getOutputStream(append = true)
        }
        assertThat(ex.message).contains("does not support resumable")
    }

    @Test
    fun safDest_getChild_returnsSelf_singleFileInvariant() {
        // SAF Uris always point at a single file; SCPDownloadClient's tree-walking
        // getChild() resolution returns `this` so SSHJ writes through the same
        // OutputStream regardless of what server-side filename it announces. A future
        // change that wants to support directory downloads must replace this with a
        // DocumentFile.createFile(...) call AND change the test to assert the new
        // child is a fresh SafDestFile pointing at the new Uri.
        val dst = SafDestFile(Uri.parse("content://x/abc"), mockk(), "a.txt")
        assertThat(dst.getChild("anything")).isSameInstanceAs(dst)
        assertThat(dst.getTargetFile("anything")).isSameInstanceAs(dst)
        assertThat(dst.getTargetDirectory("anything")).isSameInstanceAs(dst)
    }
}
