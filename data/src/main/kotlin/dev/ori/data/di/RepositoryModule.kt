package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.network.ssh.HostKeyStore
import dev.ori.data.repository.ConnectionRepositoryImpl
import dev.ori.data.repository.HostKeyStoreImpl
import dev.ori.data.repository.KnownHostRepositoryImpl
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.KnownHostRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(
        impl: ConnectionRepositoryImpl,
    ): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindKnownHostRepository(
        impl: KnownHostRepositoryImpl,
    ): KnownHostRepository

    @Binds
    @Singleton
    abstract fun bindHostKeyStore(
        impl: HostKeyStoreImpl,
    ): HostKeyStore
}
