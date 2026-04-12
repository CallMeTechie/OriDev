package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.repository.ClaudeRepositoryImpl
import dev.ori.data.repository.SessionRecordingRepositoryImpl
import dev.ori.domain.repository.ClaudeRepository
import dev.ori.domain.repository.SessionRecordingRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingsModule {

    @Binds
    @Singleton
    abstract fun bindClaudeRepository(impl: ClaudeRepositoryImpl): ClaudeRepository

    @Binds
    @Singleton
    abstract fun bindSessionRecordingRepository(
        impl: SessionRecordingRepositoryImpl,
    ): SessionRecordingRepository
}
