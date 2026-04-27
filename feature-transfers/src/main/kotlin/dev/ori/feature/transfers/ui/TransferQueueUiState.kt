package dev.ori.feature.transfers.ui

import dev.ori.domain.model.ConflictRequest
import dev.ori.domain.model.TransferRequest

enum class TransferFilter {
    ALL,
    ACTIVE,
    COMPLETED,
    FAILED,
}

data class TransferQueueUiState(
    val transfers: List<TransferRequest> = emptyList(),
    val allTransfers: List<TransferRequest> = emptyList(),
    val filter: TransferFilter = TransferFilter.ALL,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Bug I — informational success Snackbar message. Kept separate from
    // `error` so the UI layer can style success and failure feedback
    // independently (e.g. error tint vs neutral surface).
    val infoSnackbar: String? = null,
    val pendingConflict: ConflictRequest? = null,
)

sealed class TransferEvent {
    data class SetFilter(val filter: TransferFilter) : TransferEvent()
    data class PauseTransfer(val transferId: Long) : TransferEvent()
    data class ResumeTransfer(val transferId: Long) : TransferEvent()
    data class CancelTransfer(val transferId: Long) : TransferEvent()
    data class RetryTransfer(val transfer: TransferRequest) : TransferEvent()
    data object ClearCompleted : TransferEvent()
    data object ClearError : TransferEvent()
    data object ClearInfo : TransferEvent()
    data object PauseAll : TransferEvent()
    data object CancelAll : TransferEvent()
    data class ResolveConflict(
        val conflictId: String,
        val resolution: dev.ori.domain.model.ConflictResolution,
    ) : TransferEvent()
}
