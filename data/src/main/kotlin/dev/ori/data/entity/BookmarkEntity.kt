package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long?,
    val path: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis(),
)
