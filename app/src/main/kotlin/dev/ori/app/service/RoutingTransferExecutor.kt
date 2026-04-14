package dev.ori.app.service

import dev.ori.core.common.model.Protocol
import dev.ori.domain.repository.ConnectionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 12 P12.5 — routes each transfer to the correct backend executor
 * based on the owning [dev.ori.domain.model.ServerProfile.protocol].
 *
 * Bound as the default [TransferExecutor] in [TransferEngineModule],
 * replacing the P12.4 skeleton binding. The `sessionId` string flowing in
 * from [TransferWorkerCoroutine] is the stringified `serverProfileId`; we
 * parse it back, look up the profile's protocol, and dispatch to the
 * protocol-appropriate executor.
 *
 * Proxmox is explicitly rejected: it is a management / VM control backend,
 * not a file transfer target. Any attempt to route a Proxmox transfer is a
 * programming error.
 */
@Singleton
internal class RoutingTransferExecutor @Inject constructor(
    private val sshExecutor: SshTransferExecutor,
    private val ftpExecutor: FtpTransferExecutor,
    private val connectionRepository: ConnectionRepository,
) : TransferExecutor {

    override suspend fun upload(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        when (protocolFor(sessionId)) {
            Protocol.SSH, Protocol.SFTP, Protocol.SCP ->
                sshExecutor.upload(sessionId, localPath, remotePath, offsetBytes, onProgress)
            Protocol.FTP, Protocol.FTPS ->
                ftpExecutor.upload(sessionId, localPath, remotePath, offsetBytes, onProgress)
            Protocol.PROXMOX ->
                throw UnsupportedOperationException("Proxmox is not a transfer target")
        }
    }

    override suspend fun download(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        when (protocolFor(sessionId)) {
            Protocol.SSH, Protocol.SFTP, Protocol.SCP ->
                sshExecutor.download(sessionId, remotePath, localPath, offsetBytes, onProgress)
            Protocol.FTP, Protocol.FTPS ->
                ftpExecutor.download(sessionId, remotePath, localPath, offsetBytes, onProgress)
            Protocol.PROXMOX ->
                throw UnsupportedOperationException("Proxmox is not a transfer target")
        }
    }

    override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? =
        when (protocolForOrNull(sessionId)) {
            Protocol.SSH, Protocol.SFTP, Protocol.SCP ->
                sshExecutor.remoteFileSize(sessionId, remotePath)
            Protocol.FTP, Protocol.FTPS ->
                ftpExecutor.remoteFileSize(sessionId, remotePath)
            Protocol.PROXMOX, null -> null
        }

    private suspend fun protocolFor(sessionId: String): Protocol {
        val profileId = sessionId.toLongOrNull()
            ?: error("RoutingTransferExecutor: invalid sessionId=$sessionId")
        val profile = connectionRepository.getProfileById(profileId)
            ?: error("RoutingTransferExecutor: unknown server profile id=$profileId")
        return profile.protocol
    }

    private suspend fun protocolForOrNull(sessionId: String): Protocol? {
        val profileId = sessionId.toLongOrNull() ?: return null
        return connectionRepository.getProfileById(profileId)?.protocol
    }
}
