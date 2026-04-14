package dev.ori.app.service

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.domain.repository.TransferEngineController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Phase 12 P12.5 — Hilt bindings for the transfer engine internals.
 *
 * [TransferExecutor] is bound to [RoutingTransferExecutor], which picks
 * SSH vs. FTP per-transfer from the `ServerProfile.protocol` of each row.
 * [TransferEngineController] is bound to the `:data` module's
 * `TransferEngineServiceControllerImpl`, which sends Intents to
 * [TransferEngineService].
 *
 * The service-owned [CoroutineScope] is still provided here as a
 * singleton `SupervisorJob + Dispatchers.IO`; the service keeps a
 * reference and cancels children on destroy, but the scope itself
 * survives across service restarts so the [TransferDispatcher] graph
 * stays stable.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TransferEngineModule {

    @Binds
    @Singleton
    abstract fun bindTransferExecutor(impl: RoutingTransferExecutor): TransferExecutor

    @Binds
    @Singleton
    abstract fun bindTransferEngineController(
        impl: TransferEngineServiceControllerImpl,
    ): TransferEngineController

    companion object {
        @Provides
        @Singleton
        fun provideTransferEngineScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
