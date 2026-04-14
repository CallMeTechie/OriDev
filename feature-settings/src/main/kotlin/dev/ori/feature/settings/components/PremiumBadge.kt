package dev.ori.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.Crown
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.OriExtraShapes
import dev.ori.core.ui.theme.OriTypography
import dev.ori.core.ui.theme.PremiumGold

/**
 * Compact "Premium" pill chip — 11 sp / 600 weight uppercase label with the
 * Lucide [Crown] icon, Premium Gold background, white text. Used as a row
 * accessory for Premium-only settings (e.g. "Biometric Unlock — PREMIUM").
 *
 * Phase 11 P1.2 ships a placeholder "Premium — bald verfügbar" row in the
 * Account section; the actual paywall + Play Billing integration is deferred
 * to future Phase 12 (Monetarisierung) per plan v6 §3 item 3.
 */
@Composable
public fun PremiumBadge(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(OriExtraShapes.pill)
            .background(PremiumGold)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = LucideIcons.Crown,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = Color.White,
        )
        Text(
            text = "PREMIUM",
            style = OriTypography.overline,
            color = Color.White,
        )
    }
}
