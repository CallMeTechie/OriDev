package dev.ori.feature.terminal.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 14 Task 14.3 — unit tests for the pure modifier translator
 * [translateForModifiers]. Covers the full Ctrl table (letters +
 * non-letters), Alt-prefix, Ctrl+Alt composition, sticky latch
 * behaviour, tab-switch reset, and the pass-through fallback for
 * unsupported characters.
 */
class ModifierStateTest {

    private val none = ModifierState()
    private val ctrl = ModifierState(ctrl = true)
    private val alt = ModifierState(alt = true)
    private val ctrlAlt = ModifierState(ctrl = true, alt = true)

    // region Ctrl + A–Z round-trip

    @Test
    fun translateForModifiers_ctrlLowercaseC_emits0x03() {
        val result = translateForModifiers("c", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x03))
    }

    @Test
    fun translateForModifiers_ctrlUppercaseC_emits0x03() {
        val result = translateForModifiers("C", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x03))
    }

    @Test
    fun translateForModifiers_ctrlA_emits0x01() {
        val result = translateForModifiers("a", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x01))
    }

    @Test
    fun translateForModifiers_ctrlZ_emits0x1A() {
        val result = translateForModifiers("z", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x1A))
    }

    // endregion

    // region Ctrl + non-letter round-trip (power-user readline/tmux bindings)

    @Test
    fun translateForModifiers_ctrlAt_emitsNul() {
        val result = translateForModifiers("@", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x00))
    }

    @Test
    fun translateForModifiers_ctrlSpace_emitsNul() {
        val result = translateForModifiers(" ", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x00))
    }

    @Test
    fun translateForModifiers_ctrlLeftBracket_emitsEsc() {
        val result = translateForModifiers("[", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x1B))
    }

    @Test
    fun translateForModifiers_ctrlBackslash_emitsFs() {
        val result = translateForModifiers("\\", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x1C))
    }

    @Test
    fun translateForModifiers_ctrlRightBracket_emitsGs() {
        val result = translateForModifiers("]", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x1D))
    }

    @Test
    fun translateForModifiers_ctrlCaret_emitsRs() {
        val result = translateForModifiers("^", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x1E))
    }

    @Test
    fun translateForModifiers_ctrlUnderscore_emitsUs() {
        val result = translateForModifiers("_", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x1F))
    }

    @Test
    fun translateForModifiers_ctrlQuestionMark_emitsDel() {
        val result = translateForModifiers("?", ctrl)
        assertThat(result).isEqualTo(byteArrayOf(0x7F.toByte()))
    }

    // endregion

    // region Alt and Ctrl+Alt composition

    @Test
    fun translateForModifiers_altLowercaseX_emitsEscX() {
        val result = translateForModifiers("x", alt)
        assertThat(result).isEqualTo(byteArrayOf(0x1B, 'x'.code.toByte()))
    }

    @Test
    fun translateForModifiers_ctrlAltLowercaseC_emitsEscThen0x03() {
        val result = translateForModifiers("c", ctrlAlt)
        assertThat(result).isEqualTo(byteArrayOf(0x1B, 0x03))
    }

    // endregion

    // region Pass-through (unsupported + no modifiers)

    @Test
    fun translateForModifiers_noModifiers_passesThroughUtf8() {
        val result = translateForModifiers("hi", none)
        assertThat(result).isEqualTo("hi".toByteArray())
    }

    @Test
    fun translateForModifiers_ctrlEmoji_doesNotCrashAndPassesThrough() {
        val emoji = "\uD83D\uDE80" // rocket
        val result = translateForModifiers(emoji, ctrl)
        assertThat(result).isEqualTo(emoji.toByteArray())
    }

    @Test
    fun translateForModifiers_emptyString_emitsEmptyArray() {
        val result = translateForModifiers("", ctrl)
        assertThat(result).isEmpty()
    }

    @Test
    fun translateForModifiers_emptyStringWithAlt_emitsEmptyArray() {
        // Edge: if there's no character to prefix, no ESC is emitted.
        // Callers never send empty SendText in practice, but the
        // translator must not crash.
        val result = translateForModifiers("", alt)
        assertThat(result).isEmpty()
    }

    // endregion

    // region ModifierState toggling semantics (ViewModel-facing contract)

    @Test
    fun modifierState_default_hasAllFalse() {
        val state = ModifierState()
        assertThat(state.ctrl).isFalse()
        assertThat(state.alt).isFalse()
        assertThat(state.sticky).isFalse()
    }

    @Test
    fun modifierState_copyWithCtrlTrue_onlyChangesCtrl() {
        val state = ModifierState().copy(ctrl = true)
        assertThat(state.ctrl).isTrue()
        assertThat(state.alt).isFalse()
        assertThat(state.sticky).isFalse()
    }

    // endregion

    // region Sticky latch — simulates ViewModel's "clear unless sticky" rule

    @Test
    fun stickyCtrl_staysLatchedAcrossTwoChars_untilUntoggled() {
        // Step 1: sticky Ctrl latched
        var state = ModifierState(ctrl = true, sticky = true)

        // Emit 1: Ctrl+a = 0x01
        val first = translateForModifiers("a", state)
        assertThat(first).isEqualTo(byteArrayOf(0x01))

        // Simulate ViewModel's "clear non-sticky" logic: sticky stays.
        state = if (!state.sticky) state.copy(ctrl = false, alt = false) else state
        assertThat(state.ctrl).isTrue()

        // Emit 2: Ctrl+b = 0x02 (sticky still held)
        val second = translateForModifiers("b", state)
        assertThat(second).isEqualTo(byteArrayOf(0x02))
        state = if (!state.sticky) state.copy(ctrl = false, alt = false) else state
        assertThat(state.ctrl).isTrue()

        // User explicitly toggles Ctrl off. (ToggleCtrl event)
        state = state.copy(ctrl = false)

        // Emit 3: plain 'c' — no translation.
        val third = translateForModifiers("c", state)
        assertThat(third).isEqualTo("c".toByteArray())
    }

    @Test
    fun nonStickyCtrl_clearsAfterFirstEmit() {
        val initial = ModifierState(ctrl = true, sticky = false)
        val first = translateForModifiers("a", initial)
        assertThat(first).isEqualTo(byteArrayOf(0x01))

        // Simulate ViewModel's clear-after-emit. Next char is plain.
        val afterEmit = initial.copy(ctrl = false, alt = false)
        val second = translateForModifiers("b", afterEmit)
        assertThat(second).isEqualTo("b".toByteArray())
    }

    // endregion

    // region Tab-switch reset — ViewModel contract

    @Test
    fun switchTab_resetSemantics_clearsCtrlAndAltButKeepsSticky() {
        // Simulates what TerminalViewModel.switchTab does: the
        // ModifierState mutation pattern used there is copy(ctrl=false,
        // alt=false). Sticky is a user preference, not per-tab state.
        val before = ModifierState(ctrl = true, alt = true, sticky = true)
        val afterSwitch = before.copy(ctrl = false, alt = false)

        assertThat(afterSwitch.ctrl).isFalse()
        assertThat(afterSwitch.alt).isFalse()
        assertThat(afterSwitch.sticky).isTrue()

        // And post-reset, translation passes through normally.
        val bytes = translateForModifiers("a", afterSwitch)
        assertThat(bytes).isEqualTo("a".toByteArray())
    }

    // endregion
}
