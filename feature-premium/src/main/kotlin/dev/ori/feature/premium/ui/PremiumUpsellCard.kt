package dev.ori.feature.premium.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.icons.lucide.Crown
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.PremiumGold
import dev.ori.domain.model.PremiumFeatureKey

@Composable
fun PremiumUpsellCard(
    featureKey: PremiumFeatureKey,
    onUpgradeTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OriCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, PremiumGold),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = LucideIcons.Crown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = PremiumGold,
                )
                Text(
                    text = "Mit Premium freischalten",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = descriptionForFeature(featureKey),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onUpgradeTap,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Indigo500,
                    contentColor = Color.White,
                ),
            ) {
                Text(text = "Upgrade auf Premium")
            }
        }
    }
}

private fun descriptionForFeature(key: PremiumFeatureKey): String = when (key) {
    PremiumFeatureKey.BANDWIDTH_THROTTLE ->
        "Passe die Bandbreite deiner Transfers individuell an, um andere Dienste nicht zu beeinträchtigen."
    PremiumFeatureKey.CHUNKED_TRANSFER ->
        "Übertrage große Dateien in Chunks für stabilere und fortsetzbare Uploads."
}
