package dev.ori.feature.proxmox.ui

import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm

data class ProxmoxDashboardUiState(
    val nodes: List<ProxmoxNode> = emptyList(),
    val selectedNodeId: Long? = null,
    val vms: List<ProxmoxVm> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddNodeSheet: Boolean = false,
    val showCertificateDialog: CertificateTrustRequest? = null,
    val vmActionInProgress: Int? = null,
)

data class CertificateTrustRequest(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val pendingAddData: AddNodePending,
)

data class AddNodePending(
    val name: String,
    val host: String,
    val port: Int,
    val tokenId: String,
    val tokenSecret: String,
)

sealed class ProxmoxEvent {
    data class SelectNode(val nodeId: Long) : ProxmoxEvent()
    data object ShowAddNodeSheet : ProxmoxEvent()
    data object HideAddNodeSheet : ProxmoxEvent()
    data class ProbeAndAddNode(val pending: AddNodePending) : ProxmoxEvent()
    data class ConfirmTrustCertificate(val request: CertificateTrustRequest) : ProxmoxEvent()
    data class RejectCertificate(val request: CertificateTrustRequest) : ProxmoxEvent()
    data class DeleteNode(val node: ProxmoxNode) : ProxmoxEvent()
    data class StartVm(val nodeId: Long, val vmid: Int) : ProxmoxEvent()
    data class StopVm(val nodeId: Long, val vmid: Int) : ProxmoxEvent()
    data class RestartVm(val nodeId: Long, val vmid: Int) : ProxmoxEvent()
    data class DeleteVm(val nodeId: Long, val vmid: Int) : ProxmoxEvent()
    data object RefreshVms : ProxmoxEvent()
    data object ClearError : ProxmoxEvent()
}
