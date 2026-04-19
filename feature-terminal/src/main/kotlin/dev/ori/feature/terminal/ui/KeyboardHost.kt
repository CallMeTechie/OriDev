package dev.ori.feature.terminal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import dev.ori.domain.model.KeyboardMode

/**
 * Phase 14 Task 14.5 — single entry-point composable that renders the
 * correct keyboard surface under the terminal pane based on the user's
 * persisted [KeyboardMode]. Replaces the two direct `CustomKeyboard(...)`
 * call-sites in [TerminalScreen] (landscape + portrait) with one
 * mode-aware wrapper.
 *
 * ## The three modes
 *
 * - [KeyboardMode.CUSTOM] — renders the existing [CustomKeyboard]. No
 *   IME involvement, no `imePadding()` — the keyboard occupies a
 *   fixed slice of screen space governed by the drag-divider and
 *   `splitRatio` state.
 * - [KeyboardMode.HYBRID] — a vertical stack of [TerminalExtraKeys]
 *   (sticky row above the system IME) plus one [TerminalImeAnchor]
 *   (invisible 1×1 `BasicTextField` that consumes IME text). The IME
 *   push-up is owned by the [TerminalScreen] root (Task 15.2) — this
 *   host just renders its 53dp content, which naturally sits flush
 *   above the IME because the whole terminal stack above it is
 *   lifted. The extra-keys row is gated on [WindowInsets.isImeVisible]
 *   so it vanishes when the user dismisses Gboard via the back
 *   gesture.
 * - [KeyboardMode.SYSTEM_ONLY] — just the anchor, no extra-keys row.
 *   Power-user escape hatch for folks who want their full system IME
 *   real-estate without any Ori chrome.
 *
 * ## Invariants
 *
 * - **Exactly ONE `TerminalImeAnchor`** is rendered across the entire
 *   terminal screen (even when the user has multiple SSH tabs). This
 *   is critical: the anchor owns focus, and instantiating it per-tab
 *   would make every `SwitchTab` event steal-and-restore focus,
 *   which in practice means the Android IME slams shut and re-opens
 *   on every tab switch. By placing the anchor in KeyboardHost (a
 *   single instance rendered once in [TerminalScreen]), focus stays
 *   continuously held and the IME survives tab switches.
 * - [CUSTOM] does NOT render the anchor, so there is zero interaction
 *   with the system IME in that mode (the pre-Phase-14 behaviour).
 *
 * @param mode Current [KeyboardMode] from
 *   `KeyboardPreferences.keyboardModeFlow`, plumbed via
 *   [TerminalUiState.keyboardMode].
 * @param modifierState Current [ModifierState] (Task 14.3). Forwarded
 *   to [CustomKeyboard] and [TerminalExtraKeys]; ignored by pure
 *   system-IME mode.
 * @param imeFocusRequester Focus handle owned by the parent so a tap
 *   on the terminal pane can call `requestFocus()` to summon the IME.
 *   Unused in [CUSTOM] mode.
 * @param onInput Lambda that forwards IME-committed bytes to the
 *   ViewModel (wired to [TerminalEvent.SendInput]).
 * @param onEvent Standard ViewModel event dispatch for
 *   [CustomKeyboard] / [TerminalExtraKeys] key presses.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardHost(
    mode: KeyboardMode,
    modifierState: ModifierState,
    imeFocusRequester: FocusRequester,
    onInput: (ByteArray) -> Unit,
    onEvent: (TerminalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        KeyboardMode.CUSTOM -> {
            CustomKeyboard(
                modifierState = modifierState,
                onEvent = onEvent,
                modifier = modifier,
            )
        }
        KeyboardMode.HYBRID -> {
            // Phase 15 Task 15.2 — imePadding() was MOVED UP to the
            // TerminalScreen root Column; applying it here caused the
            // 53dp content to float at the bottom of a tall
            // (content + IME-height) column, leaving a large gap
            // above the system IME. Now the whole terminal stack
            // lifts uniformly when the IME opens, and this host just
            // renders its content directly above it.
            //
            // The extra-keys row is gated on isImeVisible so that
            // dismissing Gboard via the back-gesture also hides the
            // row (bug #4 in Task 15.2). The TerminalImeAnchor is
            // NOT gated — something has to own focus so re-tapping
            // the terminal pane can summon the IME again.
            Column(
                modifier = modifier.fillMaxWidth(),
            ) {
                if (WindowInsets.isImeVisible) {
                    TerminalExtraKeys(
                        modifierState = modifierState,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TerminalImeAnchor(
                    focusRequester = imeFocusRequester,
                    onInput = onInput,
                )
            }
        }
        KeyboardMode.SYSTEM_ONLY -> {
            // No extra-keys row; the user gets the raw system IME
            // real estate. Anchor still has to exist (something has
            // to own focus), but is invisible. Phase 15 Task 15.2 —
            // imePadding() + fillMaxWidth() stripped from here; the
            // parent-supplied modifier is passed through as-is and
            // the IME push-up is owned by the TerminalScreen root.
            TerminalImeAnchor(
                focusRequester = imeFocusRequester,
                onInput = onInput,
                modifier = modifier,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KeyboardHostHybridPreview() {
    val focusRequester = remember { FocusRequester() }
    KeyboardHost(
        mode = KeyboardMode.HYBRID,
        modifierState = ModifierState(),
        imeFocusRequester = focusRequester,
        onInput = {},
        onEvent = {},
    )
}
