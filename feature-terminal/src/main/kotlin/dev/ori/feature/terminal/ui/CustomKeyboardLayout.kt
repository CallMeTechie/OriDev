package dev.ori.feature.terminal.ui

/**
 * Phase 15 Task 15.3 — pure-data split definition for the unfolded
 * foldable CustomKeyboard layout.
 *
 * The default [CustomKeyboard] layout is a single horizontal block
 * that runs the full width of the window. On a Pixel Fold unfolded
 * (screenWidthDp ≈ 673) that places every key in a 12-column band
 * centred on the device — which looks fine statically but is
 * ergonomically hostile: both thumbs have to cross the centre fold
 * to reach the far side of each row, forcing a re-grip between
 * letters. User feedback called this out directly:
 *
 *   "die integrierte Tastatur ist linksbündig angeordnet, was bei
 *    einem Foldable das aufgeklappt ist, sehr ungünstig ist, man
 *    benötigt beide hände um die Tastatur zu bedienen. diese sollte
 *    eher zweigeteilt sein um sie mit beiden daumen von links und
 *    rechts bedienen zu können."
 *
 * When [shouldUseSplit] returns true the keyboard is rendered as
 * `Row { LeftHalf weight 1; Spacer(GAP); RightHalf weight 1 }` so
 * both thumbs land naturally on their own cluster of keys. The
 * breakpoint mirrors [TerminalScreen]'s existing
 * `screenWidthDp >= 600` split — the same threshold already
 * separates list-detail vs. stacked navigation, so re-using it
 * keeps the fold-vs-phone cutover consistent across the app.
 *
 * This file intentionally holds *only* the layout decision and the
 * per-side key partitioning. The concrete button rendering
 * (KeyButton/ToggleKeyButton/RepeatKeyButton/SpaceKeyButton) still
 * lives in [CustomKeyboard] because it drags in a lot of Compose
 * state — extracting just the side split keeps this file
 * testable as pure Kotlin.
 */

/** Width breakpoint at which the keyboard switches to the split layout. */
internal const val FOLDABLE_SPLIT_BREAKPOINT_DP = 600

/** Gap between the two halves on the split layout, in dp. */
internal const val FOLDABLE_SPLIT_GAP_DP = 80

/**
 * Pure layout decision — true when the keyboard should render as
 * two half-width clusters with a mid-gap, false when it should
 * render as a single full-width block (the legacy phone path).
 *
 * Extracted as a pure function so it can be unit-tested without
 * Compose / Robolectric; [CustomKeyboard] just calls this with
 * `LocalConfiguration.current.screenWidthDp`.
 */
internal fun shouldUseSplit(screenWidthDp: Int): Boolean =
    screenWidthDp >= FOLDABLE_SPLIT_BREAKPOINT_DP

/**
 * Partitions a character sequence into (left, right) halves for the
 * split layout. The left half gets the first `ceil(n/2)` items so
 * that an 11-element row ("1234567890-=") splits 6 | 6 and a
 * 10-element row ("qwertyuiop") splits 5 | 5.
 */
internal fun <T> splitRow(items: List<T>): Pair<List<T>, List<T>> {
    val mid = (items.size + 1) / 2
    return items.take(mid) to items.drop(mid)
}

/**
 * Side descriptor — passed to the per-row composable helpers in
 * [CustomKeyboard] so they know which half of their keys to emit.
 * [FULL] is the legacy phone path (single block, everything on one
 * row); [LEFT] / [RIGHT] are the split halves.
 */
internal enum class KeyboardSide { FULL, LEFT, RIGHT }
