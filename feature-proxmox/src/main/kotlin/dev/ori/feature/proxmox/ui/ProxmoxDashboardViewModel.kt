package dev.ori.feature.proxmox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.result.getAppError
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.usecase.AddProxmoxNodeUseCase
import dev.ori.domain.usecase.DeleteProxmoxNodeUseCase
import dev.ori.domain.usecase.DeleteVmUseCase
import dev.ori.domain.usecase.GetProxmoxNodesUseCase
import dev.ori.domain.usecase.GetProxmoxVmsUseCase
import dev.ori.domain.usecase.ProbeCertificateUseCase
import dev.ori.domain.usecase.RestartVmUseCase
import dev.ori.domain.usecase.StartVmUseCase
import dev.ori.domain.usecase.StopVmUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class ProxmoxDashboardViewModel @Inject constructor(
    private val getProxmoxNodes: GetProxmoxNodesUseCase,
    private val getProxmoxVms: GetProxmoxVmsUseCase,
    private val startVmUseCase: StartVmUseCase,
    private val stopVmUseCase: StopVmUseCase,
    private val restartVmUseCase: RestartVmUseCase,
    private val deleteVmUseCase: DeleteVmUseCase,
    private val probeCertificateUseCase: ProbeCertificateUseCase,
    private val addProxmoxNodeUseCase: AddProxmoxNodeUseCase,
    private val deleteProxmoxNodeUseCase: DeleteProxmoxNodeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProxmoxDashboardUiState(isLoading = true))
    val uiState: StateFlow<ProxmoxDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getProxmoxNodes()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load Proxmox nodes",
                        )
                    }
                }
                .collect { nodes ->
                    val currentSelected = _uiState.value.selectedNodeId
                    val newSelected = when {
                        currentSelected != null && nodes.any { it.id == currentSelected } -> currentSelected
                        nodes.isNotEmpty() -> nodes.first().id
                        else -> null
                    }
                    _uiState.update {
                        it.copy(
                            nodes = nodes,
                            selectedNodeId = newSelected,
                            isLoading = false,
                        )
                    }
                    if (newSelected != null && newSelected != currentSelected) {
                        loadVms(newSelected)
                    }
                }
        }
    }

    fun onEvent(event: ProxmoxEvent) {
        when (event) {
            is ProxmoxEvent.SelectNode -> selectNode(event.nodeId)
            ProxmoxEvent.ShowAddNodeSheet ->
                _uiState.update { it.copy(showAddNodeSheet = true) }
            ProxmoxEvent.HideAddNodeSheet ->
                _uiState.update { it.copy(showAddNodeSheet = false) }
            is ProxmoxEvent.ProbeAndAddNode -> probeAndAddNode(event.pending)
            is ProxmoxEvent.ConfirmTrustCertificate -> confirmTrustCertificate(event.request)
            is ProxmoxEvent.RejectCertificate ->
                _uiState.update { it.copy(showCertificateDialog = null) }
            is ProxmoxEvent.DeleteNode -> deleteNode(event.node)
            is ProxmoxEvent.StartVm -> runVmAction(event.nodeId, event.vmid) {
                startVmUseCase(event.nodeId, event.vmid)
            }
            is ProxmoxEvent.StopVm -> runVmAction(event.nodeId, event.vmid) {
                stopVmUseCase(event.nodeId, event.vmid)
            }
            is ProxmoxEvent.RestartVm -> runVmAction(event.nodeId, event.vmid) {
                restartVmUseCase(event.nodeId, event.vmid)
            }
            is ProxmoxEvent.DeleteVm -> runVmAction(event.nodeId, event.vmid) {
                deleteVmUseCase(event.nodeId, event.vmid)
            }
            ProxmoxEvent.RefreshVms -> {
                _uiState.value.selectedNodeId?.let { loadVms(it) }
            }
            ProxmoxEvent.ClearError ->
                _uiState.update { it.copy(error = null) }
        }
    }

    private fun selectNode(nodeId: Long) {
        _uiState.update { it.copy(selectedNodeId = nodeId) }
        loadVms(nodeId)
    }

    private fun loadVms(nodeId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = getProxmoxVms(nodeId)
            result.fold(
                onSuccess = { vms ->
                    _uiState.update { it.copy(vms = vms, isLoading = false) }
                },
                onFailure = {
                    val message = result.getAppError()?.message ?: it.message ?: "Failed to load VMs"
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = message)
                    }
                },
            )
        }
    }

    private fun probeAndAddNode(pending: AddNodePending) {
        viewModelScope.launch {
            val result = probeCertificateUseCase(pending.host, pending.port)
            result.fold(
                onSuccess = { fingerprint ->
                    _uiState.update {
                        it.copy(
                            showAddNodeSheet = false,
                            showCertificateDialog = CertificateTrustRequest(
                                host = pending.host,
                                port = pending.port,
                                fingerprint = fingerprint,
                                pendingAddData = pending,
                            ),
                        )
                    }
                },
                onFailure = {
                    val message = result.getAppError()?.message ?: it.message ?: "Failed to probe certificate"
                    _uiState.update { state -> state.copy(error = message) }
                },
            )
        }
    }

    private fun confirmTrustCertificate(request: CertificateTrustRequest) {
        viewModelScope.launch {
            val pending = request.pendingAddData
            val result = addProxmoxNodeUseCase(
                name = pending.name,
                host = pending.host,
                port = pending.port,
                tokenId = pending.tokenId,
                tokenSecret = pending.tokenSecret,
                certFingerprint = request.fingerprint,
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            showCertificateDialog = null,
                            showAddNodeSheet = false,
                        )
                    }
                },
                onFailure = {
                    val message = result.getAppError()?.message ?: it.message ?: "Failed to add node"
                    _uiState.update { state ->
                        state.copy(
                            showCertificateDialog = null,
                            error = message,
                        )
                    }
                },
            )
        }
    }

    private fun deleteNode(node: ProxmoxNode) {
        viewModelScope.launch {
            try {
                deleteProxmoxNodeUseCase(node)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete node") }
            }
        }
    }

    private fun runVmAction(
        nodeId: Long,
        vmid: Int,
        action: suspend () -> Result<String>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(vmActionInProgress = vmid) }
            val result = action()
            result.fold(
                onSuccess = {
                    _uiState.update { state -> state.copy(vmActionInProgress = null) }
                    loadVms(nodeId)
                },
                onFailure = {
                    val message = result.getAppError()?.message ?: it.message ?: "VM action failed"
                    _uiState.update { state ->
                        state.copy(
                            vmActionInProgress = null,
                            error = message,
                        )
                    }
                },
            )
        }
    }
}
