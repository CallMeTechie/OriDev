package dev.ori.feature.transfers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.model.TransferStatus
import dev.ori.domain.model.TransferRequest
import dev.ori.domain.usecase.CancelTransferUseCase
import dev.ori.domain.usecase.ClearCompletedTransfersUseCase
import dev.ori.domain.usecase.EnqueueTransferUseCase
import dev.ori.domain.usecase.GetTransfersUseCase
import dev.ori.domain.usecase.PauseTransferUseCase
import dev.ori.domain.usecase.ResumeTransferUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransferQueueViewModel @Inject constructor(
    private val getTransfersUseCase: GetTransfersUseCase,
    private val pauseTransferUseCase: PauseTransferUseCase,
    private val resumeTransferUseCase: ResumeTransferUseCase,
    private val cancelTransferUseCase: CancelTransferUseCase,
    private val enqueueTransferUseCase: EnqueueTransferUseCase,
    private val clearCompletedTransfersUseCase: ClearCompletedTransfersUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferQueueUiState())
    val uiState: StateFlow<TransferQueueUiState> = _uiState.asStateFlow()

    init {
        loadTransfers()
    }

    fun onEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.SetFilter -> _uiState.update { it.copy(filter = event.filter) }
            is TransferEvent.PauseTransfer -> pauseTransfer(event.transferId)
            is TransferEvent.ResumeTransfer -> resumeTransfer(event.transferId)
            is TransferEvent.CancelTransfer -> cancelTransfer(event.transferId)
            is TransferEvent.RetryTransfer -> retryTransfer(event.transfer)
            is TransferEvent.ClearCompleted -> clearCompleted()
            is TransferEvent.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun loadTransfers() {
        viewModelScope.launch {
            getTransfersUseCase()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load transfers",
                        )
                    }
                }
                .collect { transfers ->
                    _uiState.update {
                        it.copy(
                            transfers = applyFilter(transfers, it.filter),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun applyFilter(
        transfers: List<TransferRequest>,
        filter: TransferFilter,
    ) = when (filter) {
        TransferFilter.ALL -> transfers
        TransferFilter.ACTIVE -> transfers.filter { it.status.isActive }
        TransferFilter.COMPLETED -> transfers.filter { it.status == TransferStatus.COMPLETED }
        TransferFilter.FAILED -> transfers.filter { it.status == TransferStatus.FAILED }
    }

    private fun pauseTransfer(transferId: Long) {
        viewModelScope.launch {
            runCatching { pauseTransferUseCase(transferId) }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to pause transfer") }
                }
        }
    }

    private fun resumeTransfer(transferId: Long) {
        viewModelScope.launch {
            runCatching { resumeTransferUseCase(transferId) }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to resume transfer") }
                }
        }
    }

    private fun cancelTransfer(transferId: Long) {
        viewModelScope.launch {
            runCatching { cancelTransferUseCase(transferId) }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to cancel transfer") }
                }
        }
    }

    private fun retryTransfer(transfer: TransferRequest) {
        viewModelScope.launch {
            runCatching {
                enqueueTransferUseCase(
                    transfer.copy(
                        id = 0,
                        status = TransferStatus.QUEUED,
                        transferredBytes = 0,
                        errorMessage = null,
                        retryCount = transfer.retryCount + 1,
                    ),
                )
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to retry transfer") }
            }
        }
    }

    private fun clearCompleted() {
        viewModelScope.launch {
            runCatching { clearCompletedTransfersUseCase() }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = e.message ?: "Failed to clear completed transfers")
                    }
                }
        }
    }
}
