package dev.ori.core.security.biometric

import android.content.Context
import androidx.biometric.BiometricManager
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
 * Only [BiometricGateImpl.isAvailable] is exercised — the `authenticate()`
 * path requires a live [androidx.fragment.app.FragmentActivity] and the
 * platform biometric executor, which is instrumentation-test territory.
 */
class BiometricGateImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val biometricManager = mockk<BiometricManager>()
    private lateinit var gate: BiometricGateImpl

    @BeforeEach
    fun setUp() {
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns biometricManager
        gate = BiometricGateImpl(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(BiometricManager::class)
    }

    @Test
    fun isAvailable_whenBiometricManagerReturnsSuccess_returnsTrue() = runTest {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS

        val result = gate.isAvailable()

        assertThat(result).isTrue()
    }

    @Test
    fun isAvailable_whenNoHardware_returnsFalse() = runTest {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        val result = gate.isAvailable()

        assertThat(result).isFalse()
    }

    @Test
    fun isAvailable_whenNoneEnrolled_returnsFalse() = runTest {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        val result = gate.isAvailable()

        assertThat(result).isFalse()
    }
}
