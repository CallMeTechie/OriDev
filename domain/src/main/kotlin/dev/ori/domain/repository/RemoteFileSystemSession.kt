package dev.ori.domain.repository

/**
 * Session-binding side of the remote [FileSystemRepository]. Kept separate
 * from [FileSystemRepository] so the pure file-operation contract stays
 * uniform across local and remote implementations — only the remote impl
 * needs a handle to tie operations to an SSH session.
 *
 * Callers obtain the active SSH session id from
 * [ConnectionRepository.getActiveSessionId] for a given profile and push
 * it into [setActiveSession] before invoking any remote file operation.
 * The underlying implementation is a singleton shared with the `@RemoteFileSystem`
 * qualified [FileSystemRepository] binding, so the session id set here
 * is visible to all subsequent list/read/write calls.
 */
interface RemoteFileSystemSession {
    fun setActiveSession(sessionId: String)
}
