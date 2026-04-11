package dev.ori.feature.connections.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.result.getAppError
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.usecase.SaveProfileUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditFormState(
    val name: String = "",
    val host: String = "",
    val port: String = Protocol.SSH.defaultPort.toString(),
    val protocol: Protocol = Protocol.SSH,
    val username: String = "",
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    val credential: String = "",
    val startupCommand: String = "",
    val projectDirectory: String = "",
    val isAdvancedExpanded: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val nameError: String? = null,
    val hostError: String? = null,
    val portError: String? = null,
    val usernameError: String? = null,
    val credentialError: String? = null,
    val isEditMode: Boolean = false,
    val profileId: Long = 0,
) {
    val title: String get() = if (isEditMode) "Edit Connection" else "Add Connection"
}

sealed class AddEditEvent {
    data class NameChanged(val value: String) : AddEditEvent()
    data class HostChanged(val value: String) : AddEditEvent()
    data class PortChanged(val value: String) : AddEditEvent()
    data class ProtocolChanged(val value: Protocol) : AddEditEvent()
    data class UsernameChanged(val value: String) : AddEditEvent()
    data class AuthMethodChanged(val value: AuthMethod) : AddEditEvent()
    data class CredentialChanged(val value: String) : AddEditEvent()
    data class StartupCommandChanged(val value: String) : AddEditEvent()
    data class ProjectDirectoryChanged(val value: String) : AddEditEvent()
    data object ToggleAdvanced : AddEditEvent()
    data object Save : AddEditEvent()
}

sealed class AddEditEffect {
    data object NavigateBack : AddEditEffect()
    data class ShowError(val message: String) : AddEditEffect()
}

@HiltViewModel
class AddEditConnectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {

    private val profileId: Long = savedStateHandle["profileId"] ?: 0L

    private val _formState = MutableStateFlow(AddEditFormState())
    val formState: StateFlow<AddEditFormState> = _formState.asStateFlow()

    private val _effect = MutableSharedFlow<AddEditEffect>()
    val effect: SharedFlow<AddEditEffect> = _effect.asSharedFlow()

    init {
        if (profileId != 0L) {
            loadProfile(profileId)
        }
    }

    private fun loadProfile(id: Long) {
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                val profile = connectionRepository.getProfileById(id)
                if (profile != null) {
                    _formState.update {
                        it.copy(
                            name = profile.name,
                            host = profile.host,
                            port = profile.port.toString(),
                            protocol = profile.protocol,
                            username = profile.username,
                            authMethod = profile.authMethod,
                            credential = profile.credentialRef,
                            startupCommand = profile.startupCommand ?: "",
                            projectDirectory = profile.projectDirectory ?: "",
                            isEditMode = true,
                            profileId = profile.id,
                            isLoading = false,
                        )
                    }
                } else {
                    _formState.update { it.copy(isLoading = false) }
                    _effect.emit(AddEditEffect.ShowError("Profile not found"))
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false) }
                _effect.emit(AddEditEffect.ShowError("Failed to load profile: ${e.message}"))
            }
        }
    }

    fun onEvent(event: AddEditEvent) {
        when (event) {
            is AddEditEvent.NameChanged -> _formState.update {
                it.copy(name = event.value, nameError = null)
            }
            is AddEditEvent.HostChanged -> _formState.update {
                it.copy(host = event.value, hostError = null)
            }
            is AddEditEvent.PortChanged -> _formState.update {
                it.copy(port = event.value.filter { c -> c.isDigit() }, portError = null)
            }
            is AddEditEvent.ProtocolChanged -> _formState.update {
                it.copy(
                    protocol = event.value,
                    port = event.value.defaultPort.toString(),
                )
            }
            is AddEditEvent.UsernameChanged -> _formState.update {
                it.copy(username = event.value, usernameError = null)
            }
            is AddEditEvent.AuthMethodChanged -> _formState.update {
                it.copy(authMethod = event.value, credential = "", credentialError = null)
            }
            is AddEditEvent.CredentialChanged -> _formState.update {
                it.copy(credential = event.value, credentialError = null)
            }
            is AddEditEvent.StartupCommandChanged -> _formState.update {
                it.copy(startupCommand = event.value)
            }
            is AddEditEvent.ProjectDirectoryChanged -> _formState.update {
                it.copy(projectDirectory = event.value)
            }
            is AddEditEvent.ToggleAdvanced -> _formState.update {
                it.copy(isAdvancedExpanded = !it.isAdvancedExpanded)
            }
            is AddEditEvent.Save -> save()
        }
    }

    private fun save() {
        val state = _formState.value
        var hasError = false

        if (state.name.isBlank()) {
            _formState.update { it.copy(nameError = "Name is required") }
            hasError = true
        }
        if (state.host.isBlank()) {
            _formState.update { it.copy(hostError = "Host is required") }
            hasError = true
        }
        val port = state.port.toIntOrNull()
        if (port == null || port !in 1..65535) {
            _formState.update { it.copy(portError = "Port must be 1-65535") }
            hasError = true
        }
        if (state.username.isBlank()) {
            _formState.update { it.copy(usernameError = "Username is required") }
            hasError = true
        }
        if (state.credential.isBlank()) {
            val label = if (state.authMethod == AuthMethod.SSH_KEY) "SSH key path" else "Password"
            _formState.update { it.copy(credentialError = "$label is required") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }

            val profile = ServerProfile(
                id = state.profileId,
                name = state.name.trim(),
                host = state.host.trim(),
                port = port!!,
                protocol = state.protocol,
                username = state.username.trim(),
                authMethod = state.authMethod,
                credentialRef = state.credential,
                startupCommand = state.startupCommand.ifBlank { null },
                projectDirectory = state.projectDirectory.ifBlank { null },
            )

            val result = saveProfileUseCase(profile)
            result.getAppError()?.let { error ->
                _formState.update { it.copy(isSaving = false) }
                _effect.emit(AddEditEffect.ShowError(error.message))
                return@launch
            }

            _formState.update { it.copy(isSaving = false) }
            _effect.emit(AddEditEffect.NavigateBack)
        }
    }
}
