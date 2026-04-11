package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_snippets")
data class CommandSnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long?,
    val name: String,
    val command: String,
    val category: String,
    val isWatchQuickCommand: Boolean = false,
    val sortOrder: Int = 0
)
