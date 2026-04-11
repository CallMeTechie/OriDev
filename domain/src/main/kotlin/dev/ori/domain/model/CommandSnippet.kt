package dev.ori.domain.model

data class CommandSnippet(
    val id: Long = 0,
    val serverProfileId: Long?,
    val name: String,
    val command: String,
    val category: String,
    val isWatchQuickCommand: Boolean = false,
    val sortOrder: Int = 0,
)
