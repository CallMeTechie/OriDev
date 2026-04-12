package dev.ori.data.repository

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appSuccess
import dev.ori.core.common.result.getAppError
import dev.ori.core.network.proxmox.ProxmoxApiService
import dev.ori.core.network.proxmox.ProxmoxTarget
import dev.ori.core.network.proxmox.model.ProxmoxCloneRequest
import dev.ori.core.network.proxmox.model.ProxmoxNodeDto
import dev.ori.core.network.proxmox.model.ProxmoxTaskStatusDto
import dev.ori.core.network.proxmox.model.ProxmoxVmDto
import dev.ori.data.dao.ProxmoxNodeDao
import dev.ori.data.entity.ProxmoxNodeEntity
import dev.ori.domain.repository.CredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests use a hand-written FakeProxmoxApiService rather than mockk stubs because mockk
 * mis-handles suspend functions that return Kotlin's inline-class `Result` (AppResult).
 */
class ProxmoxRepositoryImplTest {

    private val dao = mockk<ProxmoxNodeDao>(relaxed = true)
    private val credentialStore = mockk<CredentialStore>(relaxed = true)
    private val api = FakeProxmoxApiService()

    private val repository = ProxmoxRepositoryImpl(dao, api, credentialStore)

    private fun entity(id: Long = 1L) = ProxmoxNodeEntity(
        id = id,
        name = "lab",
        host = "10.0.0.5",
        port = 8006,
        tokenId = "root@pam!api",
        tokenSecretRef = "proxmox_token_$id",
        certFingerprint = "AA:BB:CC",
        lastSyncAt = 0L,
    )

