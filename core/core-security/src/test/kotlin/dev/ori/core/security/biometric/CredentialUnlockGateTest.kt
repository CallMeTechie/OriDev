package dev.ori.core.security.biometric

import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CredentialUnlockGate] — the Phase 11 Tier-1 T1d wire-up
 * that translates the `biometricUnlock` preference into a BiometricPrompt.
 *
 * Uses a hand-rolled [BiometricGate] fake rather than MockK because
 * Kotlin's `Result<T>` is a value class and MockK's stub boxing mangles
 * the wrapped exception — specifically, returning `Result.failure(...)`
 * from a `coEvery { } returns` stub leaks out as a "success" value in the
 * caller. A tiny explicit fake avoids the whole class of problems and
 * still verifies invocation count via a counter field.
 *
 * Similarly, [BiometricPreferenceSource] is implemented as a tiny fake
 * (one `Flow<Boolean>` field) so the test doesn't need
 * [dev.ori.feature.settings.data.AppPreferences], which would require
 * DataStore/TempDir scaffolding and a cross-module dependency that
 * doesn't exist (and can't exist, because :core:core-security is a
 * downstream of :feature-settings).
 */
class CredentialUnlockGateTest {

    private val activity = mockk<FragmentActivity>(relaxed = true)

    private fun unlockGate(
        enabled: Boolean,
        biometricGate: BiometricGate,
    ): CredentialUnlockGate = CredentialUnlockGate(
        biometricGate = biometricGate,
        preferenceSource = FakePreferenceSource(enabled),
    )

    @Test
    fun requireUnlock_disabledInPrefs_returnsSuccessWithoutPrompt() = runTest {
        val fakeGate = FakeBiometricGate()

        val result = unlockGate(enabled = false, biometricGate = fakeGate)
            .requireUnlock(activity)

        assertThat(result.isSuccess).isTrue()
        assertThat(fakeGate.authenticateCalls).isEqualTo(0)
        assertThat(fakeGate.isAvailableCalls).isEqualTo(0)
    }

    @Test
    fun requireUnlock_enabled_availableHardware_delegatesToGate() = runTest {
        val fakeGate = FakeBiometricGate(
            available = true,
            authResult = Result.success(Unit),
        )

        val result = unlockGate(enabled = true, biometricGate = fakeGate)
            .requireUnlock(activity, title = "T", subtitle = "S")

        assertThat(result.isSuccess).isTrue()
        assertThat(fakeGate.authenticateCalls).isEqualTo(1)
        assertThat(fakeGate.lastTitle).isEqualTo("T")
        assertThat(fakeGate.lastSubtitle).isEqualTo("S")
    }

    @Test
    fun requireUnlock_enabled_noHardware_failsOpenReturnsSuccess() = runTest {
        val fakeGate = FakeBiometricGate(available = false)

        val result = unlockGate(enabled = true, biometricGate = fakeGate)
            .requireUnlock(activity)

        assertThat(result.isSuccess).isTrue()
        assertThat(fakeGate.authenticateCalls).isEqualTo(0)
    }

    @Test
    fun requireUnlock_enabled_authenticationFails_returnsFailure() = runTest {
        val fakeGate = FakeBiometricGate(
            available = true,
            authResult = Result.failure(BiometricError.UserCancelled),
        )

        val result = unlockGate(enabled = true, biometricGate = fakeGate)
            .requireUnlock(activity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .isInstanceOf(BiometricError.UserCancelled::class.java)
        assertThat(fakeGate.authenticateCalls).isEqualTo(1)
    }

    private class FakePreferenceSource(enabled: Boolean) : BiometricPreferenceSource {
        override val enabled: Flow<Boolean> = flowOf(enabled)
    }

    private class FakeBiometricGate(
        private val available: Boolean = true,
        private val authResult: Result<Unit> = Result.success(Unit),
    ) : BiometricGate {
        var isAvailableCalls: Int = 0
            private set
        var authenticateCalls: Int = 0
            private set
        var lastTitle: String? = null
            private set
        var lastSubtitle: String? = null
            private set

        override suspend fun isAvailable(): Boolean {
            isAvailableCalls++
            return available
        }

        override suspend fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String?,
            description: String?,
        ): Result<Unit> {
            authenticateCalls++
            lastTitle = title
            lastSubtitle = subtitle
            return authResult
        }
    }
}
