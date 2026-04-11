package dev.ori.domain.usecase

import dev.ori.domain.model.Bookmark
import dev.ori.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookmarksUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(serverId: Long?): Flow<List<Bookmark>> =
        repository.getBookmarksForServer(serverId)
}
