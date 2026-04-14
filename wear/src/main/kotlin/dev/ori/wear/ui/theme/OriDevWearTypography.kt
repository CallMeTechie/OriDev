package dev.ori.wear.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Typography
import dev.ori.core.fonts.OriFonts

/**
 * Wear Material3 [Typography] backed by [OriFonts.RobotoFlex].
 *
 * Wear M3 has ~20 named styles covering display/title/label/body slots across
 * three sizes (Large/Medium/Small). Mockup `watch.html` uses three typographic
 * registers:
 *
 * 1. **Display numerals** — big stat gauges (20–32 sp, bold).
 * 2. **Labels / small caps** — 8–11 sp uppercase legends (600 weight).
 * 3. **Body** — 12–13 sp regular text for connection/VM names.
 *
 * We therefore override the relevant Wear M3 slots with Roboto Flex and leave
 * the rest of the scale to inherit the default Wear M3 sizing, which already
 * targets wrist displays.
 */
internal fun oriDevWearTypography(): Typography {
    val family = OriFonts.RobotoFlex
    val defaults = Typography()

    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displayMedium = defaults.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displaySmall = defaults.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleLarge = defaults.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleMedium = defaults.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleSmall = defaults.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        labelLarge = defaults.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        labelMedium = defaults.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
        ),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = family),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = family),
        bodySmall = defaults.bodySmall.copy(fontFamily = family),
    )
}
