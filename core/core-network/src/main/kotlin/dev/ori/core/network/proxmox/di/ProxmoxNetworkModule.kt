package dev.ori.core.network.proxmox.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.network.proxmox.ProxmoxApiService
import dev.ori.core.network.proxmox.ProxmoxApiServiceImpl
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProxmoxMoshi

@Module
@InstallIn(SingletonComponent::class)
object ProxmoxNetworkModule {

    @Provides
    @Singleton
    @ProxmoxMoshi
    fun provideProxmoxMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProxmoxNetworkBindsModule {

    @Binds
    @Singleton
    abstract fun bindProxmoxApiService(impl: ProxmoxApiServiceImpl): ProxmoxApiService
}
