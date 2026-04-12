package dev.ori.feature.proxmox.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.model.Connection
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.model.ProxmoxVmStatus
import dev.ori.domain.model.ServerProfile
import dev.ori.domain.repository.ConnectionRepository
import dev.ori.domain.repository.CredentialStore
import dev.ori.domain.repository.ProxmoxRepository
import dev.ori.domain.usecase.CloneVmUseCase
import dev.ori.domain.usecase.GetProxmoxTemplatesUseCase
import dev.ori.domain.usecase.PollVmSshUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class CreateVmWizardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepo: FakeProxmoxRepoForWizard
    private lateinit var fakeConnRepo: FakeConnectionRepository
    private lateinit var fakeCredStore: FakeCredentialStore

    private val template1 = ProxmoxVm(
        vmid = 9000,
        name = "ubuntu-template",
        nodeName = "pve1",
        status = ProxmoxVmStatus.STOPPED,
        isTemplate = true,
    )
    private val template2 = ProxmoxVm(
        vmid = 9001,
        name = "debian-template",
        nodeName = "pve1",
        status = ProxmoxVmStatus.STOPPED,
        isTemplate = true,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeProxmoxRepoForWizard()
        fakeConnRepo = FakeConnectionRepository()
        fakeCredStore = FakeCredentialStore()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(nodeId: Long = 1L): CreateVmWizardViewModel =
        CreateVmWizardViewModel(
            savedStateHandle = SavedStateHandle(mapOf("nodeId" to nodeId)),
            getTemplatesUseCase = GetProxmoxTemplatesUseCase(fakeRepo),
            cloneVmUseCase = CloneVmUseCase(fakeRepo),
            pollVmSshUseCase = PollVmSshUseCase(fakeRepo),
            proxmoxRepository = fakeRepo,
            connectionRepository = fakeConnRepo,
            credentialStore = fakeCredStore,
        )

    @Test
    fun `init with nodeId loads templates`() = runTest {
        fakeRepo.templatesResult = appSuccess(listOf(template1, template2))

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.nodeId).isEqualTo(1L)
            assertThat(state.templates).containsExactly(template1, template2).inOrder()
            assertThat(state.step).isEqualTo(WizardStep.SELECT_TEMPLATE)
        }
    }

    @Test
    fun `selectTemplate advancesToConfigure`() = runTest {
        fakeRepo.templatesResult = appSuccess(listOf(template1))

        val vm = createViewModel()
        vm.onEvent(CreateVmWizardEvent.SelectTemplate(template1))
        vm.onEvent(CreateVmWizardEvent.NextStep)

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedTemplate).isEqualTo(template1)
            assertThat(state.step).isEqualTo(WizardStep.CONFIGURE)
        }
    }

    @Test
    fun `configure to network preserves fields`() = runTest {
        fakeRepo.templatesResult = appSuccess(listOf(template1))

        val vm = createViewModel()
        vm.onEvent(CreateVmWizardEvent.SelectTemplate(template1))
        vm.onEvent(CreateVmWizardEvent.NextStep) // -> CONFIGURE
        vm.onEvent(
            CreateVmWizardEvent.UpdateConfig(
                newVmid = 150,
                newName = "test-vm",
                fullClone = false,
            ),
        )
        vm.onEvent(CreateVmWizardEvent.NextStep) // -> NETWORK

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.step).isEqualTo(WizardStep.NETWORK)
            assertThat(state.newVmid).isEqualTo(150)
            assertThat(state.newName).isEqualTo("test-vm")
            assertThat(state.fullClone).isFalse()
        }
    }

    @Test
    fun `network to review preserves credentials`() = runTest {
        fakeRepo.templatesResult = appSuccess(listOf(template1))

        val vm = createViewModel()
        vm.onEvent(CreateVmWizardEvent.SelectTemplate(template1))
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(
            CreateVmWizardEvent.UpdateConfig(newVmid = 150, newName = "test-vm"),
        )
        vm.onEvent(CreateVmWizardEvent.NextStep) // -> NETWORK
        vm.onEvent(
            CreateVmWizardEvent.UpdateNetwork(
                sshUsername = "admin",
                sshPassword = "secret",
                bridge = "vmbr1",
            ),
        )
        vm.onEvent(CreateVmWizardEvent.NextStep) // -> REVIEW

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.step).isEqualTo(WizardStep.REVIEW)
            assertThat(state.sshUsername).isEqualTo("admin")
            assertThat(state.sshPassword).isEqualTo("secret")
            assertThat(state.bridge).isEqualTo("vmbr1")
        }
    }

    @Test
    fun `cloneAndStart success advances through states`() = runTest {
        fakeRepo.templatesResult = appSuccess(listOf(template1))
        fakeRepo.cloneResult = appSuccess("UPID:node:task:1")
        fakeRepo.waitTaskResult = appSuccess(Unit)
        fakeRepo.pollSshResult = appSuccess("10.0.0.42")
        fakeConnRepo.nextSavedId = 77L

        val vm = createViewModel()
        vm.onEvent(CreateVmWizardEvent.SelectTemplate(template1))
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(
            CreateVmWizardEvent.UpdateConfig(newVmid = 200, newName = "fresh"),
        )
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(
            CreateVmWizardEvent.UpdateNetwork(
                sshUsername = "root",
                sshPassword = "pw",
            ),
        )
        vm.onEvent(CreateVmWizardEvent.NextStep) // REVIEW
        vm.onEvent(CreateVmWizardEvent.CloneAndStart)

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.step).isEqualTo(WizardStep.DONE)
            assertThat(state.resultSshProfileId).isEqualTo(77L)
            assertThat(state.warningMessage).isNull()
            assertThat(state.error).isNull()
        }
        assertThat(fakeCredStore.stored["proxmox_vm_200"]).isEqualTo("pw")
        val saved = fakeConnRepo.savedProfile
        assertThat(saved).isNotNull()
        assertThat(saved?.host).isEqualTo("10.0.0.42")
        assertThat(saved?.port).isEqualTo(22)
    }

    @Test
    fun `cloneAndStart taskTimeout sets error`() = runTest {
        fakeRepo.templatesResult = appSuccess(listOf(template1))
        fakeRepo.cloneResult = appSuccess("UPID:node:task:1")
        fakeRepo.waitTaskResult = appFailure(AppError.NetworkError("timeout"))

        val vm = createViewModel()
        vm.onEvent(CreateVmWizardEvent.SelectTemplate(template1))
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(CreateVmWizardEvent.UpdateConfig(newVmid = 201, newName = "fail-vm"))
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(
            CreateVmWizardEvent.UpdateNetwork(sshUsername = "root", sshPassword = "pw"),
        )
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(CreateVmWizardEvent.CloneAndStart)

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.error).isEqualTo("timeout")
            assertThat(state.step).isEqualTo(WizardStep.REVIEW)
        }
    }

    @Test
    fun `cloneAndStart sshPollTimeout shows warningMessage but reaches DONE`() = runTest {
        fakeRepo.templatesResult = appSuccess(listOf(template1))
        fakeRepo.cloneResult = appSuccess("UPID:node:task:1")
        fakeRepo.waitTaskResult = appSuccess(Unit)
        fakeRepo.pollSshResult = appFailure(AppError.NetworkError("ssh unreachable"))

        val vm = createViewModel()
        vm.onEvent(CreateVmWizardEvent.SelectTemplate(template1))
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(CreateVmWizardEvent.UpdateConfig(newVmid = 202, newName = "warn-vm"))
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(
            CreateVmWizardEvent.UpdateNetwork(sshUsername = "root", sshPassword = "pw"),
        )
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(CreateVmWizardEvent.CloneAndStart)

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.step).isEqualTo(WizardStep.DONE)
            assertThat(state.warningMessage).isNotNull()
            assertThat(state.warningMessage).contains("SSH not reachable")
            assertThat(state.resultSshProfileId).isNull()
        }
    }

    @Test
    fun `cloneAndStart withStaticIp skips guest agent query`() = runTest {
        fakeRepo.templatesResult = appSuccess(listOf(template1))
        fakeRepo.cloneResult = appSuccess("UPID:node:task:1")
        fakeRepo.waitTaskResult = appSuccess(Unit)
        // pollSshResult is never consulted because useStaticIp = true
        fakeRepo.pollSshResult = appFailure(AppError.NetworkError("should-not-be-called"))
        fakeConnRepo.nextSavedId = 88L

        val vm = createViewModel()
        vm.onEvent(CreateVmWizardEvent.SelectTemplate(template1))
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(CreateVmWizardEvent.UpdateConfig(newVmid = 203, newName = "static-vm"))
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(
            CreateVmWizardEvent.UpdateNetwork(
                useStaticIp = true,
                staticIp = "192.168.1.50",
                sshUsername = "root",
                sshPassword = "pw",
            ),
        )
        vm.onEvent(CreateVmWizardEvent.NextStep)
        vm.onEvent(CreateVmWizardEvent.CloneAndStart)

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.step).isEqualTo(WizardStep.DONE)
            assertThat(state.resultSshProfileId).isEqualTo(88L)
            assertThat(state.warningMessage).isNull()
        }
        assertThat(fakeRepo.pollSshCalled).isFalse()
        assertThat(fakeConnRepo.savedProfile?.host).isEqualTo("192.168.1.50")
    }
}

