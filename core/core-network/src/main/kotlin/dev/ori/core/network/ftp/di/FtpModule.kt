package dev.ori.core.network.ftp.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.network.ftp.FtpClient
import dev.ori.core.network.ftp.FtpClientImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FtpModule {

    @Binds
    @Singleton
    abstract fun bindFtpClient(impl: FtpClientImpl): FtpClient
}
