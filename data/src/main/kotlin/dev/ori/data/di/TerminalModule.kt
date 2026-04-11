package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.repository.SnippetRepositoryImpl
import dev.ori.domain.repository.SnippetRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TerminalModule {

    @Binds
    @Singleton
    abstract fun bindSnippetRepository(impl: SnippetRepositoryImpl): SnippetRepository
}
