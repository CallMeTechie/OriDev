package dev.ori.feature.terminal.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 14 Task 14.4 — unit tests for the pure [keyToBytes] lookup
 * table that the [TerminalExtraKeys] composable delegates to. The
 * composable itself is a thin visual layer over this table, so
 * covering every [ExtraKey] mapping here gives us high confidence
 * in the shell-byte output without spinning up Compose.
 *
 * The plan allows a Robolectric compose test for the long-press →
 * `ToggleStickyModifier` gesture; we intentionally skip that here
 * because `feature-terminal` uses JUnit 5 (`useJUnitPlatform()` in
 * `build.gradle.kts`) while Robolectric requires the JUnit 4
 * runner. Adding a second test runner to this module for a single
 * gesture test would be more churn than the signal warrants — the
 * gesture composition is a Compose-native `detectTapGestures`
 * call and well-covered by the Compose test kit upstream. Task
 * 14.5 wires the row into `KeyboardHost` with live integration.
 */
class TerminalExtraKeysTest {

    // region keyToBytes — non-modifier keys that emit a SendInput payload

    @Test
    fun keyToBytes_esc_emits0x1B() {
        assertThat(keyToBytes(ExtraKey.Esc)).isEqualTo(byteArrayOf(0x1B))
    }

    @Test
    fun keyToBytes_tab_emits0x09() {
        assertThat(keyToBytes(ExtraKey.Tab)).isEqualTo(byteArrayOf(0x09))
    }

    @Test
    fun keyToBytes_arrowUp_emitsCsiA() {
        assertThat(keyToBytes(ExtraKey.ArrowUp)).isEqualTo("\u001b[A".toByteArray())
    }

    @Test
    fun keyToBytes_arrowDown_emitsCsiB() {
        assertThat(keyToBytes(ExtraKey.ArrowDown)).isEqualTo("\u001b[B".toByteArray())
    }

    @Test
    fun keyToBytes_arrowRight_emitsCsiC() {
        assertThat(keyToBytes(ExtraKey.ArrowRight)).isEqualTo("\u001b[C".toByteArray())
    }

    @Test
    fun keyToBytes_arrowLeft_emitsCsiD() {
        assertThat(keyToBytes(ExtraKey.ArrowLeft)).isEqualTo("\u001b[D".toByteArray())
    }

    @Test
    fun keyToBytes_pipe_emitsUtf8Pipe() {
        assertThat(keyToBytes(ExtraKey.Pipe)).isEqualTo("|".toByteArray())
    }

    @Test
    fun keyToBytes_slash_emitsUtf8Slash() {
        assertThat(keyToBytes(ExtraKey.Slash)).isEqualTo("/".toByteArray())
    }

    @Test
    fun keyToBytes_tilde_emitsUtf8Tilde() {
        assertThat(keyToBytes(ExtraKey.Tilde)).isEqualTo("~".toByteArray())
    }

    @Test
    fun keyToBytes_backtick_emitsUtf8Backtick() {
        assertThat(keyToBytes(ExtraKey.Backtick)).isEqualTo("`".toByteArray())
    }

    @Test
    fun keyToBytes_home_emitsCsiH() {
        assertThat(keyToBytes(ExtraKey.Home)).isEqualTo("\u001b[H".toByteArray())
    }

    @Test
    fun keyToBytes_end_emitsCsiF() {
        assertThat(keyToBytes(ExtraKey.End)).isEqualTo("\u001b[F".toByteArray())
    }

    @Test
    fun keyToBytes_pgUp_emitsCsi5Tilde() {
        assertThat(keyToBytes(ExtraKey.PgUp)).isEqualTo("\u001b[5~".toByteArray())
    }

    @Test
    fun keyToBytes_pgDn_emitsCsi6Tilde() {
        assertThat(keyToBytes(ExtraKey.PgDn)).isEqualTo("\u001b[6~".toByteArray())
    }

    // endregion

    // region keyToBytes — state-toggle keys return null (no SendInput payload)

    @Test
    fun keyToBytes_ctrl_returnsNull() {
        // Ctrl is a ToggleCtrl event, not a byte-emitting key.
        assertThat(keyToBytes(ExtraKey.Ctrl)).isNull()
    }

    @Test
    fun keyToBytes_alt_returnsNull() {
        // Alt is a ToggleAlt event, not a byte-emitting key.
        assertThat(keyToBytes(ExtraKey.Alt)).isNull()
    }

    @Test
    fun keyToBytes_fn_returnsNull() {
        // Fn is a reserved future toggle affordance; no assigned byte.
        assertThat(keyToBytes(ExtraKey.Fn)).isNull()
    }

    // endregion

    // region AllExtraKeys — inventory invariants

    @Test
    fun allExtraKeys_containsAllSeventeenEntries() {
        // The plan pins the key count at exactly 17 — a regression
        // here (e.g. forgetting to register a new ExtraKey subtype
        // in the display list) would leave that key unreachable.
        assertThat(AllExtraKeys).hasSize(17)
    }

    @Test
    fun allExtraKeys_isInPlanOrder() {
        assertThat(AllExtraKeys).containsExactly(
            ExtraKey.Esc,
            ExtraKey.Tab,
            ExtraKey.Ctrl,
            ExtraKey.Alt,
            ExtraKey.ArrowUp,
            ExtraKey.ArrowDown,
            ExtraKey.ArrowLeft,
            ExtraKey.ArrowRight,
            ExtraKey.Fn,
            ExtraKey.Pipe,
            ExtraKey.Slash,
            ExtraKey.Tilde,
            ExtraKey.Backtick,
            ExtraKey.Home,
            ExtraKey.End,
            ExtraKey.PgUp,
            ExtraKey.PgDn,
        ).inOrder()
    }

    @Test
    fun keyToBytes_everyNonModifierKey_hasNonEmptyPayload() {
        // Every key that isn't a state toggle MUST resolve to a
        // non-empty ByteArray — an empty SendInput is a no-op and
        // would silently swallow a user tap.
        val stateToggles = setOf(ExtraKey.Ctrl, ExtraKey.Alt, ExtraKey.Fn)
        AllExtraKeys.filterNot { it in stateToggles }.forEach { key ->
            val bytes = keyToBytes(key)
            assertThat(bytes).isNotNull()
            assertThat(bytes!!.size).isGreaterThan(0)
        }
    }

    // endregion
}
