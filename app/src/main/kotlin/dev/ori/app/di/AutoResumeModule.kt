package dev.ori.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.domain.preferences.AutoResumePreferences
import dev.ori.feature.settings.data.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AutoResumeModule {
    @Provides
    @Singleton
    fun provideAutoResumePreferences(
        appPreferences: AppPreferences,
    ): AutoResumePreferences = appPreferences
}
