package dev.ori.app.wear

import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import dev.ori.core.network.ssh.SshClient
import dev.ori.domain.model.WearPaths
import dev.ori.domain.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Receives MessageClient events from a paired Wear OS device.
 *
 * THREAT MODEL: MessageClient events are received from paired Wear OS devices.
 * Google Play Services ensures the wire protocol is authenticated per-device-pair.
 * However, a malicious companion app on the same paired watch could call
 * MessageClient with our path prefix. To mitigate:
 * 1. Validate sourceNodeId is in the currently paired-and-connected nodes list
 *    (not strictly sufficient, but rejects unpaired nodes).
 * 2. Command execution is limited to already-established SSH sessions; no new
 *    shell channels are opened from watch-originated messages.
 * 3. Future: add an HMAC token provisioned at pairing time (deferred).
 */
@AndroidEntryPoint
class WearMessageListenerService : WearableListenerService() {

    @Inject lateinit var connectionRepository: ConnectionRepository

    @Inject lateinit var sshClient: SshClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        scope.launch {
            if (!isTrustedNode(event.sourceNodeId)) return@launch
            when (event.path) {
                WearPaths.PANIC_DISCONNECT_ALL -> handlePanicDisconnect()
                WearPaths.COMMAND_EXECUTE -> handleCommandExecute(event)
                WearPaths.CONNECT_REQUEST -> handleConnect(event)
                WearPaths.DISCONNECT_REQUEST -> handleDisconnect(event)
                WearPaths.TWO_FA_RESPONSE -> handleTwoFactorResponse(event)
            }
        }
    }

    private suspend fun isTrustedNode(nodeId: String): Boolean = runCatching {
        val nodes = Wearable.getNodeClient(this).connectedNodes.await()
        nodes.any { it.id == nodeId }
    }.getOrDefault(false)

    private suspend fun handlePanicDisconnect() {
        connectionRepository.getActiveConnections().first().forEach { conn ->
            runCatching { connectionRepository.disconnect(conn.profileId) }
        }
    }

    private suspend fun handleCommandExecute(event: MessageEvent) {
        val requestData = DataMap.fromByteArray(event.data)
        val profileId = requestData.getLong("profileId")
        val command = requestData.getString("command") ?: return
        val requestId = requestData.getString("requestId") ?: return

        val sessionId = connectionRepository.getActiveSessionId(profileId)
        if (sessionId == null) {
            sendResponse(event.sourceNodeId, requestId, EXIT_CODE_ERROR, "", "Not connected", false)
            return
        }

        runCatching {
            val result = sshClient.executeCommand(sessionId, command)
            val stdout = result.stdout.take(STDOUT_LIMIT)
            val stderr = result.stderr.take(STDERR_LIMIT)
            val truncated = result.stdout.length > STDOUT_LIMIT || result.stderr.length > STDERR_LIMIT
            sendResponse(event.sourceNodeId, requestId, result.exitCode, stdout, stderr, truncated)
        }.onFailure {
            sendResponse(event.sourceNodeId, requestId, EXIT_CODE_ERROR, "", it.message ?: "Error", false)
        }
    }

    private fun handleTwoFactorResponse(event: MessageEvent) {
        val map = DataMap.fromByteArray(event.data)
        val requestId = map.getString("requestId") ?: return
        val approved = map.getBoolean("approved")
        TwoFactorCoordinator.completeRequest(requestId, approved)
    }

    private suspend fun sendResponse(
        nodeId: String,
        requestId: String,
        exitCode: Int,
        stdout: String,
        stderr: String,
        truncated: Boolean,
    ) {
        val messageClient = Wearable.getMessageClient(this)
        val data = DataMap().apply {
            putString("requestId", requestId)
            putInt("exitCode", exitCode)
            putString("stdout", stdout)
            putString("stderr", stderr)
            putBoolean("truncated", truncated)
        }
        runCatching {
            messageClient.sendMessage(nodeId, WearPaths.COMMAND_RESPONSE, data.toByteArray()).await()
        }
    }

    private suspend fun handleConnect(event: MessageEvent) {
        val profileId = ByteBuffer.wrap(event.data).long
        runCatching { connectionRepository.connect(profileId) }
    }

    private suspend fun handleDisconnect(event: MessageEvent) {
        val profileId = ByteBuffer.wrap(event.data).long
        runCatching { connectionRepository.disconnect(profileId) }
    }

    private companion object {
        const val STDOUT_LIMIT = 4096
        const val STDERR_LIMIT = 1024
        const val EXIT_CODE_ERROR = -1
    }
}
