package dev.ori.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.domain.repository.PremiumRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CheckPremiumUseCaseTest {

    private val premiumFlow = MutableStateFlow(false)
    private val repo = mockk<PremiumRepository> { every { isPremium } returns premiumFlow }
    private val useCase = CheckPremiumUseCase(repo)

    @Test
    fun invoke_flowEmitsRepoValue() = runTest {
        useCase().test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun invoke_premiumChanges_flowUpdates() = runTest {
        useCase().test {
            assertThat(awaitItem()).isFalse()
            premiumFlow.value = true
            assertThat(awaitItem()).isTrue()
        }
    }
}
