package dev.ori.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.common.feature.FeatureGateManager
import dev.ori.core.common.feature.FeatureGateManagerStub
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeatureGateModule {
    @Provides
    @Singleton
    fun provideFeatureGateManager(): FeatureGateManager = FeatureGateManagerStub()
}
