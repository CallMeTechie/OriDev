package dev.ori.feature.connections.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.result.getAppError
import dev.ori.core.security.crash.NonFatalErrorLogger
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.CredentialStore
import dev.ori.domain.usecase.CheckPremiumUseCase
import dev.ori.domain.usecase.SaveProfileUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
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
    val maxBandwidthKbps: Int? = null,
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
    val hasExistingPassword: Boolean = false,
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
    data class MaxBandwidthKbpsChanged(val kbps: Int?) : AddEditEvent()
    data object ToggleAdvanced : AddEditEvent()
    data object Save : AddEditEvent()
}

sealed class AddEditEffect {
    data object NavigateBack : AddEditEffect()
    data class ShowError(val message: String) : AddEditEffect()
}

/**
 * ViewModel for the Add/Edit Connection form.
 *
 * ## Credential handling (Phase 11 Security-S1, T2d partial mitigation)
 *
 * The credential field is held as a [String] in [AddEditFormState] because
 * Jetpack Compose's [androidx.compose.material3.OutlinedTextField] and the
 * [dev.ori.core.ui.components.OriInput] primitive are both backed by
 * [String]-typed state (`value: String, onValueChange: (String) -> Unit`).
 * Switching to a `CharArray`-backed buffer would require migrating the UI
 * to the Compose 1.7+ `TextFieldState` API — tracked as a follow-up.
 *
 * As a partial mitigation, [onCleared] explicitly blanks the credential
 * field so the String state isn't retained beyond the screen lifetime.
 * **Limitation:** the JVM String pool may still hold the typed characters
 * until GC; this is unavoidable with the current Compose text-field API.
 * The real zero-fill happens downstream in
 * `SshClientImpl.connect(..., password = chars)` (P11-S1), which reads the
 * String once, copies into a `CharArray`, and zero-fills in a `finally`
 * block.
 */
