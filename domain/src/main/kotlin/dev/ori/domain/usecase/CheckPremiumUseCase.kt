package dev.ori.domain.usecase

import dev.ori.domain.repository.PremiumRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CheckPremiumUseCase @Inject constructor(
    private val premiumRepository: PremiumRepository,
) {
    operator fun invoke(): Flow<Boolean> = premiumRepository.isPremium
}
