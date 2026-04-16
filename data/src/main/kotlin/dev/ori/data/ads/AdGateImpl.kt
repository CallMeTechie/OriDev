package dev.ori.data.ads

import dev.ori.domain.model.AdRules
import dev.ori.domain.model.AdSlot
import dev.ori.domain.repository.AdGate
import dev.ori.domain.repository.PremiumRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdGateImpl @Inject constructor(
    private val premiumRepo: PremiumRepository,
    private val adPrefs: AdPreferences,
    private val rules: AdRules,
) : AdGate {

    override suspend fun shouldShow(slot: AdSlot): Boolean {
        if (premiumRepo.getCachedEntitlement()) return false
        return when (slot) {
            AdSlot.SETTINGS_HOUSE_UPSELL ->
                adPrefs.msSinceDismissal(slot) >= rules.houseAdDismissedForMs ||
                    !adPrefs.isDismissed(slot)
            else -> true
        }
    }

    override suspend fun recordShown(slot: AdSlot) {
        adPrefs.recordShown(slot)
    }

    override suspend fun recordDismissed(slot: AdSlot) {
        adPrefs.recordDismissed(slot)
    }
}
