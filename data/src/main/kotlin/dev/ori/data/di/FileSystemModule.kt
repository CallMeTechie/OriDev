package dev.ori.data.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
        // Phase 15 Task 15.6 — LocalFileSystemRepository is now SAF-backed
        // (DocumentFile + ContentResolver) instead of java.io.File.
        // GitStatusParser is no longer wired here because SAF document
        // URIs have no POSIX path → git on the shell has nothing to run
        // against. Remote (SSH) git status is unaffected.
        @Provides
        @Singleton
        @LocalFileSystem
        fun provideLocalFileSystemRepository(
            @ApplicationContext context: Context,
        ): FileSystemRepository =
            LocalFileSystemRepository(context)

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
