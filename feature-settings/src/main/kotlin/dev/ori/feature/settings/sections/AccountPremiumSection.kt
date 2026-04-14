package dev.ori.feature.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ori.core.ui.icons.lucide.Crown
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.User
import dev.ori.core.ui.theme.Gray500
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow

/**
 * Account & Premium section — Phase 11 P1.2.
 *
 * **Premium ist ein Placeholder.** Das Plan-v6 §3-Item-3-Lock-In sagt: full
 * Premium UI (Paywall, Pricing-Tiles, Play-Billing-Integration) wird erst in
 * Phase 12 (Monetarisierung) implementiert. P1.2 zeigt nur eine
 * "Bald verfügbar"-Zeile mit dem Crown-Icon, ohne onClick.
 */
@Composable
internal fun AccountPremiumSection() {
    SettingsCard(sectionLabel = "Konto & Premium") {
        SettingsRow(
            icon = LucideIcons.User,
            title = "Profil",
            subtitle = "Lokales Profil — keine Cloud-Synchronisation",
        )
        SettingsRow(
            icon = LucideIcons.Crown,
            title = "Premium",
            subtitle = "Bald verfügbar",
            trailing = {
                Text(
                    text = "—",
                    color = Gray500,
                )
            },
        )
    }
}
