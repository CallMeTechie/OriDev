package dev.ori.feature.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Invisible 1×1 [BasicTextField] that acts as the anchor for the Android system IME
 * in Ori:Dev's HYBRID / SYSTEM_ONLY terminal keyboard modes.
 *
 * ## Why this exists (Phase 14 — Terminal Hybrid Keyboard)
 *
 * The terminal pane renders its own buffer and cannot directly receive IME input.
 * This composable consumes focus so the system keyboard has somewhere to send
 * text, then converts committed characters into UTF-8 bytes that the parent
 * forwards to the shell.
 *
 * ## Design invariants (devil's-advocate review R1 & R2)
 *
 * 1. **Composing-text awareness.** Swipe-typing on Gboard / SwiftKey fires
 *    [onValueChange] many times per second while the underlined composition
 *    string evolves (`"h"` → `"he"` → `"hel"` …). A naive diff would flood the
 *    shell with every composing frame. We therefore IGNORE any update where
 *    [TextFieldValue.composition] is non-null, and emit bytes only once the
 *    composition clears (i.e. the IME has committed the word, usually when
 *    the user lifts their finger or hits space). After emit we reset the
 *    field back to `TextFieldValue("")`.
 *
 * 2. **Suppress IME dictionary learning.** SSH passwords, hostnames, and
 *    command-line secrets typed in the terminal must not leak into Gboard's
 *    personal dictionary, Gboard Cloud Sync, or SwiftKey's learning engine.
 *    [KeyboardType.Password] is the Android signal that disables dictionary
 *    learning across all major IMEs; we pair it with `autoCorrectEnabled = false`
 *    to belt-and-suspender the guarantee. This mirrors the project's
 *    existing security posture (Android Keystore, CharArray passwords,
 *    clipboard sensitive flag).
 *
 * @param focusRequester Provided by the parent so a tap on the terminal pane
 *   can call [FocusRequester.requestFocus] to summon the IME.
 * @param onInput Called with the UTF-8 bytes of each committed chunk.
 *   Invariant: never called while a composition is in flight.
 * @param modifier Normally left default — the composable is already sized 1×1
 *   and fully transparent.
 */
@Composable
fun TerminalImeAnchor(
    focusRequester: FocusRequester,
    onInput: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }

    Box(
        modifier = modifier
            .size(1.dp)
            .alpha(0f)
            .semantics { hideFromAccessibility() },
    ) {
        BasicTextField(
            value = value,
            onValueChange = { new ->
                val committed = committedDelta(previous = value, current = new)
                if (committed != null) {
                    if (committed.isNotEmpty()) {
                        onInput(committed.toByteArray(Charsets.UTF_8))
                    }
                    // Reset so the field never accumulates text — we only care
                    // about the delta between commits, not the running buffer.
                    value = TextFieldValue(text = "", selection = TextRange.Zero)
                } else {
                    // Composition in flight (swipe-typing, IME preview, etc.)
                    // Track the state so Compose renders the underline, but
                    // do NOT forward anything to the shell yet.
                    value = new
                }
            },
            keyboardOptions = TerminalImeAnchorKeyboardOptions,
            singleLine = true,
            modifier = Modifier
                .focusRequester(focusRequester)
                .onKeyEvent { event -> handleKeyEvent(event, onInput) },
        )
    }
}

/**
 * [KeyboardOptions] used by [TerminalImeAnchor]. Exposed at file scope so
 * unit tests can snapshot-assert the configuration without driving Compose.
 *
 * - `autoCorrectEnabled = false`: disables inline correction popups.
 * - `keyboardType = KeyboardType.Password`: the Android contract signal that
 *   disables dictionary learning / cloud sync across Gboard & SwiftKey.
 * - `imeAction = ImeAction.None`: no "Done"/"Send" affordance — Enter is
 *   handled by [handleKeyEvent] and mapped to `0x0D`.
 */
internal val TerminalImeAnchorKeyboardOptions: KeyboardOptions = KeyboardOptions(
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Password,
    imeAction = ImeAction.None,
)

/**
 * Pure extraction of the composing-vs-committed decision used by
 * [TerminalImeAnchor.onValueChange]. Returning `null` means "still composing,
 * do not emit anything". Returning a string (possibly empty) means "composition
 * cleared, this is the committed delta".
 *
 * Rules:
 * - If [current] has a non-null composition, the IME is mid-word — return `null`.
 * - Otherwise the IME has committed. The delta is the text that was added on
 *   top of [previous]'s committed portion. Since the anchor resets to `""`
 *   after every emit, [previous].text is typically `""` and the delta is
 *   simply [current].text — but if a composition was in flight, [previous]
 *   holds the composing preview, and we only care about the text OUTSIDE
 *   that composition range when computing the committed delta.
 *
 * The practical implementation: once composition is null, whatever text is in
 * [current] that wasn't already in [previous]'s *non-composing* portion is
 * what the IME just committed.
 */
internal fun committedDelta(
    previous: TextFieldValue,
    current: TextFieldValue,
): String? {
    if (current.composition != null) {
        // Still composing — do not emit.
        return null
    }
    // Composition just cleared (or there never was one). Compute what is new
    // relative to the committed portion of [previous].
    val previousCommitted = previous.committedText()
    val currentText = current.text
    return if (currentText.startsWith(previousCommitted)) {
        currentText.substring(previousCommitted.length)
    } else {
        // Fallback: IME did something non-append-y (e.g. replaced selection
        // entirely). Emit the full current text — better to over-send than
        // to drop user input.
        currentText
    }
}

/**
 * The portion of a [TextFieldValue] that is NOT part of the active composition.
 * When composition is null, that's the whole text.
 */
private fun TextFieldValue.committedText(): String {
    val comp = composition ?: return text
    // Strip the composing substring out of [text]. composition is a TextRange
    // over [text], so concatenate the pieces before/after it.
    val start = comp.min.coerceIn(0, text.length)
    val end = comp.max.coerceIn(0, text.length)
    return text.substring(0, start) + text.substring(end)
}

/**
 * Direct key-event shortcut used by [TerminalImeAnchor.onKeyEvent]. Hardware
 * keyboards and some IME "Enter"/"Backspace" paths come through here instead
 * of via [TextFieldValue] commits.
 *
 * - Enter  → `0x0D` (carriage return, what a shell expects)
 * - Backspace → `0x7F` (DEL, the byte xterm & friends emit for backspace)
 *
 * Space is deliberately NOT handled here — space arrives through the normal
 * IME commit path so swipe-typed words (which end in a space commit) work
 * correctly.
 *
 * Returns `true` when the event was consumed, `false` to let Compose handle it.
 */
internal fun handleKeyEvent(
    event: KeyEvent,
    onInput: (ByteArray) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> {
            onInput(byteArrayOf(0x0D))
            true
        }
        Key.Backspace -> {
            onInput(byteArrayOf(0x7F))
            true
        }
        else -> false
    }
}
