package dev.ori.feature.premium.ui

import androidx.compose.runtime.Composable
import dev.ori.domain.model.PremiumFeatureKey

@Composable
fun PremiumGate(
    featureKey: PremiumFeatureKey,
    isPremium: Boolean,
    onUpgradeTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isPremium) {
        content()
    } else {
        PremiumUpsellCard(featureKey = featureKey, onUpgradeTap = onUpgradeTap)
    }
}
