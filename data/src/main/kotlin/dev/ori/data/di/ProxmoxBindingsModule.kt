package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.repository.ProxmoxRepositoryImpl
import dev.ori.domain.repository.ProxmoxRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProxmoxBindingsModule {

    @Binds
    @Singleton
    abstract fun bindProxmoxRepository(impl: ProxmoxRepositoryImpl): ProxmoxRepository
}
