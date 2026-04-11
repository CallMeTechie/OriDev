package dev.ori.data.repository

import dev.ori.data.dao.BookmarkDao
import dev.ori.data.entity.BookmarkEntity
import dev.ori.domain.model.Bookmark
import dev.ori.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
) : BookmarkRepository {

    override fun getBookmarksForServer(serverId: Long?): Flow<List<Bookmark>> =
        bookmarkDao.getForServer(serverId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addBookmark(bookmark: Bookmark): Long =
        bookmarkDao.insert(bookmark.toEntity())

    override suspend fun removeBookmark(bookmark: Bookmark) {
        bookmarkDao.delete(bookmark.toEntity())
    }

    private fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
        id = id,
        serverProfileId = serverProfileId,
        path = path,
        label = label,
    )

    private fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
        id = id,
        serverProfileId = serverProfileId,
        path = path,
        label = label,
        // createdAt uses default value from BookmarkEntity
    )
}
