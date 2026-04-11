package dev.ori.core.network.model

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val permissions: String,
    val owner: String,
)
