package dev.ori.domain.usecase

import dev.ori.domain.repository.PremiumRepository
import javax.inject.Inject

class PurchaseUseCase @Inject constructor(
    private val premiumRepository: PremiumRepository,
) {
    suspend operator fun invoke(purchaseSucceeded: Boolean) {
        if (purchaseSucceeded) {
            premiumRepository.cacheEntitlement(true)
        }
    }
}
