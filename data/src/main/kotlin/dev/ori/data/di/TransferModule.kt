package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.conflict.TransferConflictRepositoryImpl
import dev.ori.data.repository.PremiumRepositoryImpl
import dev.ori.data.repository.TransferChunkRepositoryImpl
import dev.ori.data.repository.TransferRepositoryImpl
import dev.ori.domain.repository.PremiumRepository
import dev.ori.domain.repository.TransferChunkRepository
import dev.ori.domain.repository.TransferConflictRepository
import dev.ori.domain.repository.TransferRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {

    @Binds
    @Singleton
    abstract fun bindTransferRepository(
        impl: TransferRepositoryImpl,
    ): TransferRepository

    @Binds
    @Singleton
    abstract fun bindTransferConflictRepository(
        impl: TransferConflictRepositoryImpl,
    ): TransferConflictRepository

    @Binds
    @Singleton
    abstract fun bindTransferChunkRepository(
        impl: TransferChunkRepositoryImpl,
    ): TransferChunkRepository

    @Binds
    @Singleton
    abstract fun bindPremiumRepository(
        impl: PremiumRepositoryImpl,
    ): PremiumRepository
}