@Suppress("TooManyFunctions")
private class FakeProxmoxRepoForWizard : ProxmoxRepository {
    var templatesResult: AppResult<List<ProxmoxVm>> = appSuccess(emptyList())
    var cloneResult: AppResult<String> = appSuccess("")
    var waitTaskResult: AppResult<Unit> = appSuccess(Unit)
    var pollSshResult: AppResult<String> = appSuccess("")

    var pollSshCalled = false

    override fun getNodes(): Flow<List<ProxmoxNode>> = MutableStateFlow(emptyList())

    override suspend fun addNode(
        name: String,
        host: String,
        port: Int,
        tokenId: String,
        tokenSecret: String,
        certFingerprint: String,
    ): AppResult<Long> = appSuccess(0L)

    override suspend fun updateNode(node: ProxmoxNode) = Unit

    override suspend fun deleteNode(node: ProxmoxNode) = Unit

    override suspend fun probeCertificate(host: String, port: Int): AppResult<String> =
        appSuccess("")

    override suspend fun refreshNodeStatus(nodeId: Long): AppResult<ProxmoxNode> =
        appFailure(AppError.NetworkError("n/a"))

    override suspend fun getVms(nodeId: Long): AppResult<List<ProxmoxVm>> = appSuccess(emptyList())

    override suspend fun getTemplates(nodeId: Long): AppResult<List<ProxmoxVm>> = templatesResult

