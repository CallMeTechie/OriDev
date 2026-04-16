package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.ads.AdGateImpl
import dev.ori.domain.model.AdRules
import dev.ori.domain.repository.AdGate
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsDataModule {

    @Binds
    abstract fun bindAdGate(impl: AdGateImpl): AdGate

    companion object {
        @Provides
        @Singleton
        fun provideAdRules(): AdRules = AdRules()
    }
}
