package dev.ori.feature.proxmox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.result.getAppError
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.CredentialStore
import dev.ori.domain.repository.ProxmoxRepository
import dev.ori.domain.usecase.CloneVmUseCase
import dev.ori.domain.usecase.GetProxmoxTemplatesUseCase
import dev.ori.domain.usecase.PollVmSshUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateVmWizardState(
    val nodeId: Long,
    val step: WizardStep = WizardStep.SELECT_TEMPLATE,
    val templates: List<ProxmoxVm> = emptyList(),
    val selectedTemplate: ProxmoxVm? = null,
    val newVmid: Int = DEFAULT_NEW_VMID,
    val newName: String = "",
    val fullClone: Boolean = true,
    val useStaticIp: Boolean = false,
    val staticIp: String = "",
    val gateway: String = "",
    val bridge: String = "vmbr0",
    val sshUsername: String = "root",
    val sshPassword: String = "",
    val cloneInProgress: Boolean = false,
    val autoConnectInProgress: Boolean = false,
    val resultSshProfileId: Long? = null,
    val warningMessage: String? = null,
    val error: String? = null,
)

const val DEFAULT_NEW_VMID = 100

enum class WizardStep { SELECT_TEMPLATE, CONFIGURE, NETWORK, REVIEW, CLONING, CONNECTING, DONE }

sealed class CreateVmWizardEvent {
    data object LoadTemplates : CreateVmWizardEvent()
    data class SelectTemplate(val template: ProxmoxVm) : CreateVmWizardEvent()
    data object NextStep : CreateVmWizardEvent()
    data object PreviousStep : CreateVmWizardEvent()
    data class UpdateConfig(
        val newVmid: Int? = null,
        val newName: String? = null,
        val fullClone: Boolean? = null,
    ) : CreateVmWizardEvent()
    data class UpdateNetwork(
        val bridge: String? = null,
        val useStaticIp: Boolean? = null,
        val staticIp: String? = null,
        val gateway: String? = null,
        val sshUsername: String? = null,
        val sshPassword: String? = null,
    ) : CreateVmWizardEvent()
    data object CloneAndStart : CreateVmWizardEvent()
    data object ClearError : CreateVmWizardEvent()
}

