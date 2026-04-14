package dev.ori.wear.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme

/**
 * Mockup-aligned Wear card — a flat 14 dp rounded box tinted with the
 * Wear theme's `surfaceContainer` token (≈`#1A1A1A` on OLED) and bordered
 * with the theme's `outlineVariant`. Optional [accentBorder] draws the
 * Indigo500 accent ring when the card represents a selected / running item
 * (mockup `.s1-card` active state).
 *
 * Kept intentionally small and dependency-free — uses foundation `Box` +
 * modifiers rather than Wear M3 `Card`, so it stacks inside a
 * `ScalingLazyColumn` without the Wear M3 Card chrome the mockup doesn't use.
 */
@Composable
fun OriWearCard(
    modifier: Modifier = Modifier,
    accentBorder: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor: Color = if (accentBorder) scheme.primary else scheme.outlineVariant
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(shape)
            .background(scheme.surfaceContainer)
            .border(width = 1.dp, color = borderColor, shape = shape),
        content = { content() },
    )
}