@HiltViewModel
class AddEditConnectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val connectionRepository: ConnectionRepository,
    private val credentialStore: CredentialStore,
    checkPremiumUseCase: CheckPremiumUseCase,
) : ViewModel() {

    private val profileId: Long = savedStateHandle["profileId"] ?: 0L

    /**
     * The alias under which an existing profile's password is already
     * persisted in [CredentialStore] (e.g. `kref_2c7…`). Captured when an
     * edit loads a profile so `save()` can either (a) reuse the alias and
     * overwrite the stored bytes when the user types a new password, or
     * (b) keep the existing entry untouched when the password field is
     * left blank. Null for new profiles and for legacy/unmanaged refs.
     */
    private var existingManagedAlias: String? = null

    private val _formState = MutableStateFlow(AddEditFormState())
    val formState: StateFlow<AddEditFormState> = _formState.asStateFlow()

    val isPremium: StateFlow<Boolean> = checkPremiumUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    private val _effect = MutableSharedFlow<AddEditEffect>()
    val effect: SharedFlow<AddEditEffect> = _effect.asSharedFlow()

    init {
        if (profileId != 0L) {
            loadProfile(profileId)
        }
    }

    override fun onCleared() {
        // Phase 11 T2d — clear credential on VM death so the String-backed
        // state isn't retained past screen lifetime. See class KDoc for the
        // JVM String-pool limitation.
        _formState.update { it.copy(credential = "") }
        super.onCleared()
    }

    private fun loadProfile(id: Long) {
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                val profile = connectionRepository.getProfileById(id)
                if (profile != null) {
                    // For PASSWORD auth: decrypt from the Keystore and
                    // prefill the masked field. The text-field already
                    // hides the value behind `PasswordVisualTransformation`
                    // with an eye toggle, so showing the plaintext in the
                    // VM state is no worse than what the SSH client does
                    // during a connect. Not prefilling was unnecessarily
                    // confusing: users saw an empty field and assumed the
                    // password was gone. Legacy plaintext in `credentialRef`
                    // (pre-Keystore migration) is taken as-is.
                    val isManagedPasswordProfile = profile.authMethod == AuthMethod.PASSWORD &&
                        profile.credentialRef.startsWith(KEYSTORE_ALIAS_PREFIX)
                    existingManagedAlias = if (isManagedPasswordProfile) profile.credentialRef else null
                    val initialCredentialField = when {
                        profile.authMethod != AuthMethod.PASSWORD -> profile.credentialRef
                        isManagedPasswordProfile -> decryptPasswordOrEmpty(profile.credentialRef)
                        else -> profile.credentialRef
                    }
                    _formState.update {
                        it.copy(
                            name = profile.name,
                            host = profile.host,
                            port = profile.port.toString(),
                            protocol = profile.protocol,
                            username = profile.username,
                            authMethod = profile.authMethod,
                            credential = initialCredentialField,
                            startupCommand = profile.startupCommand ?: "",
                            projectDirectory = profile.projectDirectory ?: "",
                            maxBandwidthKbps = profile.maxBandwidthKbps,
                            isEditMode = true,
                            profileId = profile.id,
                            isLoading = false,
                            hasExistingPassword = isManagedPasswordProfile,
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
            is AddEditEvent.MaxBandwidthKbpsChanged -> _formState.update {
                it.copy(maxBandwidthKbps = event.kbps)
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
        // Password / key path is always required — the edit form prefills
        // the decrypted password on load, so a blank field at save time
        // means the user actively cleared it. Treat that as a validation
        // error rather than a silent "keep existing" signal: the user
        // sees immediate feedback instead of an update that strips the
        // credential.
        if (state.credential.isBlank()) {
            val label = if (state.authMethod == AuthMethod.SSH_KEY) "SSH key path" else "Password"
            _formState.update { it.copy(credentialError = "$label is required") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }

            // For PASSWORD auth we encrypt via the Keystore-backed
            // CredentialStore and only persist the alias in Room. Before
            // this rewrite the UI stored the plaintext password inside
            // `ServerProfile.credentialRef` and no call to
            // `credentialStore.storePassword(...)` ever happened, so the
            // subsequent `connect()` lookup via `getPassword(ref)` always
            // returned null and the SSH client failed with "Either
            // password or private key must be provided".
            val resolvedCredentialRef = when (state.authMethod) {
                AuthMethod.PASSWORD -> storePasswordAndResolveAlias(
                    plaintext = state.credential,
                )
                AuthMethod.SSH_KEY,
                AuthMethod.KEY_AGENT,
                -> state.credential
            }

            val profile = ServerProfile(
                id = state.profileId,
                name = state.name.trim(),
                host = state.host.trim(),
                port = port!!,
                protocol = state.protocol,
                username = state.username.trim(),
                authMethod = state.authMethod,
                credentialRef = resolvedCredentialRef,
                startupCommand = state.startupCommand.ifBlank { null },
                projectDirectory = state.projectDirectory.ifBlank { null },
                maxBandwidthKbps = state.maxBandwidthKbps,
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

    /**
     * Persist the typed password via the Keystore-backed [CredentialStore]
     * and return the alias to store in [ServerProfile.credentialRef]. The
     * alias is reused across edits of the same profile so the Keystore
     * entry is overwritten in place rather than leaking stale blobs.
     *
     * The edit form prefills the decrypted password into the text field,
     * so every save carries the full intended value — no "keep existing"
     * shortcut is needed.
     */
    private suspend fun storePasswordAndResolveAlias(plaintext: String): String {
        val alias = existingManagedAlias ?: "$KEYSTORE_ALIAS_PREFIX${UUID.randomUUID()}"
        val chars = plaintext.toCharArray()
        try {
            credentialStore.storePassword(alias, chars)
        } finally {
            // `storePassword` already wipes the CharArray after encrypting,
            // so this is defence-in-depth for the path where storePassword
            // throws before reaching its own zero-fill.
            chars.fill('\u0000')
        }
        existingManagedAlias = alias
        return alias
    }

    /**
     * Fetch a managed password from the Keystore so the edit form can
     * prefill it. Any failure (missing entry, crypto mismatch after a
     * factory reset, provider unavailable) is downgraded to an empty
     * string so the UI stays usable — the user can just retype. Errors
     * are forwarded to [NonFatalErrorLogger] so we never fail silently.
     */
    private suspend fun decryptPasswordOrEmpty(alias: String): String = try {
        credentialStore.getPassword(alias)?.let { chars ->
            try {
                String(chars)
            } finally {
                chars.fill('\u0000')
            }
        } ?: run {
            NonFatalErrorLogger.log(
                category = "credential-miss",
                throwable = IllegalStateException("Keystore has no entry for alias $alias"),
                contextNote = "profileId=$profileId",
            )
            ""
        }
    } catch (e: Exception) {
        NonFatalErrorLogger.log(
            category = "credential-decrypt",
            throwable = e,
            contextNote = "profileId=$profileId; alias=$alias",
        )
        ""
    }

    private companion object {
        /**
         * Prefix that identifies an alias this ViewModel minted. Lets us
         * distinguish managed aliases from legacy `credentialRef` strings
         * that still carry the raw plaintext password (pre-fix installs).
         */
        const val KEYSTORE_ALIAS_PREFIX = "kref_"
    }
}
