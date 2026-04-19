package dev.ori.feature.terminal.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 15 Task 15.3 — unit tests for the pure layout-decision
 * helpers that back the foldable split in [CustomKeyboard].
 *
 * These tests intentionally exercise only the *data* side of the
 * feature (breakpoint threshold + row partitioning). The composable
 * rendering branch (`if (split) SplitKeyboardBody else
 * PhoneKeyboardBody`) is a thin one-line wrapper around
 * [shouldUseSplit]; covering the pure function here gives us high
 * confidence without pulling Robolectric into the module. Same
 * rationale as [TerminalExtraKeysTest] — `feature-terminal` runs
 * JUnit 5 and adding JUnit 4 + Robolectric for one visual check
 * would be more churn than signal.
 */
class CustomKeyboardLayoutTest {

    // region shouldUseSplit — breakpoint semantics

    @Test
    fun shouldUseSplit_phoneWidth_returnsFalse() {
        // Pixel 9 portrait ≈ 412dp — pure phone path, no split.
        assertThat(shouldUseSplit(412)).isFalse()
    }

    @Test
    fun shouldUseSplit_smallTablet_returnsFalse() {
        // 599dp is exactly one dp below the breakpoint — still phone.
        assertThat(shouldUseSplit(599)).isFalse()
    }

    @Test
    fun shouldUseSplit_atBreakpoint_returnsTrue() {
        // 600dp is the configured cutover — matches TerminalScreen's
        // existing `screenWidthDp >= 600` split for consistency.
        assertThat(shouldUseSplit(FOLDABLE_SPLIT_BREAKPOINT_DP)).isTrue()
    }

    @Test
    fun shouldUseSplit_pixelFoldUnfolded_returnsTrue() {
        // Pixel Fold unfolded landscape ≈ 673dp — the target device
        // for this feature. Must split so both thumbs reach naturally.
        assertThat(shouldUseSplit(673)).isTrue()
    }

    @Test
    fun shouldUseSplit_tabletLandscape_returnsTrue() {
        // Large tablets also get the split — the same thumb-reach
        // problem applies, and the split just moves the two clusters
        // further apart, which is still the right UX.
        assertThat(shouldUseSplit(1024)).isTrue()
    }

    // endregion

    // region splitRow — partitioning the row into halves

    @Test
    fun splitRow_evenList_splitsDownTheMiddle() {
        val qwerty = "qwertyuiop".toList()
        val (left, right) = splitRow(qwerty)
        assertThat(left).containsExactly('q', 'w', 'e', 'r', 't').inOrder()
        assertThat(right).containsExactly('y', 'u', 'i', 'o', 'p').inOrder()
    }

    @Test
    fun splitRow_oddList_biasesLeftHalfLarger() {
        // 9-letter middle row — left takes 5 (`asdfg`), right takes 4
        // (`hjkl`). This keeps the letter `g` on the left so it lines
        // up visually with `t` above and `b` below.
        val asdf = "asdfghjkl".toList()
        val (left, right) = splitRow(asdf)
        assertThat(left).containsExactly('a', 's', 'd', 'f', 'g').inOrder()
        assertThat(right).containsExactly('h', 'j', 'k', 'l').inOrder()
    }

    @Test
    fun splitRow_twelveElements_splitsSixSix() {
        val numbers = "1234567890-=".toList()
        val (left, right) = splitRow(numbers)
        assertThat(left).hasSize(6)
        assertThat(right).hasSize(6)
        assertThat(left.last()).isEqualTo('6')
        assertThat(right.first()).isEqualTo('7')
    }

    @Test
    fun splitRow_singleElement_goesLeft() {
        val (left, right) = splitRow(listOf("only"))
        assertThat(left).containsExactly("only")
        assertThat(right).isEmpty()
    }

    @Test
    fun splitRow_empty_returnsTwoEmpties() {
        val (left, right) = splitRow(emptyList<String>())
        assertThat(left).isEmpty()
        assertThat(right).isEmpty()
    }

    // endregion

    // region breakpoint + gap constants are sensible

    @Test
    fun splitBreakpoint_matchesTerminalScreenThreshold() {
        // TerminalScreen uses the same `>= 600` cutover for its
        // list-detail switch. Keeping these in lock-step means a
        // user who is in the split terminal pane is also in the
        // split keyboard — no surprise mid-mode changes.
        assertThat(FOLDABLE_SPLIT_BREAKPOINT_DP).isEqualTo(600)
    }

    @Test
    fun splitGap_isThumbReachFriendly() {
        // 80dp ≈ one standard key width — wide enough to visually
        // separate the two halves without collapsing key space.
        // Tunable in a follow-up if Pixel Fold field-testing
        // suggests a different number.
        assertThat(FOLDABLE_SPLIT_GAP_DP).isAtLeast(40)
        assertThat(FOLDABLE_SPLIT_GAP_DP).isAtMost(160)
    }

    // endregion
}
