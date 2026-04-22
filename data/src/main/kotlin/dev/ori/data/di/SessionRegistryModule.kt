package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.session.SessionRegistryImpl
import dev.ori.domain.repository.SessionRegistry
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionRegistryModule {

    /**
     * Bind the singleton [SessionRegistryImpl] to the domain
     * [SessionRegistry] interface. Both sides share the same instance
     * so any consumer that injects either type sees the same
     * `openSessions` / `focusedSessionId` streams.
     */
    @Binds
    @Singleton
    abstract fun bindSessionRegistry(impl: SessionRegistryImpl): SessionRegistry
}
