package dev.ori.app.service

import dev.ori.core.common.model.Protocol
import dev.ori.core.network.ftp.FtpClient
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.5 / Tier 2 T2a — FTP/FTPS [TransferExecutor] that owns its
 * own `FTPClient` per invocation.
 *
 * Rationale: the P12.5 skeleton reused the app-scoped singleton [FtpClient],
 * whose Apache-Commons-Net backing field is single-threaded — two FTP
 * transfers running in parallel would race on `setRestartOffset`,
 * `storeFile`, and friends. Q3 of the Phase 12 plan decided that each
 * transfer should open its own dedicated connection. This executor
 * implements that decision by:
 *
 *   1. Resolving the stringified `sessionId` back to the originating
 *      [ServerProfile] via [ConnectionRepository.getProfileById].
 *   2. Fetching the password CharArray from [CredentialStore] (password
 *      auth is the only auth method FTP supports in Ori:Dev today).
 *   3. Calling [FtpClient.uploadFileResumableDedicated] /
 *      [FtpClient.downloadFileResumableDedicated], which open a fresh
 *      `FTPClient` for the duration of the call and tear it down in a
 *      `finally`.
 *
 * The singleton [FtpClient] instance is still used by the normal file
 * manager FTP flow (browsing, ad-hoc upload/download) which is
 * single-client-at-a-time and does not need the dedicated-connection
 * guarantee.
 */
@Singleton
internal class FtpTransferExecutor @Inject constructor(
    private val ftpClient: FtpClient,
    private val connectionRepository: ConnectionRepository,
    private val credentialStore: CredentialStore,
) : TransferExecutor {

    override suspend fun upload(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val (profile, password) = resolveCredentials(sessionId)
        try {
            ftpClient.uploadFileResumableDedicated(
                host = profile.host,
                port = profile.port,
                username = profile.username,
                password = password,
                tls = profile.protocol == Protocol.FTPS,
                localPath = localPath,
                remotePath = remotePath,
                offsetBytes = offsetBytes,
                onProgress = onProgress,
            )
        } finally {
            password.fill('\u0000')
        }
    }

    override suspend fun download(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val (profile, password) = resolveCredentials(sessionId)
        try {
            ftpClient.downloadFileResumableDedicated(
                host = profile.host,
                port = profile.port,
                username = profile.username,
                password = password,
                tls = profile.protocol == Protocol.FTPS,
                remotePath = remotePath,
                localPath = localPath,
                offsetBytes = offsetBytes,
                onProgress = onProgress,
            )
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Remote stat — the transfer engine calls this to decide overwrite
     * policy. We intentionally short-circuit to `null` if the singleton
     * client is not connected, matching the P12.5 behaviour: this is a
     * best-effort probe, not a hard requirement for correctness.
     */
    override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? =
        if (ftpClient.isConnected) ftpClient.fileSize(remotePath) else null

    private suspend fun resolveCredentials(sessionId: String): Pair<ServerProfile, CharArray> {
        val profileId = sessionId.toLongOrNull()
            ?: error("FtpTransferExecutor: invalid sessionId=$sessionId (expected serverProfileId)")
        val profile = connectionRepository.getProfileById(profileId)
            ?: error("FtpTransferExecutor: unknown server profile id=$profileId")
        val password = credentialStore.getPassword(profile.credentialRef)
            ?: error("FtpTransferExecutor: no stored password for profile=$profileId ref=${profile.credentialRef}")
        return profile to password
    }
}
