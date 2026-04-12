package dev.ori.feature.transfers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.component.OriDevTopBar
import dev.ori.core.ui.theme.Gray400
import dev.ori.core.ui.theme.Indigo500

@Composable
fun TransferQueueScreen(
    viewModel: TransferQueueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(TransferEvent.ClearError)
        }
    }

    Scaffold(
        topBar = {
            OriDevTopBar(
                title = "Transfers",
                actions = {
                    TextButton(onClick = { viewModel.onEvent(TransferEvent.ClearCompleted) }) {
                        Text("Clear", color = Indigo500)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterChipRow(
                selectedFilter = uiState.filter,
                onFilterSelected = { viewModel.onEvent(TransferEvent.SetFilter(it)) },
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Indigo500)
                    }
                }
                uiState.transfers.isEmpty() -> {
                    EmptyTransfersState()
                }
                else -> {
                    TransferList(
                        transfers = uiState.transfers,
                        onEvent = viewModel::onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    selectedFilter: TransferFilter,
    onFilterSelected: (TransferFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransferFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Indigo500.copy(alpha = 0.12f),
                    selectedLabelColor = Indigo500,
                ),
            )
        }
    }
}

@Composable
private fun TransferList(
    transfers: List<dev.ori.domain.model.TransferRequest>,
    onEvent: (TransferEvent) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(transfers, key = { it.id }) { transfer ->
            TransferItemCard(
                transfer = transfer,
                onPause = { onEvent(TransferEvent.PauseTransfer(transfer.id)) },
                onResume = { onEvent(TransferEvent.ResumeTransfer(transfer.id)) },
                onCancel = { onEvent(TransferEvent.CancelTransfer(transfer.id)) },
                onRetry = { onEvent(TransferEvent.RetryTransfer(transfer)) },
            )
        }
    }
}

@Composable
private fun EmptyTransfersState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = "Keine Übertragungen",
                modifier = Modifier.size(48.dp),
                tint = Gray400,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No transfers",
                style = MaterialTheme.typography.titleMedium,
                color = Gray400,
            )
            Text(
                text = "Drag files between panes to start a transfer",
                style = MaterialTheme.typography.bodySmall,
                color = Gray400,
            )
        }
    }
}
