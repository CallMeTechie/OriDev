package dev.ori.domain.repository

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalFileSystem

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteFileSystem
