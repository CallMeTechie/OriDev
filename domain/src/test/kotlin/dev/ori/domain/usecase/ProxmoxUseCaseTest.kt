package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.model.ProxmoxNode
import dev.ori.domain.model.ProxmoxVm
import dev.ori.domain.model.ProxmoxVmStatus
import dev.ori.domain.repository.ProxmoxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ProxmoxUseCaseTest {

    private class FakeProxmoxRepository : ProxmoxRepository {
        var nodesFlow: Flow<List<ProxmoxNode>> = flowOf(emptyList())
        var nextVms: AppResult<List<ProxmoxVm>> = appSuccess(emptyList())
        var nextTemplates: AppResult<List<ProxmoxVm>> = appSuccess(emptyList())
        var nextAction: AppResult<String> = appSuccess("UPID:test")
        var nextPollIp: AppResult<String> = appSuccess("10.0.0.1")

        var lastNodeId: Long? = null
        var lastVmid: Int? = null
        var lastTemplateVmid: Int? = null
        var lastNewVmid: Int? = null
        var lastNewName: String? = null
        var lastFullClone: Boolean? = null
        var lastPollTimeout: Long? = null
        var lastMethod: String? = null

        override fun getNodes(): Flow<List<ProxmoxNode>> = nodesFlow
        override suspend fun addNode(
            name: String,
            host: String,
            port: Int,
            tokenId: String,
            tokenSecret: String,
            certFingerprint: String,
        ): AppResult<Long> = appSuccess(1L)
        override suspend fun updateNode(node: ProxmoxNode) = Unit
        override suspend fun deleteNode(node: ProxmoxNode) = Unit
        override suspend fun probeCertificate(host: String, port: Int): AppResult<String> = appSuccess("AA")
        override suspend fun refreshNodeStatus(nodeId: Long): AppResult<ProxmoxNode> =
            appSuccess(
                ProxmoxNode(
                    id = nodeId,
                    name = "n",
                    host = "h",
                    port = 8006,
                    tokenId = "t",
                    tokenSecretRef = "r",
                    certFingerprint = "f",
                ),
            )

        override suspend fun getVms(nodeId: Long): AppResult<List<ProxmoxVm>> {
            lastNodeId = nodeId
            lastMethod = "getVms"
            return nextVms
        }

        override suspend fun getTemplates(nodeId: Long): AppResult<List<ProxmoxVm>> {
            lastNodeId = nodeId
            lastMethod = "getTemplates"
            return nextTemplates
        }

        override suspend fun startVm(nodeId: Long, vmid: Int): AppResult<String> {
            lastNodeId = nodeId
            lastVmid = vmid
            lastMethod = "startVm"
            return nextAction
        }

        override suspend fun stopVm(nodeId: Long, vmid: Int): AppResult<String> {
            lastNodeId = nodeId
            lastVmid = vmid
            lastMethod = "stopVm"
            return nextAction
        }

        override suspend fun restartVm(nodeId: Long, vmid: Int): AppResult<String> {
            lastNodeId = nodeId
            lastVmid = vmid
            lastMethod = "restartVm"
            return nextAction
        }

        override suspend fun deleteVm(nodeId: Long, vmid: Int): AppResult<String> {
            lastNodeId = nodeId
            lastVmid = vmid
            lastMethod = "deleteVm"
            return nextAction
        }

        override suspend fun cloneVm(
            nodeId: Long,
            templateVmid: Int,
            newVmid: Int,
            newName: String,
            fullClone: Boolean,
        ): AppResult<String> {
            lastNodeId = nodeId
            lastTemplateVmid = templateVmid
            lastNewVmid = newVmid
            lastNewName = newName
            lastFullClone = fullClone
            lastMethod = "cloneVm"
            return nextAction
        }

        override suspend fun waitForTask(nodeId: Long, upid: String, timeoutSeconds: Long): AppResult<Unit> =
            appSuccess(Unit)

        override suspend fun pollVmSshReady(nodeId: Long, vmid: Int, timeoutSeconds: Long): AppResult<String> {
            lastNodeId = nodeId
            lastVmid = vmid
            lastPollTimeout = timeoutSeconds
            lastMethod = "pollVmSshReady"
            return nextPollIp
        }
    }

    private fun sampleNode(id: Long = 1L) = ProxmoxNode(
        id = id,
        name = "n",
        host = "h",
        port = 8006,
        tokenId = "t",
        tokenSecretRef = "r",
        certFingerprint = "f",
    )

    private fun sampleVm(vmid: Int = 100, isTemplate: Boolean = false) = ProxmoxVm(
        vmid = vmid,
        name = "vm-$vmid",
        nodeName = "pve1",
        status = ProxmoxVmStatus.RUNNING,
        isTemplate = isTemplate,
    )

    @Test
    fun `GetProxmoxNodesUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository().apply { nodesFlow = flowOf(listOf(sampleNode())) }
        val useCase = GetProxmoxNodesUseCase(repo)

        useCase().collect { list ->
            assertThat(list).hasSize(1)
        }
    }

    @Test
    fun `GetProxmoxVmsUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository().apply { nextVms = appSuccess(listOf(sampleVm())) }
        val useCase = GetProxmoxVmsUseCase(repo)

        val result = useCase(nodeId = 42L)

        assertThat(result.isSuccess).isTrue()
        assertThat(repo.lastNodeId).isEqualTo(42L)
        assertThat(repo.lastMethod).isEqualTo("getVms")
    }

    @Test
    fun `StartVmUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository()
        val result = StartVmUseCase(repo)(nodeId = 1L, vmid = 100)

        assertThat(result.getOrNull()).isEqualTo("UPID:test")
        assertThat(repo.lastMethod).isEqualTo("startVm")
        assertThat(repo.lastVmid).isEqualTo(100)
    }

    @Test
    fun `StopVmUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository()
        StopVmUseCase(repo)(nodeId = 1L, vmid = 200)

        assertThat(repo.lastMethod).isEqualTo("stopVm")
        assertThat(repo.lastVmid).isEqualTo(200)
    }

    @Test
    fun `RestartVmUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository()
        RestartVmUseCase(repo)(nodeId = 1L, vmid = 300)

        assertThat(repo.lastMethod).isEqualTo("restartVm")
        assertThat(repo.lastVmid).isEqualTo(300)
    }

    @Test
    fun `DeleteVmUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository()
        DeleteVmUseCase(repo)(nodeId = 1L, vmid = 400)

        assertThat(repo.lastMethod).isEqualTo("deleteVm")
        assertThat(repo.lastVmid).isEqualTo(400)
    }

    @Test
    fun `GetProxmoxTemplatesUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository().apply {
            nextTemplates = appSuccess(listOf(sampleVm(vmid = 9000, isTemplate = true)))
        }
        val result = GetProxmoxTemplatesUseCase(repo)(nodeId = 1L)

        assertThat(result.isSuccess).isTrue()
        assertThat(repo.lastMethod).isEqualTo("getTemplates")
    }

    @Test
    fun `CloneVmUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository()
        CloneVmUseCase(repo)(
            nodeId = 1L,
            templateVmid = 9000,
            newVmid = 101,
            newName = "cloned",
            fullClone = true,
        )

        assertThat(repo.lastMethod).isEqualTo("cloneVm")
        assertThat(repo.lastTemplateVmid).isEqualTo(9000)
        assertThat(repo.lastNewVmid).isEqualTo(101)
        assertThat(repo.lastNewName).isEqualTo("cloned")
        assertThat(repo.lastFullClone).isTrue()
    }

    @Test
    fun `PollVmSshUseCase delegates to repository`() = runTest {
        val repo = FakeProxmoxRepository()
        val result = PollVmSshUseCase(repo)(nodeId = 1L, vmid = 100, timeoutSeconds = 30L)

        assertThat(result.getOrNull()).isEqualTo("10.0.0.1")
        assertThat(repo.lastMethod).isEqualTo("pollVmSshReady")
        assertThat(repo.lastPollTimeout).isEqualTo(30L)
    }

    @Test
    fun `ProxmoxVmStatus fromString maps known values`() {
        assertThat(ProxmoxVmStatus.fromString("running")).isEqualTo(ProxmoxVmStatus.RUNNING)
        assertThat(ProxmoxVmStatus.fromString("STOPPED")).isEqualTo(ProxmoxVmStatus.STOPPED)
        assertThat(ProxmoxVmStatus.fromString("paused")).isEqualTo(ProxmoxVmStatus.PAUSED)
        assertThat(ProxmoxVmStatus.fromString("weird")).isEqualTo(ProxmoxVmStatus.UNKNOWN)
    }
}
