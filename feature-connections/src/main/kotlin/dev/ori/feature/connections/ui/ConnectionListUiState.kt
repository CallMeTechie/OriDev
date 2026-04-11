package dev.ori.feature.connections.ui

import dev.ori.domain.model.Connection
import dev.ori.domain.model.ServerProfile

data class ConnectionListUiState(
    val profiles: List<ServerProfile> = emptyList(),
    val favorites: List<ServerProfile> = emptyList(),
    val activeConnections: List<Connection> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
)

sealed class ConnectionListEvent {
    data class Connect(val profileId: Long) : ConnectionListEvent()
    data class Disconnect(val profileId: Long) : ConnectionListEvent()
    data class Delete(val profile: ServerProfile) : ConnectionListEvent()
    data class ToggleFavorite(val profile: ServerProfile) : ConnectionListEvent()
    data class Search(val query: String) : ConnectionListEvent()
    data object ClearError : ConnectionListEvent()
}
