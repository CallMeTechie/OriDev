package dev.ori.core.common.model

enum class TransferStatus {
    QUEUED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
    val isActive: Boolean get() = this == ACTIVE || this == QUEUED || this == PAUSED
}
