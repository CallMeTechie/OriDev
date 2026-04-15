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
 * Testability seam around [BiometricPrompt]. The real Android
 * [BiometricPrompt] requires a live [FragmentActivity] and the platform
 * biometric executor to fire its callbacks — both unreachable from a JVM
 * unit test. Additionally, constructing a
 * [BiometricPrompt.PromptInfo] via its Builder reaches into
 * `android.text.TextUtils`, which is stubbed as "not mocked" in plain JVM
 * unit tests. By hiding both prompt construction and callback bridging
 * behind this interface, [BiometricGateImpl]'s error-mapping logic is
 * exercisable with a fake launcher that returns canned results
 * synchronously — no Robolectric, no TextUtils, no framework stubs.
 *
 * The production binding is [RealBiometricPromptLauncher] (wired in
 * `SecurityModule`). Tests construct [BiometricGateImpl] directly with a
 * fake launcher.
 */
interface BiometricPromptLauncher {
    suspend fun launch(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        description: String?,
    ): AuthenticationOutcome
}

/**
 * Pure-Kotlin mirror of the two terminal callbacks on
 * [BiometricPrompt.AuthenticationCallback] that [BiometricGateImpl] cares
 * about. Decouples the gate's error mapping from the framework's int
 * error-code vocabulary at the seam boundary.
 *
 * `onAuthenticationFailed` (single-attempt miss — prompt stays open) is
 * intentionally *not* represented here; the real launcher swallows it.
 */
sealed class AuthenticationOutcome {
    object Succeeded : AuthenticationOutcome()
    data class Error(val code: Int, val message: String) : AuthenticationOutcome()
}

/**
 * Production [BiometricPromptLauncher] — builds a
 * [BiometricPrompt.PromptInfo], constructs the real [BiometricPrompt], and
 * bridges its callback API to a [suspendCancellableCoroutine].
 */
@Singleton
class RealBiometricPromptLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricPromptLauncher {

    override suspend fun launch(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        description: String?,
    ): AuthenticationOutcome = suspendCancellableCoroutine { cont ->
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (subtitle != null) setSubtitle(subtitle)
                if (description != null) setDescription(description)
            }
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Abbrechen")
            .build()

        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(AuthenticationOutcome.Succeeded)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) {
                    cont.resume(AuthenticationOutcome.Error(errorCode, errString.toString()))
                }
            }
            // onAuthenticationFailed() = single-attempt miss. Prompt stays
            // open so the user can retry — intentionally not forwarded.
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        prompt.authenticate(info)
    }
}
