package dev.ori.domain.repository

/**
 * Returns a per-line diff for a local file relative to the HEAD of its git
 * repository. Returns empty if the file is not in a git repo, if git is
 * unavailable, or if the file has no changes.
 *
 * The returned map uses 1-based new-side line numbers as keys.
 */
interface LineDiffProvider {
    suspend fun getLineDiff(absolutePath: String): Map<Int, LineChange>
}

enum class LineChange { ADDED, MODIFIED, DELETED }
