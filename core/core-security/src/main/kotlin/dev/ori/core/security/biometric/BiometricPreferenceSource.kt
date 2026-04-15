package dev.ori.core.security.biometric

import kotlinx.coroutines.flow.Flow

/**
 * Thin SPI that lets [CredentialUnlockGate] read the user-configurable
 * "require biometric for credentials" toggle without pulling the full
 * `:feature-settings` module (which would otherwise create a circular
 * dependency: `:feature-settings` already depends on `:core:core-security`
 * for the Keystore / clipboard helpers).
 *
 * The concrete implementation is provided by `:feature-settings` via a
 * Hilt `@Provides` method that adapts [dev.ori.feature.settings.data.AppPreferences.biometricUnlock]
 * into this interface. That inverts the dependency edge back to "leaf →
 * core-security" and keeps core-security free of feature imports.
 *
 * If the preference store is ever extracted into `:domain` or
 * `:core:core-common`, this interface can be deleted and callers can read
 * the flow directly.
 */
interface BiometricPreferenceSource {
    /**
     * Emits the current value of the "biometric unlock" toggle. Cold flow —
     * [CredentialUnlockGate] reads a single snapshot via `first()`.
     */
    val enabled: Flow<Boolean>
}
