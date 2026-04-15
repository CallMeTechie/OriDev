package dev.ori.core.security.biometric

import androidx.fragment.app.FragmentActivity

/**
 * Thin abstraction over [androidx.biometric.BiometricPrompt] so the rest of
 * the codebase can require biometric authentication without pulling in the
 * Android framework directly (and so it can be mocked in tests).
 *
 * The gate is hardwired to `BIOMETRIC_STRONG` (class 3) — fingerprint or
 * face-ID with liveness detection — so compromised class-2 sensors cannot
 * bypass credential reads.
 */
interface BiometricGate {

    /** True if the device has biometric hardware present AND enrolled. */
    suspend fun isAvailable(): Boolean

    /**
     * Prompts the user for biometric confirmation. Returns [Result] where
     * success carries no value (the caller already knows what it is
     * unlocking) and failure carries a [BiometricError].
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        description: String? = null,
    ): Result<Unit>
}

/**
 * Typed failure surface for [BiometricGate.authenticate]. Mapped from
 * `BiometricPrompt.ERROR_*` codes in [BiometricGateImpl].
 */
sealed class BiometricError : Throwable() {
    /** Hardware missing, unavailable, or no biometrics enrolled. */
    object NotAvailable : BiometricError()

    /** User dismissed the prompt or pressed the negative button. */
    object UserCancelled : BiometricError()

    /** Too many failed attempts — sensor locked out. */
    object LockedOut : BiometricError()

    /** Any other framework error — carries the original code + message. */
    data class Unknown(val code: Int, override val message: String?) : BiometricError()
}
