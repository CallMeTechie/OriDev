package dev.ori.core.security.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BiometricGateImpl].
 *
 * `isAvailable()` is tested by mocking [BiometricManager]'s static
 * factory. The `authenticate()` path is tested via
 * [FakeBiometricPromptLauncher] — a synchronous stub for
 * [BiometricPromptLauncher] that returns a canned [AuthenticationOutcome]
 * without constructing a real [androidx.biometric.BiometricPrompt]. This
 * exercises the gate's error-mapping logic end-to-end on the JVM without
 * Robolectric or instrumentation.
 */
class BiometricGateImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val biometricManager = mockk<BiometricManager>()
    private val activity = mockk<FragmentActivity>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns biometricManager
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(BiometricManager::class)
    }

    // ---- isAvailable() ----

    @Test
    fun isAvailable_whenBiometricManagerReturnsSuccess_returnsTrue() = runTest {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS
        val gate = BiometricGateImpl(context, FakeBiometricPromptLauncher(AuthenticationOutcome.Succeeded))

        val result = gate.isAvailable()

        assertThat(result).isTrue()
    }

    @Test
    fun isAvailable_whenNoHardware_returnsFalse() = runTest {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        val gate = BiometricGateImpl(context, FakeBiometricPromptLauncher(AuthenticationOutcome.Succeeded))

        val result = gate.isAvailable()

        assertThat(result).isFalse()
    }

    @Test
    fun isAvailable_whenNoneEnrolled_returnsFalse() = runTest {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        val gate = BiometricGateImpl(context, FakeBiometricPromptLauncher(AuthenticationOutcome.Succeeded))

        val result = gate.isAvailable()

        assertThat(result).isFalse()
    }

    // ---- authenticate() ----

    @Test
    fun authenticate_whenLauncherSucceeds_returnsSuccessAndLaunchesOnce() = runTest {
        val launcher = FakeBiometricPromptLauncher(AuthenticationOutcome.Succeeded)
        val gate = BiometricGateImpl(context, launcher)

        val result = gate.authenticate(
            activity = activity,
            title = "Unlock",
            subtitle = "Please verify",
            description = "Required to read credentials",
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(launcher.launchedCount).isEqualTo(1)
        assertThat(launcher.lastActivity).isSameInstanceAs(activity)
        assertThat(launcher.lastTitle).isEqualTo("Unlock")
        assertThat(launcher.lastSubtitle).isEqualTo("Please verify")
        assertThat(launcher.lastDescription).isEqualTo("Required to read credentials")
    }

    @Test
    fun authenticate_whenUserCancelled_returnsUserCancelledFailure() = runTest {
        val launcher = FakeBiometricPromptLauncher(
            AuthenticationOutcome.Error(BiometricPrompt.ERROR_USER_CANCELED, "cancelled"),
        )
        val gate = BiometricGateImpl(context, launcher)

        val result = gate.authenticate(activity = activity, title = "Unlock")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(BiometricError.UserCancelled::class.java)
    }

    @Test
    fun authenticate_whenNegativeButtonPressed_returnsUserCancelledFailure() = runTest {
        val launcher = FakeBiometricPromptLauncher(
            AuthenticationOutcome.Error(BiometricPrompt.ERROR_NEGATIVE_BUTTON, "abbrechen"),
        )
        val gate = BiometricGateImpl(context, launcher)

        val result = gate.authenticate(activity = activity, title = "Unlock")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(BiometricError.UserCancelled::class.java)
    }

    @Test
    fun authenticate_whenLockout_returnsLockedOutFailure() = runTest {
        val launcher = FakeBiometricPromptLauncher(
            AuthenticationOutcome.Error(BiometricPrompt.ERROR_LOCKOUT, "too many attempts"),
        )
        val gate = BiometricGateImpl(context, launcher)

        val result = gate.authenticate(activity = activity, title = "Unlock")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(BiometricError.LockedOut::class.java)
    }

    @Test
    fun authenticate_whenLockoutPermanent_returnsLockedOutFailure() = runTest {
        val launcher = FakeBiometricPromptLauncher(
            AuthenticationOutcome.Error(BiometricPrompt.ERROR_LOCKOUT_PERMANENT, "permanent lockout"),
        )
        val gate = BiometricGateImpl(context, launcher)

        val result = gate.authenticate(activity = activity, title = "Unlock")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(BiometricError.LockedOut::class.java)
    }

    @Test
    fun authenticate_whenNoBiometricsEnrolled_returnsNotAvailableFailure() = runTest {
        val launcher = FakeBiometricPromptLauncher(
            AuthenticationOutcome.Error(BiometricPrompt.ERROR_NO_BIOMETRICS, "none enrolled"),
        )
        val gate = BiometricGateImpl(context, launcher)

        val result = gate.authenticate(activity = activity, title = "Unlock")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(BiometricError.NotAvailable::class.java)
    }

    @Test
    fun authenticate_whenHardwareUnavailable_returnsNotAvailableFailure() = runTest {
        val launcher = FakeBiometricPromptLauncher(
            AuthenticationOutcome.Error(BiometricPrompt.ERROR_HW_UNAVAILABLE, "hw unavailable"),
        )
        val gate = BiometricGateImpl(context, launcher)

        val result = gate.authenticate(activity = activity, title = "Unlock")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(BiometricError.NotAvailable::class.java)
    }

    @Test
    fun authenticate_whenUnknownErrorCode_returnsUnknownWithCodeAndMessage() = runTest {
        val launcher = FakeBiometricPromptLauncher(
            AuthenticationOutcome.Error(99999, "some weird framework error"),
        )
        val gate = BiometricGateImpl(context, launcher)

        val result = gate.authenticate(activity = activity, title = "Unlock")

        assertThat(result.isFailure).isTrue()
        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(BiometricError.Unknown::class.java)
        val unknown = error as BiometricError.Unknown
        assertThat(unknown.code).isEqualTo(99999)
        assertThat(unknown.message).isEqualTo("some weird framework error")
    }

    /**
     * Synchronous stub for [BiometricPromptLauncher] — returns a canned
     * [AuthenticationOutcome] and records the arguments it was called
     * with. No real [androidx.biometric.BiometricPrompt] is constructed.
     */
    private class FakeBiometricPromptLauncher(
        private val outcome: AuthenticationOutcome,
    ) : BiometricPromptLauncher {
        var launchedCount: Int = 0
            private set
        var lastActivity: FragmentActivity? = null
            private set
        var lastTitle: String? = null
            private set
        var lastSubtitle: String? = null
            private set
        var lastDescription: String? = null
            private set

        override suspend fun launch(
            activity: FragmentActivity,
            title: String,
            subtitle: String?,
            description: String?,
        ): AuthenticationOutcome {
            launchedCount++
            lastActivity = activity
            lastTitle = title
            lastSubtitle = subtitle
            lastDescription = description
            return outcome
        }
    }
}
