package dev.ori.feature.terminal.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TerminalImeAnchor] pure helpers.
 *
 * Per Phase 14 Task 14.2 plan: byte-extraction & key-event logic is extracted
 * into `internal` functions so these tests can run in the plain JVM unit test
 * source set (no Robolectric, no Compose-UI test runner required).
 */
class TerminalImeAnchorTest {

    // region committedDelta — composing / commit behaviour

    @Test
    fun committedDelta_singleCharCommit_returnsSingleChar() {
        val previous = TextFieldValue(text = "")
        val current = TextFieldValue(text = "a")

        val delta = committedDelta(previous, current)

        assertThat(delta).isEqualTo("a")
        assertThat(delta!!.toByteArray(Charsets.UTF_8)).isEqualTo(byteArrayOf(0x61))
    }

    @Test
    fun committedDelta_compositionActive_returnsNull() {
        // IME is mid-swipe: "he" is shown with the composing underline.
        val previous = TextFieldValue(text = "")
        val composingH = TextFieldValue(text = "h", composition = TextRange(0, 1))
        val composingHe = TextFieldValue(text = "he", composition = TextRange(0, 2))

        assertThat(committedDelta(previous, composingH)).isNull()
        assertThat(committedDelta(composingH, composingHe)).isNull()
    }

    @Test
    fun committedDelta_compositionClearsAfterSpace_returnsCommittedWord() {
        // User types "he" as a composition, then hits space: IME commits "he ".
        val composingHe = TextFieldValue(text = "he", composition = TextRange(0, 2))
        val committed = TextFieldValue(text = "he ", composition = null)

        val delta = committedDelta(composingHe, committed)

        // The previous's committed-portion was "" (everything was composing),
        // so the whole of current is the new committed delta.
        assertThat(delta).isEqualTo("he ")
        assertThat(delta!!.toByteArray(Charsets.UTF_8)).hasLength(3)
    }

    @Test
    fun committedDelta_swipeTypingEightFramesCollapseToOneCommit_emitsExactlyFiveBytes() {
        // Simulate Gboard swipe-typing "hello": eight composing frames, then commit.
        val frames = listOf(
            TextFieldValue(text = "", composition = null),
            TextFieldValue(text = "h", composition = TextRange(0, 1)),
            TextFieldValue(text = "he", composition = TextRange(0, 2)),
            TextFieldValue(text = "hel", composition = TextRange(0, 3)),
            TextFieldValue(text = "hell", composition = TextRange(0, 4)),
            TextFieldValue(text = "hello", composition = TextRange(0, 5)),
            TextFieldValue(text = "helloo", composition = TextRange(0, 6)),
            TextFieldValue(text = "hellw", composition = TextRange(0, 5)),
            TextFieldValue(text = "hello", composition = TextRange(0, 5)),
        )
        // Final frame: composition clears.
        val committed = TextFieldValue(text = "hello", composition = null)

        val emitted = mutableListOf<ByteArray>()
        var previous = frames[0]
        for (i in 1 until frames.size) {
            val delta = committedDelta(previous, frames[i])
            if (delta != null && delta.isNotEmpty()) {
                emitted.add(delta.toByteArray(Charsets.UTF_8))
            }
            previous = frames[i]
        }
        val finalDelta = committedDelta(previous, committed)
        if (finalDelta != null && finalDelta.isNotEmpty()) {
            emitted.add(finalDelta.toByteArray(Charsets.UTF_8))
        }

        // Exactly ONE emit, exactly 5 bytes ("hello").
        assertThat(emitted).hasSize(1)
        assertThat(emitted[0]).isEqualTo("hello".toByteArray(Charsets.UTF_8))
        assertThat(emitted[0]).hasLength(5)
    }

    @Test
    fun committedDelta_umlautCommit_returnsTwoUtf8Bytes() {
        // UTF-8 round-trip: "ö" (U+00F6) must serialise to 0xC3 0xB6.
        val previous = TextFieldValue(text = "")
        val current = TextFieldValue(text = "ö")

        val delta = committedDelta(previous, current)

        assertThat(delta).isEqualTo("ö")
        val bytes = delta!!.toByteArray(Charsets.UTF_8)
        assertThat(bytes).isEqualTo(byteArrayOf(0xC3.toByte(), 0xB6.toByte()))
        assertThat(bytes).hasLength(2)
    }

