package dev.ori.feature.terminal.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CodeBlockDetectorTest {

    private fun String.toUtf8(): ByteArray = this.toByteArray(Charsets.UTF_8)

    @Test
    fun processChunk_noBlocks_returnsEmpty() {
        val detector = CodeBlockDetector()
        val bytes = "hello world\nno code here\n".toUtf8()
        val blocks = detector.processChunk(bytes, bytes.size)
        assertThat(blocks).isEmpty()
    }

    @Test
    fun processChunk_completeBlock_detects() {
        val detector = CodeBlockDetector()
        val input = "preamble\n```\nfoo\nbar\n```\ntail\n"
        val bytes = input.toUtf8()
        val blocks = detector.processChunk(bytes, bytes.size)
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].language).isNull()
        assertThat(blocks[0].content).isEqualTo("foo\nbar")
    }

    @Test
    fun processChunk_withLanguage_capturesLanguage() {
        val detector = CodeBlockDetector()
        val input = "```kotlin\nval x = 1\n```\n"
        val bytes = input.toUtf8()
        val blocks = detector.processChunk(bytes, bytes.size)
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].language).isEqualTo("kotlin")
        assertThat(blocks[0].content).isEqualTo("val x = 1")
    }

    @Test
    fun processChunk_splitAcrossChunks_detects() {
        val detector = CodeBlockDetector()
        val part1 = "```python\nprint('hi')\n".toUtf8()
        val part2 = "```\nafter\n".toUtf8()
        val b1 = detector.processChunk(part1, part1.size)
        assertThat(b1).isEmpty()
        val b2 = detector.processChunk(part2, part2.size)
        assertThat(b2).hasSize(1)
        assertThat(b2[0].language).isEqualTo("python")
        assertThat(b2[0].content).isEqualTo("print('hi')")
    }

    @Test
    fun processChunk_multipleBlocks_detectsAll() {
        val detector = CodeBlockDetector()
        val input = "```\nblock1\n```\n\n```js\nblock2\n```\n"
        val bytes = input.toUtf8()
        val blocks = detector.processChunk(bytes, bytes.size)
        assertThat(blocks).hasSize(2)
        assertThat(blocks[0].content).isEqualTo("block1")
        assertThat(blocks[1].language).isEqualTo("js")
        assertThat(blocks[1].content).isEqualTo("block2")
    }

    @Test
    fun processChunk_unterminatedBlock_trimsBuffer() {
        val detector = CodeBlockDetector()
        // Feed a large unterminated block to trigger buffer trimming
        val header = "```\n".toUtf8()
        detector.processChunk(header, header.size)
        val chunk = ByteArray(1024) { 'x'.code.toByte() }
        // 100 KB of x's, still no closing fence
        repeat(100) {
            val result = detector.processChunk(chunk, chunk.size)
            assertThat(result).isEmpty()
        }
        // No crash, no blocks yet
    }

    @Test
    fun processChunk_utf8MultiByteBoundary_handlesGracefully() {
        val detector = CodeBlockDetector()
        // € = 0xE2 0x82 0xAC in UTF-8
        val full = "```\nprice: \u20AC5\n```\n".toUtf8()
        // Find split point so that middle of € is across chunks.
        // Locate the 0xE2 byte:
        val euroStart = full.indexOfFirst { it == 0xE2.toByte() }
        assertThat(euroStart).isGreaterThan(0)
        val part1 = full.copyOfRange(0, euroStart + 1) // 0xE2 only
        val part2 = full.copyOfRange(euroStart + 1, full.size) // 0x82 0xAC ...

        val b1 = detector.processChunk(part1, part1.size)
        val b2 = detector.processChunk(part2, part2.size)
        val all = b1 + b2
        assertThat(all).hasSize(1)
        assertThat(all[0].content).isEqualTo("price: \u20AC5")
    }

    @Test
    fun processChunk_inlineBackticksInCode_doesNotTerminatePrematurely() {
        val detector = CodeBlockDetector()
        // Inline ``` mid-line should not match -- only line-start
        val input = "```\nthis line has ``` inline\nstill inside\n```\n"
        val bytes = input.toUtf8()
        val blocks = detector.processChunk(bytes, bytes.size)
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].content).isEqualTo("this line has ``` inline\nstill inside")
    }

    @Test
    fun reset_clearsBufferAndCarry() {
        val detector = CodeBlockDetector()
        val part1 = "```\npartial".toUtf8()
        detector.processChunk(part1, part1.size)
        detector.reset()
        // After reset, feeding ```\n...``` should detect fresh
        val full = "```\nfoo\n```\n".toUtf8()
        val blocks = detector.processChunk(full, full.size)
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].content).isEqualTo("foo")
    }
}
