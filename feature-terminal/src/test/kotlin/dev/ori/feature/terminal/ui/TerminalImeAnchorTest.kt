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

        val result = committedDelta(previous, current)

        assertThat(result).isInstanceOf(CommitResult.Insert::class.java)
        val insert = result as CommitResult.Insert
        assertThat(insert.text).isEqualTo("a")
        assertThat(insert.text.toByteArray(Charsets.UTF_8)).isEqualTo(byteArrayOf(0x61))
    }

    @Test
    fun committedDelta_compositionActive_returnsNoOp() {
        // IME is mid-swipe: "he" is shown with the composing underline.
        val previous = TextFieldValue(text = "")
        val composingH = TextFieldValue(text = "h", composition = TextRange(0, 1))
        val composingHe = TextFieldValue(text = "he", composition = TextRange(0, 2))

        assertThat(committedDelta(previous, composingH)).isEqualTo(CommitResult.NoOp)
        assertThat(committedDelta(composingH, composingHe)).isEqualTo(CommitResult.NoOp)
    }

    @Test
    fun committedDelta_compositionClearsAfterSpace_returnsCommittedWord() {
        // User types "he" as a composition, then hits space: IME commits "he ".
        val composingHe = TextFieldValue(text = "he", composition = TextRange(0, 2))
        val committed = TextFieldValue(text = "he ", composition = null)

        val result = committedDelta(composingHe, committed)

        // The previous's committed-portion was "" (everything was composing),
        // so the whole of current is the new committed delta.
        assertThat(result).isInstanceOf(CommitResult.Insert::class.java)
        val insert = result as CommitResult.Insert
        assertThat(insert.text).isEqualTo("he ")
        assertThat(insert.text.toByteArray(Charsets.UTF_8)).hasLength(3)
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
            val result = committedDelta(previous, frames[i])
            if (result is CommitResult.Insert && result.text.isNotEmpty()) {
                emitted.add(result.text.toByteArray(Charsets.UTF_8))
            }
            previous = frames[i]
        }
        val finalResult = committedDelta(previous, committed)
        if (finalResult is CommitResult.Insert && finalResult.text.isNotEmpty()) {
            emitted.add(finalResult.text.toByteArray(Charsets.UTF_8))
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

        val result = committedDelta(previous, current)

        assertThat(result).isInstanceOf(CommitResult.Insert::class.java)
        val insert = result as CommitResult.Insert
        assertThat(insert.text).isEqualTo("ö")
        val bytes = insert.text.toByteArray(Charsets.UTF_8)
        assertThat(bytes).isEqualTo(byteArrayOf(0xC3.toByte(), 0xB6.toByte()))
        assertThat(bytes).hasLength(2)
    }

    @Test
    fun committedDelta_selectionReplacedEntirely_returnsFullCurrentText() {
        // Exercises the DEFENSIVE FALLBACK branch in committedDelta: when
        // currentText does not start with previousCommitted AND is not a pure
        // shrink. In production this branch is unreachable because the anchor
        // resets state to "" after every emit, but a future refactor or an
        // odd IME could hit it — we want to over-send rather than drop input.
        val previous = TextFieldValue(text = "foo", composition = null)
        val current = TextFieldValue(text = "xxx", composition = null)

        val result = committedDelta(previous, current)

        assertThat(result).isInstanceOf(CommitResult.Insert::class.java)
        val insert = result as CommitResult.Insert
        assertThat(insert.text).isEqualTo("xxx")
    }

    // endregion

    // region committedDelta — soft-keyboard Backspace (text-shrink) detection

    @Test
    fun committedDelta_textShrinksByOne_returnsDeleteOne() {
        // Gboard: user typed "abc" (now in the committed portion), then tapped
        // the on-screen Backspace key. Gboard routes that as a text-change
        // from "abc" → "ab", NOT as a KeyEvent. We must detect it here.
        val previous = TextFieldValue(text = "abc", composition = null)
        val current = TextFieldValue(text = "ab", composition = null)

        val result = committedDelta(previous, current)

        assertThat(result).isEqualTo(CommitResult.Delete(count = 1))
    }

    @Test
    fun committedDelta_textClearedFromNonEmpty_returnsDeleteAllChars() {
        // User held Backspace or the IME nuked the whole field in one frame.
        val previous = TextFieldValue(text = "hello", composition = null)
        val current = TextFieldValue(text = "", composition = null)

        val result = committedDelta(previous, current)

        assertThat(result).isEqualTo(CommitResult.Delete(count = 5))
    }

    @Test
    fun committedDelta_typeAbcThenThreeBackspaces_emitsExpectedByteSequence() {
        // Integration-style: simulate the real onValueChange loop that the
        // composable runs. Anchor resets to "" after every emit (Insert OR
        // Delete), so each Backspace step sees previous="X"/current="".

        // Step 1: type "a" (committed, no composition).
        // Step 2: anchor reset → type "b".
        // Step 3: anchor reset → type "c".
        // Step 4: soft Backspace — field showed "" (post-reset) briefly,
        //         but for Backspace-after-emit we model the case where the
        //         field held the typed char and then shrank. Concretely:
        //         simulate previous="c", current="" (length shrink).
        // Steps 5/6: another two Backspaces, modelled the same way.
        val emitted = mutableListOf<Byte>()
        fun drive(previous: TextFieldValue, current: TextFieldValue): TextFieldValue {
            return when (val r = committedDelta(previous, current)) {
                CommitResult.NoOp -> current
                is CommitResult.Insert -> {
                    emitted += r.text.toByteArray(Charsets.UTF_8).toList()
                    TextFieldValue("")
                }
                is CommitResult.Delete -> {
                    repeat(r.count) { emitted += 0x7F.toByte() }
                    TextFieldValue("")
                }
            }
        }

        var state = TextFieldValue("")
        state = drive(state, TextFieldValue("a"))
        state = drive(state, TextFieldValue("b"))
        state = drive(state, TextFieldValue("c"))
        // Now simulate three soft-Backspaces. After each Insert the field was
        // reset to "", so to model a real shrink we pretend the user typed a
        // char and then backspaced before the next state observation. In the
        // canonical flow described by the issue, each Backspace path goes:
        //   previous holds 1 char → current is "".
        state = drive(TextFieldValue("a"), TextFieldValue(""))
        state = drive(TextFieldValue("a"), TextFieldValue(""))
        state = drive(TextFieldValue("a"), TextFieldValue(""))

        assertThat(emitted).containsExactly(
            0x61.toByte(), // 'a'
            0x62.toByte(), // 'b'
            0x63.toByte(), // 'c'
            0x7F.toByte(), // DEL
            0x7F.toByte(), // DEL
            0x7F.toByte(), // DEL
        ).inOrder()
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
