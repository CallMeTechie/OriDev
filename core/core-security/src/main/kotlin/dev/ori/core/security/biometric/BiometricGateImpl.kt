package dev.ori.core.security.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [BiometricGate] implementation backed by `androidx.biometric`.
 *
 * Prompt construction and the [BiometricPrompt] callback bridge live
 * behind the [BiometricPromptLauncher] seam so the authenticate()
 * error-mapping path can be exercised with a fake launcher from a plain
 * JVM unit test. This keeps the gate free of any framework calls — even
 * transitive `TextUtils` hits from [BiometricPrompt.PromptInfo.Builder] —
 * and lets tests assert error-code → [BiometricError] mapping directly.
 */
@Singleton
class BiometricGateImpl @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val launcher: BiometricPromptLauncher,
) : BiometricGate {

    override suspend fun isAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        description: String?,
    ): Result<Unit> = when (
        val outcome = launcher.launch(activity, title, subtitle, description)
    ) {
        AuthenticationOutcome.Succeeded -> Result.success(Unit)
        is AuthenticationOutcome.Error -> Result.failure(mapError(outcome.code, outcome.message))
    }

    private fun mapError(code: Int, message: String): BiometricError = when (code) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> BiometricError.UserCancelled

        BiometricPrompt.ERROR_LOCKOUT,
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        -> BiometricError.LockedOut

        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        -> BiometricError.NotAvailable

        else -> BiometricError.Unknown(code, message)
    }
}
