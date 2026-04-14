@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.ori.core.fonts

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Static font assets shared across the Ori:Dev phone app, the Wear OS
 * companion, and any future modules that need typography.
 *
 * Lives in `:core:core-fonts` so that the Wear module can depend on font
 * resources without pulling in phone Jetpack Compose Material 3 — a transitive
 * leak that the architect review of Phase 11 plan v1 identified as the reason
 * for not letting `:wear` depend directly on `:core:core-ui`.
 *
 * **Bundled families:**
 * - **Inter** (rsms.dev/inter, OFL 1.1) — phone UI text. Five weights:
 *   400 Regular, 500 Medium, 600 SemiBold, 700 Bold, 900 Black.
 * - **JetBrains Mono** (jetbrains.com/lp/mono, Apache 2.0) — terminal output,
 *   code editor body, monospaced labels (host fields in connection cards).
 *   Three weights: 400 Regular, 500 Medium, 700 Bold.
 * - **Roboto Flex** (googlefonts/roboto-flex, Apache 2.0) — Wear OS text.
 *   Variable font keeps a single TTF for all weights via the `wght` axis, so
 *   the [Font] entries below all reference the same resource and pass a
 *   [FontVariation.weight] setting per slot.
 *
 * **Weight resolution:** Compose's [FontFamily] picks the closest declared
 * [FontWeight] for a requested [TextStyle][androidx.compose.ui.text.TextStyle]
 * weight. With three Inter slots between 400 and 600, a request for
 * `FontWeight.Light` (300) returns Regular (400). For Roboto Flex, Compose
 * additionally applies the variation axis to interpolate the variable font
 * even for non-declared weights.
 *
 * **Subsetting:** all bundled TTFs are subset to Latin-1 + Latin-1 Supplement
 * (covers German umlauts), em/en dash, smart quotes, ellipsis. JetBrains Mono
 * additionally retains the Box Drawing block (U+2500–257F) for terminal
 * rendering. Combined assets ≤ 1.5 MB enforced by `checkFontBudget`.
 *
 * **Typography styles** that use these families live in `:core:core-ui`'s
 * `Type.kt` and `OriTypography` object. Feature modules consume those styles
 * via `MaterialTheme.typography.*` (M3 scale) or `OriTypography.terminalBody`
 * etc. (custom slots).
 */
public object OriFonts {

    /** Phone UI text. Five static weights, no variable axes. */
    public val Inter: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.inter_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.inter_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.inter_black, FontWeight.Black, FontStyle.Normal),
    )

    /** Terminal output, code editor, monospaced labels. */
    public val JetBrainsMono: FontFamily = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold, FontStyle.Normal),
    )

    /**
     * Wear OS text. Single variable TTF; per-weight [Font] slots pass an
     * explicit [FontVariation.Settings] so Compose drives the variable axis.
     */
    public val RobotoFlex: FontFamily = FontFamily(
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_REGULAR)),
        ),
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight.Medium,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_MEDIUM)),
        ),
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight.Bold,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_BOLD)),
        ),
    )

    private const val WEIGHT_REGULAR = 400
    private const val WEIGHT_MEDIUM = 500
    private const val WEIGHT_BOLD = 700
}