@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class CreateVmWizardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTemplatesUseCase: GetProxmoxTemplatesUseCase,
    private val cloneVmUseCase: CloneVmUseCase,
    private val pollVmSshUseCase: PollVmSshUseCase,
    private val proxmoxRepository: ProxmoxRepository,
    private val connectionRepository: ConnectionRepository,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    private val nodeId: Long = savedStateHandle.get<Long>("nodeId")
        ?: error("nodeId required")

    private val _uiState = MutableStateFlow(CreateVmWizardState(nodeId = nodeId))
    val uiState: StateFlow<CreateVmWizardState> = _uiState.asStateFlow()

    init {
        loadTemplates()
    }

    fun onEvent(event: CreateVmWizardEvent) {
        when (event) {
            CreateVmWizardEvent.LoadTemplates -> loadTemplates()
            is CreateVmWizardEvent.SelectTemplate ->
                _uiState.update { it.copy(selectedTemplate = event.template) }
            CreateVmWizardEvent.NextStep -> advanceStep()
            CreateVmWizardEvent.PreviousStep -> retreatStep()
            is CreateVmWizardEvent.UpdateConfig -> _uiState.update {
                it.copy(
                    newVmid = event.newVmid ?: it.newVmid,
                    newName = event.newName ?: it.newName,
                    fullClone = event.fullClone ?: it.fullClone,
                )
            }
            is CreateVmWizardEvent.UpdateNetwork -> _uiState.update {
                it.copy(
                    bridge = event.bridge ?: it.bridge,
                    useStaticIp = event.useStaticIp ?: it.useStaticIp,
                    staticIp = event.staticIp ?: it.staticIp,
                    gateway = event.gateway ?: it.gateway,
                    sshUsername = event.sshUsername ?: it.sshUsername,
                    sshPassword = event.sshPassword ?: it.sshPassword,
                )
            }
            CreateVmWizardEvent.CloneAndStart -> cloneAndStart()
            CreateVmWizardEvent.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun advanceStep() {
        _uiState.update { state ->
            val next = when (state.step) {
                WizardStep.SELECT_TEMPLATE -> WizardStep.CONFIGURE
                WizardStep.CONFIGURE -> WizardStep.NETWORK
                WizardStep.NETWORK -> WizardStep.REVIEW
                else -> state.step
            }
            state.copy(step = next)
        }
    }

    private fun retreatStep() {
        _uiState.update { state ->
            val prev = when (state.step) {
                WizardStep.CONFIGURE -> WizardStep.SELECT_TEMPLATE
                WizardStep.NETWORK -> WizardStep.CONFIGURE
                WizardStep.REVIEW -> WizardStep.NETWORK
                else -> state.step
            }
            state.copy(step = prev)
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            val result = getTemplatesUseCase(nodeId)
            result.fold(
                onSuccess = { templates ->
                    _uiState.update { it.copy(templates = templates) }
                },
                onFailure = {
                    val message = result.getAppError()?.message
                        ?: it.message
                        ?: "Failed to load templates"
                    _uiState.update { state -> state.copy(error = message) }
                },
            )
        }
    }

    @Suppress("LongMethod", "ReturnCount")
    private fun cloneAndStart() {
        viewModelScope.launch {
            val snapshot = _uiState.value
            val template = snapshot.selectedTemplate ?: return@launch
            _uiState.update {
                it.copy(
                    cloneInProgress = true,
                    step = WizardStep.CLONING,
                    error = null,
                    warningMessage = null,
                )
            }
            val cloneResult = cloneVmUseCase(
                nodeId = nodeId,
                templateVmid = template.vmid,
                newVmid = snapshot.newVmid,
                newName = snapshot.newName,
                fullClone = snapshot.fullClone,
            )
            val upid = cloneResult.getOrElse {
                val message = cloneResult.getAppError()?.message
                    ?: it.message
                    ?: "Clone failed"
                _uiState.update { state ->
                    state.copy(
                        cloneInProgress = false,
                        step = WizardStep.REVIEW,
                        error = message,
                    )
                }
                return@launch
            }
            val waitResult = proxmoxRepository.waitForTask(nodeId, upid)
            if (waitResult.isFailure) {
                val message = waitResult.getAppError()?.message
                    ?: "Task did not complete in time"
                _uiState.update { state ->
                    state.copy(
                        cloneInProgress = false,
                        step = WizardStep.REVIEW,
                        error = message,
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    cloneInProgress = false,
                    autoConnectInProgress = true,
                    step = WizardStep.CONNECTING,
                )
            }
            val ipResult: Result<String> = if (snapshot.useStaticIp) {
                Result.success(snapshot.staticIp)
            } else {
                pollVmSshUseCase(nodeId, snapshot.newVmid)
            }
            ipResult.fold(
                onSuccess = { vmIp ->
                    finalizeWithProfile(snapshot, vmIp)
                },
                onFailure = {
                    val message = ipResult.getAppError()?.message
                        ?: it.message
                        ?: "SSH not reachable within timeout"
                    _uiState.update { state ->
                        state.copy(
                            autoConnectInProgress = false,
                            step = WizardStep.DONE,
                            warningMessage = "VM cloned but SSH not reachable: $message. " +
                                "You can manually connect later via the Connection Manager.",
                        )
                    }
                },
            )
        }
    }

    private suspend fun finalizeWithProfile(snapshot: CreateVmWizardState, vmIp: String) {
        val alias = "proxmox_vm_${snapshot.newVmid}"
        credentialStore.storePassword(alias, snapshot.sshPassword.toCharArray())
        val profile = ServerProfile(
            name = snapshot.newName,
            host = vmIp,
            port = SSH_PORT,
            protocol = Protocol.SSH,
            username = snapshot.sshUsername,
            authMethod = AuthMethod.PASSWORD,
            credentialRef = alias,
            sshKeyType = null,
            startupCommand = null,
            projectDirectory = null,
            claudeCodeModel = null,
            claudeMdPath = null,
        )
        val profileId = connectionRepository.saveProfile(profile)
        _uiState.update {
            it.copy(
                autoConnectInProgress = false,
                resultSshProfileId = profileId,
                step = WizardStep.DONE,
            )
        }
    }

    companion object {
        const val SSH_PORT = 22
    }
}
