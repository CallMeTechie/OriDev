package dev.ori.core.network.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.network.ssh.SshClientImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindSshClient(impl: SshClientImpl): SshClient
}