    @Test
    fun `addNode stores token and inserts entity`() = runTest {
        coEvery { dao.insert(any()) } returns 7L

        val result = repository.addNode(
            name = "lab",
            host = "10.0.0.5",
            port = 8006,
            tokenId = "root@pam!api",
            tokenSecret = "super-secret",
            certFingerprint = "AA:BB:CC",
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(7L)

        val aliasSlot = slot<String>()
        val charsSlot = slot<CharArray>()
        coVerify { credentialStore.storePassword(capture(aliasSlot), capture(charsSlot)) }
        assertThat(aliasSlot.captured).isEqualTo("proxmox_token_7")
        assertThat(String(charsSlot.captured)).isEqualTo("super-secret")

        coVerify { dao.update(match { it.id == 7L && it.tokenSecretRef == "proxmox_token_7" }) }
    }

    @Test
    fun `startVm loads credentials and calls api`() = runTest {
        coEvery { dao.getById(1L) } returns entity(1L)
        coEvery { credentialStore.getPassword("proxmox_token_1") } returns "secret".toCharArray()
        api.nodes = listOf(ProxmoxNodeDto(node = "pve", status = "online"))
        api.startVmUpid = "UPID:start"

        val result = repository.startVm(nodeId = 1L, vmid = 100)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("UPID:start")
        assertThat(api.startVmCalls).containsExactly(Triple("pve", 100, Unit))
    }

    @Test
    fun `getVms filters out templates`() = runTest {
        coEvery { dao.getById(1L) } returns entity(1L)
        coEvery { credentialStore.getPassword("proxmox_token_1") } returns "secret".toCharArray()
        api.nodes = listOf(ProxmoxNodeDto(node = "pve", status = "online"))
        api.vms = listOf(
            ProxmoxVmDto(vmid = 100, name = "regular", status = "running", template = 0),
            ProxmoxVmDto(vmid = 9000, name = "tmpl", status = "stopped", template = 1),
        )

        val result = repository.getVms(1L)

        assertThat(result.isSuccess).isTrue()
        val vms = result.getOrNull().orEmpty()
        assertThat(vms).hasSize(1)
        assertThat(vms.first().vmid).isEqualTo(100)
        assertThat(vms.first().isTemplate).isFalse()
    }

    @Test
    fun `getTemplates returns only templates`() = runTest {
        coEvery { dao.getById(1L) } returns entity(1L)
        coEvery { credentialStore.getPassword("proxmox_token_1") } returns "secret".toCharArray()
        api.nodes = listOf(ProxmoxNodeDto(node = "pve", status = "online"))
        api.vms = listOf(
            ProxmoxVmDto(vmid = 100, name = "regular", status = "running", template = 0),
            ProxmoxVmDto(vmid = 9000, name = "tmpl", status = "stopped", template = 1),
        )

        val result = repository.getTemplates(1L)

        assertThat(result.isSuccess).isTrue()
        val templates = result.getOrNull().orEmpty()
        assertThat(templates).hasSize(1)
        assertThat(templates.first().vmid).isEqualTo(9000)
        assertThat(templates.first().isTemplate).isTrue()
    }

    @Test
    fun `cloneVm forwards request to api`() = runTest {
        coEvery { dao.getById(1L) } returns entity(1L)
        coEvery { credentialStore.getPassword("proxmox_token_1") } returns "secret".toCharArray()
        api.nodes = listOf(ProxmoxNodeDto(node = "pve", status = "online"))
        api.cloneUpid = "UPID:clone"

        val result = repository.cloneVm(
            nodeId = 1L,
            templateVmid = 9000,
            newVmid = 101,
            newName = "new-vm",
            fullClone = true,
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("UPID:clone")
        val captured = api.cloneCalls.single()
        assertThat(captured.third.newid).isEqualTo(101)
        assertThat(captured.third.name).isEqualTo("new-vm")
        assertThat(captured.third.full).isEqualTo(1)
    }

    @Test
    fun `waitForTask polls until complete`() = runTest {
        coEvery { dao.getById(1L) } returns entity(1L)
        coEvery { credentialStore.getPassword("proxmox_token_1") } returns "secret".toCharArray()
        api.nodes = listOf(ProxmoxNodeDto(node = "pve", status = "online"))
        api.taskStatusSequence = mutableListOf(
            ProxmoxTaskStatusDto(status = "running"),
            ProxmoxTaskStatusDto(status = "stopped", exitstatus = "OK"),
        )

        val result: AppResult<Unit> = repository.waitForTask(
            nodeId = 1L,
            upid = "UPID:xxx",
            timeoutSeconds = 10L,
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(api.taskStatusCalls.get()).isAtLeast(2)
    }

    @Test
    fun `pollVmSshReady returns deferred failure for v1`() = runTest {
        val result = repository.pollVmSshReady(nodeId = 1L, vmid = 100, timeoutSeconds = 1L)
        assertThat(result.isFailure).isTrue()
        val error = result.getAppError()
        assertThat(error).isInstanceOf(AppError.NetworkError::class.java)
    }

    @Test
    fun `getNodes maps entities with runtime defaults`() = runTest {
        coEvery { dao.getAll() } returns flowOf(listOf(entity(1L), entity(2L)))

        val nodes = repository.getNodes().first()

        assertThat(nodes).hasSize(2)
        assertThat(nodes.first().isOnline).isFalse()
        assertThat(nodes.first().nodeName).isNull()
    }
}

private class FakeProxmoxApiService : ProxmoxApiService {
    var nodes: List<ProxmoxNodeDto> = emptyList()
    var vms: List<ProxmoxVmDto> = emptyList()
    var startVmUpid: String = "UPID:default"
    var cloneUpid: String = "UPID:default"
    var taskStatusSequence: MutableList<ProxmoxTaskStatusDto> = mutableListOf()
    val taskStatusCalls = AtomicInteger(0)
    val startVmCalls = mutableListOf<Triple<String, Int, Unit>>()
    val cloneCalls = mutableListOf<Triple<String, Int, ProxmoxCloneRequest>>()

    override suspend fun listNodes(target: ProxmoxTarget): AppResult<List<ProxmoxNodeDto>> =
        appSuccess(nodes)

    override suspend fun listVms(target: ProxmoxTarget, node: String): AppResult<List<ProxmoxVmDto>> =
        appSuccess(vms)

    override suspend fun getVmStatus(target: ProxmoxTarget, node: String, vmid: Int): AppResult<ProxmoxVmDto> =
        appSuccess(vms.first { it.vmid == vmid })

    override suspend fun startVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String> {
        startVmCalls.add(Triple(node, vmid, Unit))
        return appSuccess(startVmUpid)
    }

    override suspend fun stopVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String> =
        appSuccess("UPID:stop")

    override suspend fun rebootVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String> =
        appSuccess("UPID:reboot")

    override suspend fun deleteVm(target: ProxmoxTarget, node: String, vmid: Int): AppResult<String> =
        appSuccess("UPID:delete")

    override suspend fun cloneVm(
        target: ProxmoxTarget,
        node: String,
        templateVmid: Int,
        request: ProxmoxCloneRequest,
    ): AppResult<String> {
        cloneCalls.add(Triple(node, templateVmid, request))
        return appSuccess(cloneUpid)
    }

    override suspend fun getTaskStatus(
        target: ProxmoxTarget,
        node: String,
        upid: String,
    ): AppResult<ProxmoxTaskStatusDto> {
        taskStatusCalls.incrementAndGet()
        val next = if (taskStatusSequence.size > 1) {
            taskStatusSequence.removeAt(0)
        } else {
            taskStatusSequence.firstOrNull() ?: ProxmoxTaskStatusDto(status = "stopped", exitstatus = "OK")
        }
        return appSuccess(next)
    }
}
