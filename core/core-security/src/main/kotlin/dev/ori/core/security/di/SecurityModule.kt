package dev.ori.core.security.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.security.KeyStoreManager
import dev.ori.core.security.biometric.BiometricGate
import dev.ori.core.security.biometric.BiometricGateImpl
import dev.ori.domain.repository.CredentialStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindCredentialStore(impl: KeyStoreManager): CredentialStore

    @Binds
    @Singleton
    abstract fun bindBiometricGate(impl: BiometricGateImpl): BiometricGate
}
