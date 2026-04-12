package dev.ori.app.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.model.Connection
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.model.WearPaths
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.SnippetRepository
import dev.ori.domain.repository.TransferRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes phone-side state to the Wear Data Layer so the watch can render
 * connections, active transfers, and watch-pinned command snippets.
 *
 * sample(1000) is used to throttle bursts of updates and avoid flooding the
 * Data Layer API. Each publish call is wrapped in runCatching so a transient
 * Wearable failure does not tear down the publisher.
 */
@OptIn(FlowPreview::class)
@Singleton
class WearDataSyncPublisher @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val transferRepository: TransferRepository,
    private val snippetRepository: SnippetRepository,
    @ApplicationContext private val context: Context,
) {
    private val dataClient: DataClient get() = Wearable.getDataClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            connectionRepository.getActiveConnections()
                .sample(SAMPLE_INTERVAL_MS)
                .collect { connections -> publishConnections(connections) }
        }
        scope.launch {
            transferRepository.getActiveTransfers()
                .sample(SAMPLE_INTERVAL_MS)
                .collect { transfers -> publishTransfers(transfers) }
        }
        scope.launch {
            snippetRepository.getSnippetsForServer(null)
                .map { list -> list.filter { it.isWatchQuickCommand } }
                .distinctUntilChanged()
                .collect { snippets -> publishSnippets(snippets) }
        }
    }

    private suspend fun publishConnections(connections: List<Connection>) {
        runCatching {
            val request = PutDataMapRequest.create(WearPaths.CONNECTIONS_STATUS).apply {
                dataMap.putLong("updated_at", System.currentTimeMillis())
                val items = arrayListOf<DataMap>()
                for (c in connections) {
                    items.add(
                        DataMap().apply {
                            putLong("profileId", c.profileId)
                            putString("serverName", c.serverName)
                            putString("host", c.host)
                            putString("status", c.status.name)
                            putLong("connectedSinceMillis", c.connectedSince ?: 0L)
                        },
                    )
                }
                dataMap.putDataMapArrayList("items", items)
            }
            dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
        }
    }

    private suspend fun publishTransfers(transfers: List<TransferRequest>) {
        runCatching {
            val request = PutDataMapRequest.create(WearPaths.TRANSFERS_ACTIVE).apply {
                dataMap.putLong("updated_at", System.currentTimeMillis())
                val items = arrayListOf<DataMap>()
                for (t in transfers) {
                    items.add(
                        DataMap().apply {
                            putLong("transferId", t.id)
                            putString("sourcePath", t.sourcePath)
                            putString("destinationPath", t.destinationPath)
                            putString("direction", t.direction.name)
                            putString("status", t.status.name)
                            putLong("totalBytes", t.totalBytes)
                            putLong("transferredBytes", t.transferredBytes)
                            putInt("filesTransferred", t.filesTransferred)
                            putInt("fileCount", t.fileCount)
                        },
                    )
                }
                dataMap.putDataMapArrayList("items", items)
            }
            dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
        }
    }

    private suspend fun publishSnippets(snippets: List<CommandSnippet>) {
        runCatching {
            val request = PutDataMapRequest.create(WearPaths.SNIPPETS_WATCH).apply {
                dataMap.putLong("updated_at", System.currentTimeMillis())
                val items = arrayListOf<DataMap>()
                for (s in snippets) {
                    items.add(
                        DataMap().apply {
                            putLong("id", s.id)
                            putString("name", s.name)
                            putString("command", s.command)
                            putString("category", s.category)
                            putLong("serverProfileId", s.serverProfileId ?: -1L)
                        },
                    )
                }
                dataMap.putDataMapArrayList("items", items)
            }
            dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
        }
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1000L
    }
}
