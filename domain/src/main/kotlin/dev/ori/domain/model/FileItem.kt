package dev.ori.domain.model

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val permissions: String? = null,
    val owner: String? = null,
    val gitStatus: GitStatus? = null,
)

enum class GitStatus {
    STAGED,
    MODIFIED,
    UNTRACKED,
}
