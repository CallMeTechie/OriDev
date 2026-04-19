package dev.ori.feature.terminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
 *   (invisible 1×1 `BasicTextField` that consumes IME text). The
 *   whole column uses `Modifier.imePadding()` so the system IME
 *   pushes the row up off the bottom inset.
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
            // imePadding() on the outer column so the whole stack
            // (extra-keys row + anchor) lifts with the system IME.
            // The anchor itself is 1×1 / alpha 0, so in practice the
            // user sees only the TerminalExtraKeys row sitting on top
            // of Gboard/SwiftKey.
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .imePadding(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                TerminalExtraKeys(
                    modifierState = modifierState,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxWidth(),
                )
                TerminalImeAnchor(
                    focusRequester = imeFocusRequester,
                    onInput = onInput,
                )
            }
        }
        KeyboardMode.SYSTEM_ONLY -> {
            // No extra-keys row; the user gets the raw system IME
            // real estate. Anchor still has to exist (something has
            // to own focus), but is invisible — so we drop the
            // redundant Column wrapper and pass imePadding() straight
            // to the anchor.
            TerminalImeAnchor(
                focusRequester = imeFocusRequester,
                onInput = onInput,
                modifier = modifier
                    .fillMaxWidth()
                    .imePadding(),
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
