package dev.ori.domain.model

enum class ProxmoxVmStatus {
    RUNNING,
    STOPPED,
    PAUSED,
    UNKNOWN,
    ;

    companion object {
        fun fromString(raw: String): ProxmoxVmStatus = when (raw.lowercase()) {
            "running" -> RUNNING
            "stopped" -> STOPPED
            "paused" -> PAUSED
            else -> UNKNOWN
        }
    }
}
