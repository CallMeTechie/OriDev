package dev.ori.domain.repository

import dev.ori.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getBookmarksForServer(serverId: Long?): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark): Long
    suspend fun removeBookmark(bookmark: Bookmark)
}
