package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.conflict.TransferConflictRepositoryImpl
import dev.ori.data.repository.TransferRepositoryImpl
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
}
