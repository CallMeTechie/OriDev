package dev.ori.domain.usecase

import dev.ori.domain.repository.PremiumRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PurchaseUseCaseTest {

    private val repo = mockk<PremiumRepository>(relaxed = true)
    private val purchaseUseCase = PurchaseUseCase(repo)
    private val restoreUseCase = RestorePurchasesUseCase(repo)

    @Test
    fun invoke_purchaseSucceeded_cachesTrue() = runTest {
        purchaseUseCase(purchaseSucceeded = true)
        coVerify { repo.cacheEntitlement(true) }
    }

    @Test
    fun invoke_purchaseFailed_doesNotCache() = runTest {
        purchaseUseCase(purchaseSucceeded = false)
        coVerify(exactly = 0) { repo.cacheEntitlement(any()) }
    }

    @Test
    fun restorePurchases_callsRefresh() = runTest {
        restoreUseCase()
        coVerify { repo.refreshEntitlement() }
    }
}
