package dev.ori.data.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ori.data.repository.BookmarkRepositoryImpl
import dev.ori.data.repository.LocalFileSystemRepository
import dev.ori.data.repository.RemoteFileSystemRepository
import dev.ori.domain.repository.BookmarkRepository
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.repository.LocalFileSystem
import dev.ori.domain.repository.RemoteFileSystem
import dev.ori.domain.repository.RemoteFileSystemSession
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FileSystemModule {

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    /**
     * Delegate the [FileSystemRepository] qualified with [RemoteFileSystem]
     * to the singleton [RemoteFileSystemRepository] injected elsewhere as
     * the concrete type. Before this binding was split into two `@Provides`
     * factories, Hilt minted a fresh `RemoteFileSystemRepository` for
     * each request — one for the file-manager via the interface and one
     * for the ViewModel via the concrete type. Their `activeSessionId`
     * fields were independent, so whatever session id the ViewModel set
     * was invisible to the file-manager and every list call tripped the
     * "No active SSH session" guard.
     */
    @Binds
    @Singleton
    @RemoteFileSystem
    abstract fun bindRemoteFileSystemRepository(
        impl: RemoteFileSystemRepository,
    ): FileSystemRepository

    /**
     * Share the same singleton [RemoteFileSystemRepository] between the
     * [RemoteFileSystem]-qualified [FileSystemRepository] and the
     * [RemoteFileSystemSession] binding, so a session id set via one
     * route is visible to the other.
     */
    @Binds
    @Singleton
    abstract fun bindRemoteFileSystemSession(
        impl: RemoteFileSystemRepository,
    ): RemoteFileSystemSession

    companion object {
        // Phase 15 Task 15.6 — LocalFileSystemRepository is SAF-backed
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
    }
}
