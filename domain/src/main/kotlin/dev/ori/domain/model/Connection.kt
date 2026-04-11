package dev.ori.domain.model

data class Connection(
    val profileId: Long,
    val serverName: String,
    val host: String,
    val status: ConnectionStatus,
    val connectedSince: Long? = null,
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR,
}
