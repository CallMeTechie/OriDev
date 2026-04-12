package dev.ori.feature.proxmox.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.model.ProxmoxVmStatus
import dev.ori.domain.repository.ProxmoxRepository
import dev.ori.domain.usecase.AddProxmoxNodeUseCase
import dev.ori.domain.usecase.DeleteProxmoxNodeUseCase
import dev.ori.domain.usecase.DeleteVmUseCase
import dev.ori.domain.usecase.GetProxmoxNodesUseCase
import dev.ori.domain.usecase.GetProxmoxVmsUseCase
import dev.ori.domain.usecase.ProbeCertificateUseCase
import dev.ori.domain.usecase.RestartVmUseCase
import dev.ori.domain.usecase.StartVmUseCase
import dev.ori.domain.usecase.StopVmUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class ProxmoxDashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepo: FakeProxmoxRepository

    private val node1 = ProxmoxNode(
        id = 1L,
        name = "pve1",
        host = "10.0.0.1",
        port = 8006,
        tokenId = "root@pam!ori",
        tokenSecretRef = "cred_1",
        certFingerprint = "AA:BB",
        isOnline = true,
    )
    private val node2 = ProxmoxNode(
        id = 2L,
        name = "pve2",
        host = "10.0.0.2",
        port = 8006,
        tokenId = "root@pam!ori",
        tokenSecretRef = "cred_2",
        certFingerprint = "CC:DD",
    )
    private val vm1 = ProxmoxVm(
        vmid = 100,
        name = "web",
        nodeName = "pve1",
        status = ProxmoxVmStatus.RUNNING,
    )
    private val vm2 = ProxmoxVm(
        vmid = 101,
        name = "db",
        nodeName = "pve1",
        status = ProxmoxVmStatus.STOPPED,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeProxmoxRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProxmoxDashboardViewModel = ProxmoxDashboardViewModel(
        getProxmoxNodes = GetProxmoxNodesUseCase(fakeRepo),
        getProxmoxVms = GetProxmoxVmsUseCase(fakeRepo),
        startVmUseCase = StartVmUseCase(fakeRepo),
        stopVmUseCase = StopVmUseCase(fakeRepo),
        restartVmUseCase = RestartVmUseCase(fakeRepo),
        deleteVmUseCase = DeleteVmUseCase(fakeRepo),
        probeCertificateUseCase = ProbeCertificateUseCase(fakeRepo),
        addProxmoxNodeUseCase = AddProxmoxNodeUseCase(fakeRepo),
        deleteProxmoxNodeUseCase = DeleteProxmoxNodeUseCase(fakeRepo),
    )

    @Test
    fun `init loads nodes from flow and selects first`() = runTest {
        fakeRepo.nodesFlow.value = listOf(node1, node2)
        fakeRepo.vmsResult = appSuccess(listOf(vm1, vm2))

        val vm = createViewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.nodes).containsExactly(node1, node2).inOrder()
            assertThat(state.selectedNodeId).isEqualTo(1L)
            assertThat(state.isLoading).isFalse()
        }
    }

    @Test
    fun `selectNode loads vms for selected node`() = runTest {
        fakeRepo.nodesFlow.value = listOf(node1, node2)
        fakeRepo.vmsResult = appSuccess(listOf(vm1))

        val vm = createViewModel()
        fakeRepo.vmsResult = appSuccess(listOf(vm2))
        vm.onEvent(ProxmoxEvent.SelectNode(2L))

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedNodeId).isEqualTo(2L)
            assertThat(state.vms).containsExactly(vm2)
        }
    }

    @Test
    fun `probeAndAddNode success shows cert dialog`() = runTest {
        fakeRepo.nodesFlow.value = emptyList()
        fakeRepo.probeResult = appSuccess("FINGERPRINT")

        val vm = createViewModel()
        val pending = AddNodePending(
            name = "pve-new",
            host = "10.0.0.9",
            port = 8006,
            tokenId = "root@pam!ori",
            tokenSecret = "secret",
        )
        vm.onEvent(ProxmoxEvent.ProbeAndAddNode(pending))

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.showCertificateDialog).isNotNull()
            assertThat(state.showCertificateDialog?.fingerprint).isEqualTo("FINGERPRINT")
            assertThat(state.showAddNodeSheet).isFalse()
        }
    }

    @Test
    fun `confirmTrustCertificate calls addNode`() = runTest {
        fakeRepo.nodesFlow.value = emptyList()
        fakeRepo.addNodeResult = appSuccess(42L)

        val vm = createViewModel()
        val request = CertificateTrustRequest(
            host = "10.0.0.9",
            port = 8006,
            fingerprint = "FP",
            pendingAddData = AddNodePending(
                name = "pve-new",
                host = "10.0.0.9",
                port = 8006,
                tokenId = "root@pam!ori",
                tokenSecret = "secret",
            ),
        )
        vm.onEvent(ProxmoxEvent.ConfirmTrustCertificate(request))

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.showCertificateDialog).isNull()
            assertThat(state.showAddNodeSheet).isFalse()
        }
        assertThat(fakeRepo.addNodeCalled).isTrue()
        assertThat(fakeRepo.lastAddNodeFingerprint).isEqualTo("FP")
    }

    @Test
    fun `startVm calls use case and refreshes`() = runTest {
        fakeRepo.nodesFlow.value = listOf(node1)
        fakeRepo.vmsResult = appSuccess(listOf(vm2))

        val vm = createViewModel()
        fakeRepo.startVmResult = appSuccess("UPID:task")
        fakeRepo.vmsResult = appSuccess(listOf(vm2.copy(status = ProxmoxVmStatus.RUNNING)))
        vm.onEvent(ProxmoxEvent.StartVm(1L, 101))

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.vmActionInProgress).isNull()
            assertThat(state.vms.first().status).isEqualTo(ProxmoxVmStatus.RUNNING)
        }
        assertThat(fakeRepo.startVmCalled).isTrue()
    }

    @Test
    fun `stopVm failure sets error`() = runTest {
        fakeRepo.nodesFlow.value = listOf(node1)
        fakeRepo.vmsResult = appSuccess(listOf(vm1))

        val vm = createViewModel()
        fakeRepo.stopVmResult = appFailure(AppError.NetworkError("boom"))
        vm.onEvent(ProxmoxEvent.StopVm(1L, 100))

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.vmActionInProgress).isNull()
            assertThat(state.error).isEqualTo("boom")
        }
    }

    @Test
    fun `refreshVms reloads list`() = runTest {
        fakeRepo.nodesFlow.value = listOf(node1)
        fakeRepo.vmsResult = appSuccess(listOf(vm1))

        val vm = createViewModel()
        fakeRepo.vmsResult = appSuccess(listOf(vm1, vm2))
        vm.onEvent(ProxmoxEvent.RefreshVms)

        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.vms).containsExactly(vm1, vm2)
        }
    }

    @Test
    fun `deleteNode calls use case`() = runTest {
        fakeRepo.nodesFlow.value = listOf(node1)
        fakeRepo.vmsResult = appSuccess(emptyList())

        val vm = createViewModel()
        vm.onEvent(ProxmoxEvent.DeleteNode(node1))

        assertThat(fakeRepo.deleteNodeCalled).isTrue()
        assertThat(fakeRepo.lastDeletedNode).isEqualTo(node1)
    }
}

