package dev.ori.app.service

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Phase 12 P12.4 — default Hilt bindings for the transfer engine internals.
 *
 * This PR defaults [TransferExecutor] to the skeletal [SshTransferExecutor]
 * so that the `@Inject` graphs around [TransferWorkerCoroutineFactory] and
 * [TransferDispatcher] resolve. P12.5 replaces this with a routing
 * executor that picks SSH vs. FTP per-transfer based on the
 * `ServerProfile.protocol` of each row and replaces the default
 * [CoroutineScope] binding with a service-owned supervisor scope.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TransferEngineModule {

    @Binds
    @Singleton
    abstract fun bindTransferExecutor(impl: SshTransferExecutor): TransferExecutor

    companion object {
        // TODO(P12.5): replace with an @ApplicationScope-qualified scope that
        // lives for the TransferEngineService's lifetime (SupervisorJob +
        // Dispatchers.IO). Keeping this default unblocks the graph today.
        @Provides
        @Singleton
        fun provideTransferEngineScope(): CoroutineScope =
            CoroutineScope(SupervisorJob())
    }
}
