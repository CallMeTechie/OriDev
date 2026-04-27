package dev.ori.feature.filemanager.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

// Bug G fix — hard-coded mime constants so the test sourceset doesn't
// have to load android.content.ClipDescription off the stub android.jar.
// Both values match the platform constants verbatim.
private const val MIME_TEXT_PLAIN = "text/plain"
private const val MIME_TEXT_HTML = "text/html"

/**
 * Bug G fix — exercises the pure-Kotlin core of the drag-and-drop
 * payload codec. The real `decodeClipData(ClipData?)` wraps these
 * helpers; constructing a real Android [android.content.ClipData] in a
 * unit test would require Robolectric, which the file-manager test
 * sourceset deliberately doesn't pull in.
 *
 * UI-test coverage for the drag gesture itself is intentionally
 * skipped per the bug-fix scope — drag-and-drop instrumentation is
 * brittle and the payload contract is the only piece that needs
 * regression protection.
 */
class DragAndDropClipDataTest {

    @Test
    fun `decodeClipItems empty input returns empty list`() {
        val out = decodeClipItems(emptyList())

        assertThat(out).isEmpty()
    }

    @Test
    fun `decodeClipItems single non-empty item returns single path`() {
        val out = decodeClipItems(listOf("/var/log/syslog"))

        assertThat(out).containsExactly("/var/log/syslog")
    }

    @Test
    fun `decodeClipItems multiple items preserves order`() {
        val items = listOf("/a.txt", "/b.txt", "/c.txt")

        val out = decodeClipItems(items)

        assertThat(out).containsExactly("/a.txt", "/b.txt", "/c.txt").inOrder()
    }

    @Test
    fun `decodeClipItems filters null entries`() {
        val items: List<CharSequence?> = listOf("/keep.txt", null, "/keep2.txt")

        val out = decodeClipItems(items)

        assertThat(out).containsExactly("/keep.txt", "/keep2.txt").inOrder()
    }

    @Test
    fun `decodeClipItems filters empty-string entries`() {
        val items: List<CharSequence?> = listOf("/keep.txt", "", "/keep2.txt")

        val out = decodeClipItems(items)

        assertThat(out).containsExactly("/keep.txt", "/keep2.txt").inOrder()
    }

    @Test
    fun `decodeClipItems coerces CharSequence subtypes to String`() {
        // ClipData items expose CharSequence, not String. The decoder
        // must call toString() so callers consistently receive String.
        val builder: CharSequence = StringBuilder("/a.txt")
        val buffer: CharSequence = StringBuffer("/b.txt")

        val out = decodeClipItems(listOf(builder, buffer))

        assertThat(out).containsExactly("/a.txt", "/b.txt").inOrder()
        // Each entry is a real String, not the original CharSequence —
        // sanity-check via type membership.
        for (entry in out) {
            assertThat(entry).isInstanceOf(String::class.java)
        }
    }

    @Test
    fun `decodeClipItems all empty input returns empty`() {
        val items: List<CharSequence?> = listOf(null, "", null)

        val out = decodeClipItems(items)

        assertThat(out).isEmpty()
    }

    @Test
    fun `containsPlainText returns true when text plain mime is present`() {
        val mimeTypes: Set<String> = setOf(MIME_TEXT_PLAIN)

        assertThat(mimeTypes.containsPlainText()).isTrue()
    }

    @Test
    fun `containsPlainText returns true when text plain is among many`() {
        val mimeTypes: Set<String> = setOf(MIME_TEXT_PLAIN, MIME_TEXT_HTML)

        assertThat(mimeTypes.containsPlainText()).isTrue()
    }

    @Test
    fun `containsPlainText returns false on non-text payload`() {
        val mimeTypes: Set<String> = setOf("image/png", "application/pdf")

        assertThat(mimeTypes.containsPlainText()).isFalse()
    }

    @Test
    fun `containsPlainText returns false on empty mime set`() {
        assertThat(emptySet<String>().containsPlainText()).isFalse()
    }
}
