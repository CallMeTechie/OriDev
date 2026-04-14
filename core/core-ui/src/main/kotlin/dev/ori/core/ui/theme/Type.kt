package dev.ori.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.ori.core.fonts.OriFonts

/**
 * Material 3 typography scale aligned with the Phase 11 mockups.
 *
 * Sizes are taken directly from the HTML mockups under `Mockups/`. The font
 * family is **always** [OriFonts.Inter] for phone surfaces — never the
 * platform default (Roboto). Custom slots that don't fit the M3 scale (terminal
 * body, code editor body, monospaced host fields, Wear logo / labels) live on
 * the [OriTypography] object below and are accessed directly, not via
 * `MaterialTheme.typography.*`.
 *
 * **Why no `color = ...` here:** v0.x set `color = Gray700` on each style which
 * forced the text color globally — and was the reason `surface = Gray50` was a
 * latent bug (text against the wrong background). Colors now flow from
 * `MaterialTheme.colorScheme.onSurface` / `onBackground` via the M3 default
 * `LocalContentColor`, so swapping `surface` to white in PR 2 Commit 0 actually
 * propagates to text contrast as expected.
 */
val OriDevTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 48.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.035).em,
        lineHeight = 1.1.em,
    ),
    displayMedium = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 32.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.030).em,
        lineHeight = 1.15.em,
    ),
    displaySmall = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 26.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.025).em,
        lineHeight = 1.2.em,
    ),
    headlineLarge = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.025).em,
        lineHeight = 1.2.em,
    ),
    headlineMedium = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 22.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.020).em,
        lineHeight = 1.25.em,
    ),
    headlineSmall = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.020).em,
        lineHeight = 1.3.em,
    ),
    titleLarge = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 18.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = (-0.015).em,
        lineHeight = 1.35.em,
    ),
    titleMedium = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 15.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = (-0.010).em,
        lineHeight = 1.4.em,
    ),
    titleSmall = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = (-0.010).em,
        lineHeight = 1.4.em,
    ),
    bodyLarge = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 15.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 1.5.em,
    ),
    bodyMedium = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 1.5.em,
    ),
    bodySmall = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 13.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 1.45.em,
    ),
    labelLarge = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 13.sp,
        fontWeight = FontWeight.W500,
        lineHeight = 1.3.em,
    ),
    labelMedium = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 12.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.01.em,
        lineHeight = 1.3.em,
    ),
    labelSmall = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.05.em,
        lineHeight = 1.25.em,
    ),
)

/**
 * Custom typography slots that do not fit Material 3's `Typography` shape.
 * Accessed directly: `OriTypography.terminalBody`, `OriTypography.hostMono`, …
 *
 * - `overline` — Phase 11 uses this for `OriSectionLabel` ("FEATURE SCREENS",
 *   "RECENT", "ALL SERVERS"). M3 doesn't expose `overline` on its `Typography`
 *   shape (it was removed when M3 launched), so it lives here.
 * - `terminalBody` — JetBrains Mono 12 sp / 1.6 line-height for the SSH
 *   terminal output (`feature-terminal`, P2.1).
 * - `editorBody` — JetBrains Mono 13 sp with line height 20 px / 13 sp ≈ 1.54
 *   matching `code-editor.html` (`feature-editor`, P2.2).
 * - `hostMono` — JetBrains Mono 12 sp with tight letter-spacing for the host
 *   field on connection cards (`feature-connections`, P2.3).
 * - `wearLogo` / `wearCompact` / `wearLabel` / `wearTerminalTiny` — Wear-only
 *   variants that use **Roboto Flex** (not Inter) per the locked-in
 *   decision in plan v6 §3 item 2; Inter at 8–12 sp on round OLED displays
 *   is known unreadable. Only `wearTerminalTiny` uses JetBrains Mono because
 *   the Wear `CommandOutputScreen` mockup explicitly shows monospaced output.
 */
object OriTypography {

    val overline: TextStyle = TextStyle(
        fontFamily = OriFonts.Inter,
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.08.em,
        lineHeight = 1.2.em,
    )

    val terminalBody: TextStyle = TextStyle(
        fontFamily = OriFonts.JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 1.6.em,
    )

    val editorBody: TextStyle = TextStyle(
        fontFamily = OriFonts.JetBrainsMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.W400,
        lineHeight = (20f / 13f).em,
    )

    val hostMono: TextStyle = TextStyle(
        fontFamily = OriFonts.JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.W400,
        letterSpacing = (-0.01).em,
    )

    // ---- Wear OS only (used by :wear's OriDevWearTheme in P3.1) -------------

    val wearLogo: TextStyle = TextStyle(
        fontFamily = OriFonts.RobotoFlex,
        fontSize = 38.sp,
        fontWeight = FontWeight.W900,
    )

    val wearCompact: TextStyle = TextStyle(
        fontFamily = OriFonts.RobotoFlex,
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.2.em,
    )

    val wearLabel: TextStyle = TextStyle(
        fontFamily = OriFonts.RobotoFlex,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
    )

    val wearTerminalTiny: TextStyle = TextStyle(
        fontFamily = OriFonts.JetBrainsMono,
        fontSize = 9.sp,
        fontWeight = FontWeight.W400,
    )
}
