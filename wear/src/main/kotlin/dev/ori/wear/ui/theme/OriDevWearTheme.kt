package dev.ori.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Ori:Dev Wear OS theme.
 *
 * Phase 11 P3.1 — replaces the default Wear M3 theme pass-through with an
 * OLED-first palette ([oriDevWearColorScheme]) and a Roboto Flex typography
 * scale ([oriDevWearTypography]), both extracted from `Mockups/watch.html`.
 *
 * Wear M3 intentionally has no light/dark toggle — OLED watches are always
 * dark for battery reasons — so this theme is a single scheme.
 */
@Composable
fun OriDevWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = oriDevWearColorScheme(),
        typography = oriDevWearTypography(),
        content = content,
    )
}
