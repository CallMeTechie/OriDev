package dev.ori.feature.premium.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ads.AdBannerView
import dev.ori.core.ads.AdNativeCardView
import dev.ori.domain.model.AdSlot
import dev.ori.domain.model.PremiumFeatureKey

@Composable
fun AdSlotHost(
    slot: AdSlot,
    modifier: Modifier = Modifier,
    onUpgradeTap: () -> Unit = {},
) {
    val vm: AdSlotHostViewModel = hiltViewModel(key = slot.name)
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(slot) { vm.init(slot) }

    when (val s = state) {
        AdSlotHostState.Hidden -> {}
        AdSlotHostState.Loading -> Spacer(modifier)
        is AdSlotHostState.Banner -> AdBannerView(handle = s.handle, modifier = modifier)
        is AdSlotHostState.Native -> AdNativeCardView(handle = s.handle, modifier = modifier)
        AdSlotHostState.House -> PremiumUpsellCard(
            featureKey = PremiumFeatureKey.CHUNKED_TRANSFER,
            onUpgradeTap = onUpgradeTap,
            modifier = modifier,
        )
    }
}
