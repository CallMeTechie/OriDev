package dev.ori.feature.proxmox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.component.LoadingIndicator
import dev.ori.core.ui.component.OriDevTopBar
import dev.ori.core.ui.theme.Gray500

private const val NODE_ROW_HEIGHT_DP = 140

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: ProxmoxDashboardViewModel = hiltViewModel(),
    @Suppress("UNUSED_PARAMETER") onNavigateToTerminal: (profileId: Long) -> Unit = {},
    onNavigateToCreateVm: (nodeId: Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        val error = state.error
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(ProxmoxEvent.ClearError)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { OriDevTopBar(title = "Proxmox Manager") },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.onEvent(ProxmoxEvent.ShowAddNodeSheet) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Proxmox node",
                    )
                },
                text = { Text("Add Node") },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.nodes.isEmpty() -> LoadingIndicator()
                state.nodes.isEmpty() -> EmptyState()
                else -> DashboardContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onNavigateToCreateVm = onNavigateToCreateVm,
                )
            }
        }
    }

    if (state.showAddNodeSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(ProxmoxEvent.HideAddNodeSheet) },
            sheetState = sheetState,
        ) {
            AddNodeSheet(
                onDismiss = { viewModel.onEvent(ProxmoxEvent.HideAddNodeSheet) },
                onProbeAndAdd = { pending ->
                    viewModel.onEvent(ProxmoxEvent.ProbeAndAddNode(pending))
                },
                isLoading = state.isLoading,
            )
        }
    }

    val certDialog = state.showCertificateDialog
    if (certDialog != null) {
        CertificateTrustDialog(
            request = certDialog,
            onConfirm = { viewModel.onEvent(ProxmoxEvent.ConfirmTrustCertificate(certDialog)) },
            onDismiss = { viewModel.onEvent(ProxmoxEvent.RejectCertificate(certDialog)) },
        )
    }
}

@Composable
private fun DashboardContent(
    state: ProxmoxDashboardUiState,
    onEvent: (ProxmoxEvent) -> Unit,
    onNavigateToCreateVm: (nodeId: Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxSize()
                .height(NODE_ROW_HEIGHT_DP.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.nodes, key = { it.id }) { node ->
                NodeCard(
                    node = node,
                    selected = node.id == state.selectedNodeId,
                    onClick = { onEvent(ProxmoxEvent.SelectNode(node.id)) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.vms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No VMs found on this node.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray500,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.vms, key = { it.vmid }) { vm ->
                    val nodeId = state.selectedNodeId ?: return@items
                    VmCard(
                        vm = vm,
                        actionInProgress = state.vmActionInProgress == vm.vmid,
                        onStart = { onEvent(ProxmoxEvent.StartVm(nodeId, vm.vmid)) },
                        onStop = { onEvent(ProxmoxEvent.StopVm(nodeId, vm.vmid)) },
                        onRestart = { onEvent(ProxmoxEvent.RestartVm(nodeId, vm.vmid)) },
                        onDelete = { onEvent(ProxmoxEvent.DeleteVm(nodeId, vm.vmid)) },
                    )
                }
            }
        }
    }
    // Suppress unused parameter warning until Task 7.5 wires CreateVm navigation.
    @Suppress("UNUSED_EXPRESSION")
    onNavigateToCreateVm
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No Proxmox nodes yet. Tap + to add one.",
            style = MaterialTheme.typography.bodyLarge,
            color = Gray500,
            textAlign = TextAlign.Center,
        )
    }
}
