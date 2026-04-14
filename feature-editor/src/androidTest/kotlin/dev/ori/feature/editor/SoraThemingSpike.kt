package dev.ori.feature.editor

import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Color as AndroidColor
import dev.ori.core.fonts.R as FontsR

/**
 * Phase 11 §P0.8 — Sora-Editor theming spike.
 *
 * Plan v6 §P0.8 lists three things this spike must prove are achievable
 * before P2.2 (code editor screen alignment) commits to the approach:
 *
 *   1. Loading [JetBrains Mono][FontsR.font.jetbrains_mono_regular] from
 *      :core:core-fonts as an Android [Typeface] and assigning it to
 *      Sora's [CodeEditor.setTypefaceText].
 *   2. Constructing a custom [EditorColorScheme] with the GitHub-style
 *      syntax palette (keyword red, string navy, comment grey, function
 *      purple, etc.) and assigning it to the editor.
 *   3. Verifying both stick — i.e. the editor reads back the typeface and
 *      colour scheme we set, not the Sora defaults.
 *
 * **Lives as an instrumented test, not a unit test:** Sora's `CodeEditor`
 * extends [android.view.View], which cannot be constructed on a JVM-only
 * unit test. Cycle 4 finding #13 corrected v5's misplacement of this file
 * under `src/test/`.
 *
 * **Stays in the codebase after the spike:** the test is not deleted after
 * the spike succeeds — it doubles as a regression guard so future Sora-Editor
 * version bumps that change the typeface/scheme APIs are caught before
 * they break P2.2's editor screen.
 */
@RunWith(AndroidJUnit4::class)
class SoraThemingSpike {

    /**
     * Sora's [CodeEditor] extends [android.view.View] and instantiates an
     * Android [android.os.Handler] in its constructor — that requires the
     * thread to have a [android.os.Looper]. The instrumentation thread does
     * not. We force every test body onto the main thread (which is the only
     * thread guaranteed to have a Looper in an Android app process) via
     * [androidx.test.platform.app.Instrumentation.runOnMainSync].
     */
    private fun runOnMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    @Test
    fun sora_editor_accepts_jetbrains_mono_typeface() {
        runOnMain {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val typeface: Typeface = requireNotNull(
                ResourcesCompat.getFont(ctx, FontsR.font.jetbrains_mono_regular),
            ) { "JetBrains Mono Regular not found in :core:core-fonts" }

            val editor = CodeEditor(ctx)
            editor.typefaceText = typeface

            // Sora exposes the typeface back via the same property — assert
            // the editor really stored what we passed.
            assertThat(editor.typefaceText).isEqualTo(typeface)
        }
    }

    @Test
    fun sora_editor_accepts_github_palette_color_scheme() {
        runOnMain {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val editor = CodeEditor(ctx)

            val scheme = EditorColorScheme()
            scheme.setColor(EditorColorScheme.KEYWORD, AndroidColor.parseColor("#CF222E"))
            scheme.setColor(EditorColorScheme.LITERAL, AndroidColor.parseColor("#0A3069"))
            scheme.setColor(EditorColorScheme.COMMENT, AndroidColor.parseColor("#6E7781"))
            scheme.setColor(EditorColorScheme.FUNCTION_NAME, AndroidColor.parseColor("#8250DF"))
            scheme.setColor(EditorColorScheme.IDENTIFIER_VAR, AndroidColor.parseColor("#0550AE"))
            scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, AndroidColor.WHITE)
            scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, AndroidColor.WHITE)

            editor.colorScheme = scheme

            // Verify the editor really stored our scheme (not Sora's default).
            assertThat(editor.colorScheme).isSameInstanceAs(scheme)

            // And that the colours are readable back unmodified.
            assertThat(editor.colorScheme.getColor(EditorColorScheme.KEYWORD))
                .isEqualTo(AndroidColor.parseColor("#CF222E"))
            assertThat(editor.colorScheme.getColor(EditorColorScheme.COMMENT))
                .isEqualTo(AndroidColor.parseColor("#6E7781"))
        }
    }

    @Test
    fun sora_editor_accepts_text_size_in_sp() {
        runOnMain {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val editor = CodeEditor(ctx)

            // Plan v6 §P2.2 specifies font size = 13 sp / line height 20 px / 13 sp
            // ≈ 1.54 em for the editor body. setTextSize is in sp.
            editor.setTextSize(13f)
            // Sora doesn't expose textSize back via a direct getter, but if the
            // setter accepts the value without throwing, the API contract holds.
            // We additionally exercise setText to ensure the editor renders.
            editor.setText("val example = \"Hello, Editor\"")
            assertThat(editor.text.toString()).isEqualTo("val example = \"Hello, Editor\"")
        }
    }
}
