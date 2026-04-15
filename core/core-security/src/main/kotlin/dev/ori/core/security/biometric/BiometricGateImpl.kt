package dev.ori.core.security.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Default [BiometricGate] implementation backed by `androidx.biometric`.
 *
 * The callback path (authenticate success/error) is NOT covered by unit
 * tests — [BiometricPrompt] requires a real [FragmentActivity] and runs on
 * the platform executor, so it is instrumentation-test territory. Only
 * [isAvailable] is unit tested.
 */
@Singleton
class BiometricGateImpl @Inject constructor(
    @ApplicationContext private val context: Context,
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
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(Result.success(Unit))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val err: BiometricError = when (errorCode) {
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

                    else -> BiometricError.Unknown(errorCode, errString.toString())
                }
                if (cont.isActive) cont.resume(Result.failure(err))
            }
            // onAuthenticationFailed() = single-attempt miss. Prompt stays
            // open so the user can retry — intentionally not forwarded.
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (subtitle != null) setSubtitle(subtitle)
                if (description != null) setDescription(description)
            }
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Abbrechen")
            .build()

        prompt.authenticate(info)
    }
}
