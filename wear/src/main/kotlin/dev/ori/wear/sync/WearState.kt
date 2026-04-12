package dev.ori.wear.sync

import dev.ori.domain.model.WearConnectionPayload
import dev.ori.domain.model.WearSnippetPayload
import dev.ori.domain.model.WearTransferPayload
import dev.ori.domain.model.WearTwoFactorRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder of state synced from the phone via the Wear Data Layer.
 *
 * The watch UI observes these StateFlows. WearDataSyncClient is the only writer.
 */
@Singleton
class WearState @Inject constructor() {
    private val _connections = MutableStateFlow<List<WearConnectionPayload>>(emptyList())
    val connections: StateFlow<List<WearConnectionPayload>> = _connections.asStateFlow()

    private val _transfers = MutableStateFlow<List<WearTransferPayload>>(emptyList())
    val transfers: StateFlow<List<WearTransferPayload>> = _transfers.asStateFlow()

    private val _snippets = MutableStateFlow<List<WearSnippetPayload>>(emptyList())
    val snippets: StateFlow<List<WearSnippetPayload>> = _snippets.asStateFlow()

    private val _isPhoneReachable = MutableStateFlow(false)
    val isPhoneReachable: StateFlow<Boolean> = _isPhoneReachable.asStateFlow()

    private val _lastCommandOutput = MutableStateFlow<String?>(null)
    val lastCommandOutput: StateFlow<String?> = _lastCommandOutput.asStateFlow()

    private val _pending2Fa = MutableStateFlow<WearTwoFactorRequest?>(null)
    val pending2Fa: StateFlow<WearTwoFactorRequest?> = _pending2Fa.asStateFlow()

    fun updateConnections(list: List<WearConnectionPayload>) {
        _connections.value = list
    }

    fun updateTransfers(list: List<WearTransferPayload>) {
        _transfers.value = list
    }

    fun updateSnippets(list: List<WearSnippetPayload>) {
        _snippets.value = list
    }

    fun updatePhoneReachable(reachable: Boolean) {
        _isPhoneReachable.value = reachable
    }

    fun setCommandOutput(output: String?) {
        _lastCommandOutput.value = output
    }

    fun set2FaRequest(request: WearTwoFactorRequest?) {
        _pending2Fa.value = request
    }
}
