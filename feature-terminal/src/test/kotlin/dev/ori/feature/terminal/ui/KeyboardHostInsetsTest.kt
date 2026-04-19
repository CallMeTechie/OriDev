package dev.ori.feature.terminal.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Phase 15 Task 15.2 — source-level smoke test that locks in the
 * layout invariant for [KeyboardHost].
 *
 * ## Why a source-string test?
 *
 * The four user-reported bugs in Task 15.2 (huge gap above IME,
 * grey strip, no live re-flow on Gboard resize, ExtraKeys stuck
 * after IME dismiss) all have the same root cause: `imePadding()`
 * applied INSIDE [KeyboardHost] instead of at the
 * [TerminalScreen] root. A future refactor could reintroduce
 * `imePadding()` to this file without any unit test noticing —
 * the Compose preview would still render, and
 * [TerminalImeAnchorTest] only exercises the committed-delta
 * pure helpers.
 *
 * This test snapshots the one-line invariant ("no `.imePadding()`
 * call in KeyboardHost.kt") and also asserts that the HYBRID
 * branch gates its extra-keys row on `WindowInsets.isImeVisible`
 * so the row vanishes when the user dismisses Gboard via the
 * back-gesture.
 *
 * Running this as a plain JVM JUnit-5 test keeps the smoke cheap
 * (no Robolectric, no Compose runtime) — the existing
 * `KeyboardHostHybridPreview` inside the file plus the full
 * `:app:assembleDebug` CI job cover the "does it compose?"
 * question.
 */
class KeyboardHostInsetsTest {

    private val source: String by lazy {
        val candidates = listOf(
            "src/main/kotlin/dev/ori/feature/terminal/ui/KeyboardHost.kt",
            "feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/KeyboardHost.kt",
        )
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error(
                "KeyboardHost.kt not found on any expected path. " +
                    "Tried: $candidates from ${File(".").absolutePath}",
            )
        file.readText()
    }

    /**
     * Source with comments stripped so keyword checks can't trip on
     * phrases inside KDoc or `//` lines (e.g. "don't apply imePadding
     * here" in a Task-15.2 breadcrumb comment).
     */
    private val sourceWithoutComments: String by lazy {
        // Strip block comments /* ... */ first (lazy, so nested
        // occurrences are safe) then strip // line comments.
        val blockStripped = source.replace(Regex("""/\*[\s\S]*?\*/"""), "")
        blockStripped.lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
    }

    // region Layout-invariant smoke tests

    @Test
    fun keyboardHost_doesNotApplyImePadding_anywhere() {
        // Invariant (Phase 15 Task 15.2): imePadding() belongs on the
        // TerminalScreen root, NOT on the KeyboardHost's subtree. If
        // you're adding it back to this file to fix a layout bug, the
        // fix is almost certainly wrong — see commit history.
        assertThat(sourceWithoutComments).doesNotContain(".imePadding()")
        assertThat(sourceWithoutComments).doesNotContain("imePadding(")
    }

    @Test
    fun keyboardHost_doesNotImportImePadding() {
        // Belt-and-suspender: catch attempts to re-import without using,
        // which would signal a half-reverted Task 15.2.
        assertThat(sourceWithoutComments)
            .doesNotContain("import androidx.compose.foundation.layout.imePadding")
    }

    @Test
    fun keyboardHost_hybridBranch_gatesExtraKeysOnIsImeVisible() {
        // The HYBRID branch must skip rendering TerminalExtraKeys when
        // the system IME is dismissed — otherwise the row remains
        // floating above empty space after the user hits the IME's
        // back-gesture (bug #4 in Task 15.2).
        assertThat(sourceWithoutComments).contains("WindowInsets.isImeVisible")
        assertThat(sourceWithoutComments)
            .contains("import androidx.compose.foundation.layout.isImeVisible")
    }

    @Test
    fun keyboardHost_optsIntoExperimentalLayoutApi() {
        // isImeVisible is @ExperimentalLayoutApi. The opt-in must be
        // scoped to this composable (not suppressed project-wide).
        assertThat(sourceWithoutComments).contains("@OptIn(ExperimentalLayoutApi::class)")
    }

    // endregion

    // region CUSTOM branch untouched

    @Test
    fun keyboardHost_customBranch_stillForwardsToCustomKeyboard() {
        // CUSTOM must keep passing the parent's modifier (including
        // the weight(1f - splitRatio) from TerminalKeyboardHostSlot)
        // through unchanged — Task 15.3 will revisit CustomKeyboard,
        // but Task 15.2 must not regress the weight routing.
        assertThat(sourceWithoutComments).contains("KeyboardMode.CUSTOM ->")
        assertThat(sourceWithoutComments).contains("CustomKeyboard(")
        // Guard: the CUSTOM block must reference the parameter-level
        // `modifier = modifier` pass-through, not a fresh Modifier.
        val customBlock = sourceWithoutComments.substringAfter("KeyboardMode.CUSTOM ->")
            .substringBefore("KeyboardMode.HYBRID ->")
        assertThat(customBlock).contains("modifier = modifier")
    }

    // endregion

    // region SYSTEM_ONLY branch normalised

    @Test
    fun keyboardHost_systemOnlyBranch_doesNotAddFillMaxWidth() {
        // SYSTEM_ONLY now passes the parent modifier through as-is;
        // the previous .fillMaxWidth().imePadding() stack is gone.
        val systemOnlyBlock = sourceWithoutComments
            .substringAfter("KeyboardMode.SYSTEM_ONLY ->")
            .substringBefore("\n        }\n    }")
        // The anchor call inside SYSTEM_ONLY should pass `modifier = modifier`
        // directly, without chained layout modifiers.
        assertThat(systemOnlyBlock).contains("modifier = modifier")
        assertThat(systemOnlyBlock).doesNotContain(".fillMaxWidth()")
    }

    // endregion
}
