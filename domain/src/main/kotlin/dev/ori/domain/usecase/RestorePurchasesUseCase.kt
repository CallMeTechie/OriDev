package dev.ori.domain.usecase

import dev.ori.domain.repository.PremiumRepository
import javax.inject.Inject

class RestorePurchasesUseCase @Inject constructor(
    private val premiumRepository: PremiumRepository,
) {
    suspend operator fun invoke() {
        premiumRepository.refreshEntitlement()
    }
}
