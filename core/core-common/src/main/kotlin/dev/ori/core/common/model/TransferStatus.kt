package dev.ori.core.common.model

enum class TransferStatus {
    QUEUED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED
    val isActive: Boolean get() = this == ACTIVE || this == QUEUED || this == PAUSED
}
