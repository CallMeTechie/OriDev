package dev.ori.core.security.biometric

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier-1 wire-up for the [BiometricGate] primitive (PR #86). Screens call
 * [requireUnlock] before fetching credentials from any repository. If the
 * user has the "biometric unlock" preference disabled (see
 * [BiometricPreferenceSource]), the gate is a no-op and returns immediately.
 * If it's enabled, a BiometricPrompt is shown and the result is propagated
 * to the caller.
 *
 * Fail-open policy: if the user enabled biometric unlock in settings but
 * the device reports no biometric hardware / enrollment at runtime, the
 * gate returns [Result.success] rather than locking the user out of their
 * own credentials. The alternative — hard-fail — would strand users who
 * toggled the preference on a device with a sensor, then restored onto a
 * device without one. The settings UI should visibly reflect the runtime
 * availability so users know when the toggle is cosmetic.
 *
 * This helper is deliberately stateless and idempotent — every call reads
 * the current preference snapshot via [BiometricPreferenceSource.enabled].
 */
@Singleton
class CredentialUnlockGate @Inject constructor(
    private val biometricGate: BiometricGate,
    private val preferenceSource: BiometricPreferenceSource,
) {
    /**
     * Returns [Result.success] if the user doesn't require biometric unlock
     * OR if they pass the prompt OR if biometric hardware is unavailable.
     * Returns [Result.failure] with a [BiometricError] if the prompt is
     * shown and the user cancels / fails it.
     */
    suspend fun requireUnlock(
        activity: FragmentActivity,
        title: String = DEFAULT_TITLE,
        subtitle: String? = null,
    ): Result<Unit> {
        val enabled = preferenceSource.enabled.first()
        if (!enabled) return Result.success(Unit)
        if (!biometricGate.isAvailable()) {
            // Fail open — see class KDoc.
            return Result.success(Unit)
        }
        return biometricGate.authenticate(activity, title, subtitle)
    }

    private companion object {
        const val DEFAULT_TITLE = "Entsperren"
    }
}