    // endregion

    // region handleKeyEvent — direct key shortcuts

    @Test
    fun handleKeyEvent_enterKeyDown_emitsCarriageReturnByte() {
        val event = keyDown(Key.Enter)
        val emitted = mutableListOf<ByteArray>()

        val consumed = handleKeyEvent(event) { emitted.add(it) }

        assertThat(consumed).isTrue()
        assertThat(emitted).hasSize(1)
        assertThat(emitted[0]).isEqualTo(byteArrayOf(0x0D))
    }

    @Test
    fun handleKeyEvent_backspaceKeyDown_emitsDelByte() {
        val event = keyDown(Key.Backspace)
        val emitted = mutableListOf<ByteArray>()

        val consumed = handleKeyEvent(event) { emitted.add(it) }

        assertThat(consumed).isTrue()
        assertThat(emitted).hasSize(1)
        assertThat(emitted[0]).isEqualTo(byteArrayOf(0x7F))
    }

    @Test
    fun handleKeyEvent_keyUpEvents_areIgnored() {
        // Only KeyDown should produce output; KeyUp must not double-emit.
        val event = keyUp(Key.Enter)
        val emitted = mutableListOf<ByteArray>()

        val consumed = handleKeyEvent(event) { emitted.add(it) }

        assertThat(consumed).isFalse()
        assertThat(emitted).isEmpty()
    }

    @Test
    fun handleKeyEvent_spaceKeyDown_isNotHandled() {
        // Space must NOT be shortcut here — it travels through the normal
        // IME commit path so swipe-typed words (which end in space) work.
        val event = keyDown(Key.Spacebar)
        val emitted = mutableListOf<ByteArray>()

        val consumed = handleKeyEvent(event) { emitted.add(it) }

        assertThat(consumed).isFalse()
        assertThat(emitted).isEmpty()
    }

    // endregion

    // region KeyboardOptions — dictionary-learning suppression

    @Test
    fun terminalImeAnchorKeyboardOptions_suppressesDictionaryLearning() {
        val options: KeyboardOptions = TerminalImeAnchorKeyboardOptions

        // R2: KeyboardType.Password is the Android signal that disables
        // dictionary learning across Gboard / SwiftKey. This is the single
        // most load-bearing assertion in this file — do not relax it.
        assertThat(options.keyboardType).isEqualTo(KeyboardType.Password)
        assertThat(options.autoCorrectEnabled).isFalse()
        assertThat(options.imeAction).isEqualTo(ImeAction.None)
    }

    // endregion

    // region test helpers

    /**
     * Build a [KeyEvent] with [type] = KeyDown and the given [key]. The
     * nativeKeyEvent is mocked because `android.view.KeyEvent` is a stub in
     * JVM unit tests (testOptions.unitTests.isReturnDefaultValues = true).
     */
    private fun keyDown(key: Key): KeyEvent = mockKeyEvent(key, KeyEventType.KeyDown)

    private fun keyUp(key: Key): KeyEvent = mockKeyEvent(key, KeyEventType.KeyUp)

    private fun mockKeyEvent(key: Key, eventType: KeyEventType): KeyEvent {
        // androidx.compose.ui.input.key.KeyEvent is a value class wrapping
        // android.view.KeyEvent. We can't easily construct a real one from
        // pure JVM, so we mockk the whole wrapper and stub the extension
        // properties via their backing fields.
        val native = mockk<android.view.KeyEvent>(relaxed = true)
        every { native.keyCode } returns when (key) {
            Key.Enter -> android.view.KeyEvent.KEYCODE_ENTER
            Key.NumPadEnter -> android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
            Key.Backspace -> android.view.KeyEvent.KEYCODE_DEL
            Key.Spacebar -> android.view.KeyEvent.KEYCODE_SPACE
            else -> 0
        }
        every { native.action } returns when (eventType) {
            KeyEventType.KeyDown -> android.view.KeyEvent.ACTION_DOWN
            KeyEventType.KeyUp -> android.view.KeyEvent.ACTION_UP
            else -> 0
        }
        return KeyEvent(native)
    }

    // endregion
}
