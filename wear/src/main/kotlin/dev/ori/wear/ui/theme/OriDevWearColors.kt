package dev.ori.wear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

/**
 * Wear OS palette for Ori:Dev, extracted from `Mockups/watch.html`.
 *
 * OLED-first: the watch mockup uses `#0F0F0F` as the screen background
 * (effectively black for AMOLED battery savings) and white foreground text.
 * Accent Indigo500 is shared with the phone palette so transfer badges,
 * status rings, and tile backgrounds feel like the same product.
 *
 * Status greens / reds match the phone palette (StatusConnected = #10B981,
 * StatusDisconnected = #EF4444) so a running VM looks the same on wrist
 * and phone.
 */
internal object OriDevWearColors {
    /** `#0F0F0F` — watch display background per Mockups/watch.html. */
    val Background = Color(0xFF0F0F0F)

    /** White — primary foreground text / high-emphasis labels. */
    val OnBackground = Color(0xFFFFFFFF)

    /** Layered surface above [Background] — used by cards and list rows. */
    val Surface = Color(0xFF1A1A1A)

    val OnSurface = Color(0xFFFFFFFF)

    /** `#888` — muted/secondary text on the watch mockup. */
    val OnSurfaceVariant = Color(0xFF888888)

    /** `#555` — tertiary/disabled text on the watch mockup. */
    val OutlineVariant = Color(0xFF555555)

    /** Ori:Dev Indigo500 accent — matches phone palette. */
    val Primary = Color(0xFF6366F1)

    val OnPrimary = Color(0xFFFFFFFF)

    /** Shared with phone: StatusConnected. Running VMs, success pings. */
    val Success = Color(0xFF10B981)

    /** Shared with phone: StatusDisconnected. Failed transfers, errors. */
    val Error = Color(0xFFEF4444)

    val OnError = Color(0xFFFFFFFF)
}

/**
 * Returns the Wear Material3 [ColorScheme] configured with [OriDevWearColors].
 * Wear M3 has a single scheme (no light/dark split — OLED is always dark).
 *
 * We start from the default [ColorScheme] and override only the slots we
 * care about via `copy` — Wear M3 carries a much larger palette than phone
 * M3 (primaryDim, tertiary, surfaceContainerLow/High, …) and letting the
 * defaults fill those keeps this file stable against library updates.
 */
internal fun oriDevWearColorScheme(): ColorScheme = ColorScheme().copy(
    background = OriDevWearColors.Background,
    onBackground = OriDevWearColors.OnBackground,
    surfaceContainerLow = OriDevWearColors.Background,
    surfaceContainer = OriDevWearColors.Surface,
    surfaceContainerHigh = OriDevWearColors.Surface,
    onSurface = OriDevWearColors.OnSurface,
    onSurfaceVariant = OriDevWearColors.OnSurfaceVariant,
    outlineVariant = OriDevWearColors.OutlineVariant,
    primary = OriDevWearColors.Primary,
    onPrimary = OriDevWearColors.OnPrimary,
    error = OriDevWearColors.Error,
    onError = OriDevWearColors.OnError,
)
