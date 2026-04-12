package dev.ori.wear.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.domain.model.WearConnectionPayload
import dev.ori.domain.model.WearPaths
import dev.ori.domain.model.WearSnippetPayload
import dev.ori.domain.model.WearTransferPayload
import dev.ori.domain.model.WearTwoFactorRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watch-side client that listens to the phone via the Wear Data Layer and forwards
 * incoming updates to [WearState]. Also exposes outbound message helpers (commands,
 * panic disconnect, 2FA response).
 *
 * Lifecycle: [start]/[stop] are idempotent. The internal coroutine scope is recreated
 * on every [start] so that stop()+start() cycles work correctly across activity
 * recreation.
 */
@Singleton
class WearDataSyncClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearState: WearState,
) : DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)

    private var scope: CoroutineScope? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        dataClient.addListener(this)
        messageClient.addListener(this)
        s.launch { pollPhoneReachability() }
        s.launch { loadInitialState() }
    }

    fun stop() {
        if (!started) return
        started = false
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        scope?.cancel()
        scope = null
    }

    override fun onDataChanged(buffer: DataEventBuffer) {
        for (event in buffer) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            handleDataUpdate(path, DataMapItem.fromDataItem(event.dataItem).dataMap)
        }
        buffer.release()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.COMMAND_RESPONSE -> handleCommandResponse(event.data)
            WearPaths.TWO_FA_REQUEST -> handleTwoFactorRequest(event.data)
        }
    }

    private fun handleDataUpdate(path: String, dataMap: DataMap) {
        when (path) {
            WearPaths.CONNECTIONS_STATUS -> {
                val items = dataMap.getDataMapArrayList("items").orEmpty()
                val list = items.map { m ->
                    WearConnectionPayload(
                        profileId = m.getLong("profileId"),
                        serverName = m.getString("serverName") ?: "",
                        host = m.getString("host") ?: "",
                        status = m.getString("status") ?: "DISCONNECTED",
                        connectedSinceMillis = m.getLong("connectedSinceMillis").takeIf { it > 0 },
                    )
                }
                wearState.updateConnections(list)
            }
            WearPaths.TRANSFERS_ACTIVE -> {
                val items = dataMap.getDataMapArrayList("items").orEmpty()
                val list = items.map { m ->
                    WearTransferPayload(
                        transferId = m.getLong("transferId"),
                        sourcePath = m.getString("sourcePath") ?: "",
                        destinationPath = m.getString("destinationPath") ?: "",
                        direction = m.getString("direction") ?: "",
                        status = m.getString("status") ?: "",
                        totalBytes = m.getLong("totalBytes"),
                        transferredBytes = m.getLong("transferredBytes"),
                        filesTransferred = m.getInt("filesTransferred"),
                        fileCount = m.getInt("fileCount"),
                    )
                }
                wearState.updateTransfers(list)
            }
            WearPaths.SNIPPETS_WATCH -> {
                val items = dataMap.getDataMapArrayList("items").orEmpty()
                val list = items.map { m ->
                    val rawProfileId = m.getLong("serverProfileId")
                    WearSnippetPayload(
                        id = m.getLong("id"),
                        name = m.getString("name") ?: "",
                        command = m.getString("command") ?: "",
                        category = m.getString("category") ?: "",
                        serverProfileId = rawProfileId.takeIf { it >= 0 },
                    )
                }
                wearState.updateSnippets(list)
            }
        }
    }

    private fun handleCommandResponse(data: ByteArray) {
        val map = DataMap.fromByteArray(data)
        val stdout = map.getString("stdout") ?: ""
        val stderr = map.getString("stderr") ?: ""
        val exitCode = map.getInt("exitCode")
        val truncated = map.getBoolean("truncated")
        val output = buildString {
            append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append("[stderr]\n").append(stderr)
            }
            if (truncated) {
                if (isNotEmpty()) append('\n')
                append("[... output truncated ...]")
            }
            if (exitCode != 0) {
                if (isNotEmpty()) append('\n')
                append("[exit: ").append(exitCode).append(']')
            }
        }
        wearState.setCommandOutput(output)
    }

    private fun handleTwoFactorRequest(data: ByteArray) {
        val map = DataMap.fromByteArray(data)
        val request = WearTwoFactorRequest(
            requestId = map.getString("requestId") ?: return,
            profileId = map.getLong("profileId"),
            serverName = map.getString("serverName") ?: "",
            host = map.getString("host") ?: "",
            expiresAtMillis = map.getLong("expiresAtMillis"),
        )
        wearState.set2FaRequest(request)
    }

    private suspend fun pollPhoneReachability() {
        while (true) {
            val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
            wearState.updatePhoneReachable(nodes.any { it.isNearby })
            delay(REACHABILITY_POLL_INTERVAL_MS)
        }
    }

    private suspend fun loadInitialState() {
        listOf(
            WearPaths.CONNECTIONS_STATUS,
            WearPaths.TRANSFERS_ACTIVE,
            WearPaths.SNIPPETS_WATCH,
        ).forEach { path ->
            runCatching {
                val items = dataClient.getDataItems(Uri.parse("wear://*$path")).await()
                items.forEach { item ->
                    handleDataUpdate(path, DataMapItem.fromDataItem(item).dataMap)
                }
                items.release()
            }
        }
    }

    /** Watch -> Phone: send an execute-command request. Returns the requestId. */
    suspend fun sendCommand(profileId: Long, command: String): String {
        val requestId = UUID.randomUUID().toString()
        val data = DataMap().apply {
            putString("requestId", requestId)
            putLong("profileId", profileId)
            putString("command", command)
        }
        sendToFirstNearbyNode(WearPaths.COMMAND_EXECUTE, data.toByteArray())
        return requestId
    }

    /** Watch -> Phone: panic disconnect all active SSH sessions. */
    suspend fun sendPanicDisconnect() {
        sendToFirstNearbyNode(WearPaths.PANIC_DISCONNECT_ALL, ByteArray(0))
    }

    /** Watch -> Phone: respond to a 2FA approval request. */
    suspend fun sendTwoFactorResponse(requestId: String, approved: Boolean) {
        val data = DataMap().apply {
            putString("requestId", requestId)
            putBoolean("approved", approved)
        }
        sendToFirstNearbyNode(WearPaths.TWO_FA_RESPONSE, data.toByteArray())
    }

    private suspend fun sendToFirstNearbyNode(path: String, payload: ByteArray) {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        nodes.firstOrNull { it.isNearby }?.let { node ->
            runCatching {
                messageClient.sendMessage(node.id, path, payload).await()
            }
        }
    }

    private companion object {
        const val REACHABILITY_POLL_INTERVAL_MS = 5000L
    }
}
