package dev.ori.feature.transfers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import dev.ori.core.common.model.TransferStatus
import dev.ori.core.ui.components.OriEmptyState
import dev.ori.core.ui.components.OriSegmentedControl
import dev.ori.core.ui.components.OriServiceIndicator
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.icons.lucide.ArrowLeftRight
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Indigo500

@Composable
fun TransferQueueScreen(
    viewModel: TransferQueueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Phase 11 carry-over #E — count of transfers currently in-flight so the
    // top bar can surface live service activity via OriServiceIndicator.
    val activeCount = uiState.transfers.count { it.status == TransferStatus.ACTIVE }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(TransferEvent.ClearError)
        }
    }

    Scaffold(
        topBar = {
            OriTopBar(
                title = "Transfers",
                height = 60.dp,
                indicator = if (activeCount > 0) {
                    { OriServiceIndicator(count = activeCount, label = "Transfers") }
                } else {
                    null
                },
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
            // Phase 11 P2.4-polish — OriSegmentedControl replaces M3 FilterChipRow.
            // Wrapped in horizontal padding to match mockup `.filter-scroll` inset.
            OriSegmentedControl(
                options = TransferFilter.entries,
                selectedValue = uiState.filter,
                onValueChange = { viewModel.onEvent(TransferEvent.SetFilter(it)) },
                optionLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                    // Phase 11 P2.4-polish — OriEmptyState replaces inline Box+Column.
                    // Lucide ArrowLeftRight replaces Material SwapVert (Lucide has no
                    // direct "swap vertical" primitive; left/right is the closest
                    // semantic match for "transfer between panes").
                    OriEmptyState(
                        icon = LucideIcons.ArrowLeftRight,
                        title = "No transfers",
                        subtitle = "Drag files between panes to start a transfer",
                    )
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
