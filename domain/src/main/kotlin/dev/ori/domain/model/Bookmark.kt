package dev.ori.domain.model

data class Bookmark(
    val id: Long = 0,
    val serverProfileId: Long?,
    val path: String,
    val label: String,
)
