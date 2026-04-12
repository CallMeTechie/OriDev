package dev.ori.data.repository

import dev.ori.domain.repository.LineChange
import dev.ori.domain.repository.LineDiffProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LineDiffProviderImpl @Inject constructor(
    private val gitStatusParser: GitStatusParser,
) : LineDiffProvider {
    override suspend fun getLineDiff(absolutePath: String): Map<Int, LineChange> {
        val file = File(absolutePath)
        if (!file.exists()) return emptyMap()
        val raw = gitStatusParser.parseLineDiff(file)
        return raw.mapValues { (_, type) ->
            when (type) {
                LineChangeType.ADDED -> LineChange.ADDED
                LineChangeType.MODIFIED -> LineChange.MODIFIED
                LineChangeType.DELETED -> LineChange.DELETED
            }
        }
    }
}
