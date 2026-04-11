package dev.ori.feature.connections.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.result.getAppError
import dev.ori.domain.usecase.ConnectUseCase
import dev.ori.domain.usecase.DeleteProfileUseCase
import dev.ori.domain.usecase.DisconnectUseCase
import dev.ori.domain.usecase.GetConnectionsUseCase
import dev.ori.domain.usecase.GetFavoriteConnectionsUseCase
import dev.ori.domain.usecase.SaveProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionListViewModel @Inject constructor(
    private val getConnections: GetConnectionsUseCase,
    private val getFavorites: GetFavoriteConnectionsUseCase,
    private val connectUseCase: ConnectUseCase,
    private val disconnectUseCase: DisconnectUseCase,
    private val deleteProfileUseCase: DeleteProfileUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionListUiState())
    val uiState: StateFlow<ConnectionListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getConnections(),
                getFavorites(),
            ) { profiles, favorites ->
                _uiState.value.copy(
                    profiles = profiles,
                    favorites = favorites,
                    isLoading = false,
                )
            }.catch { e ->
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to load profiles",
                        isLoading = false,
                    )
                }
            }.collect { state ->
                _uiState.update {
                    it.copy(
                        profiles = state.profiles,
                        favorites = state.favorites,
                        isLoading = state.isLoading,
                    )
                }
            }
        }
    }

    fun onEvent(event: ConnectionListEvent) {
        when (event) {
            is ConnectionListEvent.Connect -> connect(event.profileId)
            is ConnectionListEvent.Disconnect -> disconnect(event.profileId)
            is ConnectionListEvent.Delete -> delete(event)
            is ConnectionListEvent.ToggleFavorite -> toggleFavorite(event)
            is ConnectionListEvent.Search -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is ConnectionListEvent.ClearError -> {
                _uiState.update { it.copy(error = null) }
            }
        }
    }

    private fun connect(profileId: Long) {
        viewModelScope.launch {
            val result = connectUseCase(profileId)
            result.getAppError()?.let { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    private fun disconnect(profileId: Long) {
        viewModelScope.launch {
            try {
                disconnectUseCase(profileId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to disconnect")
                }
            }
        }
    }

    private fun delete(event: ConnectionListEvent.Delete) {
        viewModelScope.launch {
            try {
                deleteProfileUseCase(event.profile)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete profile")
                }
            }
        }
    }

    private fun toggleFavorite(event: ConnectionListEvent.ToggleFavorite) {
        viewModelScope.launch {
            val updated = event.profile.copy(isFavorite = !event.profile.isFavorite)
            val result = saveProfileUseCase(updated)
            result.getAppError()?.let { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }
}