    override suspend fun startVm(nodeId: Long, vmid: Int): AppResult<String> = appSuccess("")

    override suspend fun stopVm(nodeId: Long, vmid: Int): AppResult<String> = appSuccess("")

    override suspend fun restartVm(nodeId: Long, vmid: Int): AppResult<String> = appSuccess("")

    override suspend fun deleteVm(nodeId: Long, vmid: Int): AppResult<String> = appSuccess("")

    override suspend fun cloneVm(
        nodeId: Long,
        templateVmid: Int,
        newVmid: Int,
        newName: String,
        fullClone: Boolean,
    ): AppResult<String> = cloneResult

    override suspend fun waitForTask(
        nodeId: Long,
        upid: String,
        timeoutSeconds: Long,
    ): AppResult<Unit> = waitTaskResult

    override suspend fun pollVmSshReady(
        nodeId: Long,
        vmid: Int,
        timeoutSeconds: Long,
    ): AppResult<String> {
        pollSshCalled = true
        return pollSshResult
    }
}

private class FakeConnectionRepository : ConnectionRepository {
    var nextSavedId: Long = 1L
    var savedProfile: ServerProfile? = null

    override fun getAllProfiles(): Flow<List<ServerProfile>> = emptyFlow()
    override fun getFavoriteProfiles(): Flow<List<ServerProfile>> = emptyFlow()
    override suspend fun getProfileById(id: Long): ServerProfile? = null
    override suspend fun getProfileCount(): Int = 0
    override suspend fun saveProfile(profile: ServerProfile): Long {
        savedProfile = profile
        return nextSavedId
    }
    override suspend fun updateProfile(profile: ServerProfile) = Unit
    override suspend fun deleteProfile(profile: ServerProfile) = Unit
    override suspend fun connect(profileId: Long): Connection =
        error("connect not used in wizard tests")
    override suspend fun disconnect(profileId: Long) = Unit
    override fun getActiveConnections(): Flow<List<Connection>> = emptyFlow()
    override suspend fun getActiveSessionId(profileId: Long): String? = null
}

private class FakeCredentialStore : CredentialStore {
    val stored = mutableMapOf<String, String>()

    override suspend fun storePassword(alias: String, password: CharArray) {
        stored[alias] = String(password)
    }

    override suspend fun getPassword(alias: String): CharArray? = stored[alias]?.toCharArray()
    override suspend fun storeSshKey(alias: String, privateKey: ByteArray) = Unit
    override suspend fun getSshKey(alias: String): ByteArray? = null
    override suspend fun deleteCredential(alias: String) {
        stored.remove(alias)
    }
    override suspend fun hasCredential(alias: String): Boolean = stored.containsKey(alias)
}
