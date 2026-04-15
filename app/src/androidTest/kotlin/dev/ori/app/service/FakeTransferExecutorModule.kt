package dev.ori.app.service

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.ori.domain.repository.TransferEngineController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Tier 3 T3a — Hilt test module that swaps the production
 * [TransferEngineModule] out of the :app graph and installs the
 * controllable [FakeTransferExecutor] in its place. Because the
 * production module also binds [TransferEngineController] and the
 * transfer-engine [CoroutineScope], this replacement must rebind all
 * three to keep the graph resolvable.
 *
 * The [TransferEngineController] binding is kept pointed at the real
 * [TransferEngineServiceControllerImpl] so instrumentation tests exercise
 * the actual Intent-dispatching controller that production code uses.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [TransferEngineModule::class],
)
internal abstract class FakeTransferExecutorModule {

    @Binds
    @Singleton
    abstract fun bindFakeTransferExecutor(impl: FakeTransferExecutor): TransferExecutor

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