@Suppress("TooManyFunctions")
private class FakeProxmoxRepository : ProxmoxRepository {
    val nodesFlow = MutableStateFlow<List<ProxmoxNode>>(emptyList())

    var vmsResult: AppResult<List<ProxmoxVm>> = appSuccess(emptyList())
    var probeResult: AppResult<String> = appSuccess("")
    var addNodeResult: AppResult<Long> = appSuccess(0L)
    var startVmResult: AppResult<String> = appSuccess("")
    var stopVmResult: AppResult<String> = appSuccess("")
    var restartVmResult: AppResult<String> = appSuccess("")
    var deleteVmResult: AppResult<String> = appSuccess("")

    var addNodeCalled = false
    var lastAddNodeFingerprint: String? = null
    var deleteNodeCalled = false
    var lastDeletedNode: ProxmoxNode? = null
    var startVmCalled = false

    override fun getNodes(): Flow<List<ProxmoxNode>> = nodesFlow

    override suspend fun addNode(
        name: String,
        host: String,
        port: Int,
        tokenId: String,
        tokenSecret: String,
        certFingerprint: String,
    ): AppResult<Long> {
        addNodeCalled = true
        lastAddNodeFingerprint = certFingerprint
        return addNodeResult
    }

    override suspend fun updateNode(node: ProxmoxNode) = Unit

    override suspend fun deleteNode(node: ProxmoxNode) {
        deleteNodeCalled = true
        lastDeletedNode = node
    }

    override suspend fun probeCertificate(host: String, port: Int): AppResult<String> = probeResult

    override suspend fun refreshNodeStatus(nodeId: Long): AppResult<ProxmoxNode> =
        appFailure(AppError.NetworkError("not used"))

    override suspend fun getVms(nodeId: Long): AppResult<List<ProxmoxVm>> = vmsResult

    override suspend fun getTemplates(nodeId: Long): AppResult<List<ProxmoxVm>> =
        appSuccess(emptyList())

    override suspend fun startVm(nodeId: Long, vmid: Int): AppResult<String> {
        startVmCalled = true
        return startVmResult
    }

    override suspend fun stopVm(nodeId: Long, vmid: Int): AppResult<String> = stopVmResult

    override suspend fun restartVm(nodeId: Long, vmid: Int): AppResult<String> = restartVmResult

    override suspend fun deleteVm(nodeId: Long, vmid: Int): AppResult<String> = deleteVmResult

    override suspend fun cloneVm(
        nodeId: Long,
        templateVmid: Int,
        newVmid: Int,
        newName: String,
        fullClone: Boolean,
    ): AppResult<String> = appSuccess("")

    override suspend fun waitForTask(nodeId: Long, upid: String, timeoutSeconds: Long): AppResult<Unit> =
        appSuccess(Unit)

    override suspend fun pollVmSshReady(
        nodeId: Long,
        vmid: Int,
        timeoutSeconds: Long,
    ): AppResult<String> = appSuccess("")
}
