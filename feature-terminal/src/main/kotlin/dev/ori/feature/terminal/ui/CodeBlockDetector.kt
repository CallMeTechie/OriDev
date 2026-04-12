package dev.ori.feature.terminal.ui

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DetectedCodeBlock(
    val id: String,
    val language: String?,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * CONCURRENCY CONTRACT: Instances of this class are NOT thread-safe.
 * Each terminal tab must own exactly ONE detector instance, and all calls
 * (`processChunk`, `reset`) MUST be made from the tab's reader coroutine
 * (confined to Dispatchers.IO). Cross-coroutine access will corrupt state.
 */
class CodeBlockDetector {
    private val textBuffer = StringBuilder()
    private val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    // Carry-over bytes for incomplete UTF-8 sequences at chunk boundaries
    private val carryBuffer: ByteBuffer = ByteBuffer.allocate(MAX_CARRY_BYTES)

    // Regex: multiline, anchor end to start of line to avoid inline backtick false positives
    private val blockStartRegex = Regex("""(?m)^```(\w*)\s*$""")
    private val blockEndRegex = Regex("""(?m)^```\s*$""")

    /**
     * Processes a chunk of terminal output bytes and returns newly detected complete code blocks.
     * UTF-8 safe: handles byte sequences split across calls.
     */
    fun processChunk(bytes: ByteArray, length: Int): List<DetectedCodeBlock> {
        // Decode with carry-over: prepend any bytes from previous incomplete sequence
        val incoming = ByteBuffer.wrap(bytes, 0, length)
        val combined = ByteBuffer.allocate(carryBuffer.position() + incoming.remaining())
        carryBuffer.flip()
        combined.put(carryBuffer)
        combined.put(incoming)
        combined.flip()

        val charBuffer = CharBuffer.allocate(combined.remaining() + 1)
        decoder.decode(combined, charBuffer, false)
        charBuffer.flip()
        textBuffer.append(charBuffer)

        // Save leftover bytes for next chunk
        carryBuffer.clear()
        if (combined.hasRemaining()) {
            carryBuffer.put(combined)
        }

        return detectBlocks()
    }

    private fun detectBlocks(): List<DetectedCodeBlock> {
        val blocks = mutableListOf<DetectedCodeBlock>()

        while (true) {
            val startMatch = blockStartRegex.find(textBuffer) ?: break
            val contentStart = startMatch.range.last + 1

            // End must be AFTER contentStart to avoid matching the opening as a close
            val endMatch = blockEndRegex.find(textBuffer, contentStart) ?: break

            val language = startMatch.groupValues[1].takeIf { it.isNotEmpty() }
            val content = textBuffer.substring(contentStart, endMatch.range.first).trim('\n', '\r')

            blocks.add(
                DetectedCodeBlock(
                    id = UUID.randomUUID().toString(),
                    language = language,
                    content = content,
                ),
            )

            // Delete processed region [startMatch.start, endMatch.endInclusive]
            textBuffer.delete(startMatch.range.first, endMatch.range.last + 1)
        }

        // Trim textBuffer if it grows unbounded (no closing fence found)
        if (textBuffer.length > MAX_BUFFER_SIZE) {
            textBuffer.delete(0, textBuffer.length - MAX_BUFFER_SIZE / 2)
        }

        return blocks
    }

    fun reset() {
        textBuffer.clear()
        carryBuffer.clear()
    }

    companion object {
        private const val MAX_BUFFER_SIZE = 64 * 1024
        private const val MAX_CARRY_BYTES = 8 // UTF-8 sequences are at most 4 bytes; 8 is safety margin
    }
}
