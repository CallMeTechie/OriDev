package dev.ori.feature.settings.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.security.biometric.BiometricPreferenceSource
import dev.ori.feature.settings.data.AppPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/**
 * Provides a [BiometricPreferenceSource] to `:core:core-security` by
 * adapting [AppPreferences.biometricUnlock]. This lives in
 * `:feature-settings` (which already depends on `:core:core-security`) so
 * the dependency edge stays "feature → core" and core-security does NOT
 * import feature-settings. See [BiometricPreferenceSource] KDoc for the
 * circular-dependency reasoning.
 *
 * Phase 11 Tier-1 T1d — activates the biometricUnlock toggle that has been
 * cosmetic since P1.2.
 */
@Module
@InstallIn(SingletonComponent::class)
object BiometricPreferenceModule {

    @Provides
    @Singleton
    fun provideBiometricPreferenceSource(
        appPreferences: AppPreferences,
    ): BiometricPreferenceSource = object : BiometricPreferenceSource {
        override val enabled: Flow<Boolean> = appPreferences.biometricUnlock
    }
}
