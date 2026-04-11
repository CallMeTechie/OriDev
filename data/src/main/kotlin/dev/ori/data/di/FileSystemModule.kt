package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.network.ssh.SshClient
import dev.ori.data.repository.BookmarkRepositoryImpl
import dev.ori.data.repository.LocalFileSystemRepository
import dev.ori.data.repository.RemoteFileSystemRepository
import dev.ori.domain.repository.BookmarkRepository
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.repository.LocalFileSystem
import dev.ori.domain.repository.RemoteFileSystem
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FileSystemModule {

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    companion object {
        @Provides
        @Singleton
        @LocalFileSystem
        fun provideLocalFileSystemRepository(): FileSystemRepository =
            LocalFileSystemRepository()

        @Provides
        @RemoteFileSystem
        fun provideRemoteFileSystemRepository(sshClient: SshClient): FileSystemRepository =
            RemoteFileSystemRepository(sshClient)
        // NOT @Singleton -- ViewModel manages lifecycle and sets session ID

        @Provides
        fun provideRemoteFileSystemRepositoryConcrete(sshClient: SshClient): RemoteFileSystemRepository =
            RemoteFileSystemRepository(sshClient)
        // Separate concrete binding so ViewModel can call setActiveSession()
    }
}
