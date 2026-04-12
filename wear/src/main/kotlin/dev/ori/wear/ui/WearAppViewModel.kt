package dev.ori.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.wear.sync.WearDataSyncClient
import dev.ori.wear.sync.WearState
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Top-level Wear UI ViewModel. Bridges Hilt-injected sync client + state holder
 * to all composables via [hiltViewModel] in nested screens.
 */
@HiltViewModel
class WearAppViewModel @Inject constructor(
    private val syncClient: WearDataSyncClient,
    wearState: WearState,
) : ViewModel() {
    val connections = wearState.connections
    val transfers = wearState.transfers
    val snippets = wearState.snippets
    val phoneReachable = wearState.isPhoneReachable
    val lastCommandOutput = wearState.lastCommandOutput
    val pending2Fa = wearState.pending2Fa

    private val state = wearState

    fun startSync() = syncClient.start()
    fun stopSync() = syncClient.stop()

    fun sendCommand(profileId: Long, command: String) {
        viewModelScope.launch { syncClient.sendCommand(profileId, command) }
    }

    fun sendPanicDisconnect() {
        viewModelScope.launch { syncClient.sendPanicDisconnect() }
    }

    fun respondTo2Fa(requestId: String, approved: Boolean) {
        viewModelScope.launch {
            syncClient.sendTwoFactorResponse(requestId, approved)
            state.set2FaRequest(null)
        }
    }

    fun clearCommandOutput() {
        state.setCommandOutput(null)
    }
}
