package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.common.process.DefaultProcessRunner
import dev.ori.core.common.process.ProcessRunner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessModule {
    @Binds
    @Singleton
    abstract fun bindProcessRunner(impl: DefaultProcessRunner): ProcessRunner
}
