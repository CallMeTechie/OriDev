package dev.ori.feature.settings.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.Check
import dev.ori.core.ui.icons.lucide.Crown
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.User
import dev.ori.core.ui.theme.Indigo500
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow
import dev.ori.feature.settings.ui.PremiumStatus

/**
 * Account & Premium section — Phase 11 P1.2, updated in Phase 13 for live
 * premium status via [CheckPremiumUseCase].
 *
 * - [PremiumStatus.Free]: shows "Jetzt upgraden" and navigates to paywall on tap.
 * - [PremiumStatus.Premium]: shows "Aktiv" with a checkmark icon.
 */
@Composable
internal fun AccountPremiumSection(
    premiumStatus: PremiumStatus = PremiumStatus.Free,
    onNavigateToPaywall: () -> Unit = {},
) {
    SettingsCard(sectionLabel = "Konto & Premium") {
        SettingsRow(
            icon = LucideIcons.User,
            title = "Profil",
            subtitle = "Lokales Profil — keine Cloud-Synchronisation",
        )
        when (premiumStatus) {
            PremiumStatus.Free -> SettingsRow(
                icon = LucideIcons.Crown,
                title = "Premium",
                subtitle = "Jetzt upgraden",
                onClick = onNavigateToPaywall,
            )
            PremiumStatus.Premium -> SettingsRow(
                icon = LucideIcons.Crown,
                title = "Premium",
                subtitle = "Aktiv",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = LucideIcons.Check,
                            contentDescription = "Premium aktiv",
                            tint = Indigo500,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Aktiv",
                            color = Indigo500,
                        )
                    }
                },
            )
        }
    }
}
