package dev.ori.core.network.proxmox

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppErrorException
import dev.ori.core.network.proxmox.model.ProxmoxCloneRequest
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProxmoxApiServiceImplTest {

    private lateinit var server: MockWebServer
    private lateinit var service: ProxmoxApiServiceImpl
    private lateinit var target: ProxmoxTarget

    private class TestableImpl(
        moshi: Moshi,
        private val base: HttpUrl,
        private val plainClient: OkHttpClient,
    ) : ProxmoxApiServiceImpl(moshi) {
        override fun clientFor(target: ProxmoxTarget): OkHttpClient = plainClient
        override fun baseUrl(target: ProxmoxTarget): HttpUrl = base
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val base = server.url("/api2/json/")
        val plain = OkHttpClient.Builder().build()
        service = TestableImpl(moshi, base, plain)
        target = ProxmoxTarget(
            host = server.hostName,
            port = server.port,
            tokenId = "root@pam!mytoken",
            tokenSecret = "abc-secret",
            expectedFingerprint = "AA:BB:CC",
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listNodes success returns nodes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"node":"pve1","status":"online","cpu":0.1,"maxcpu":8}]}""",
            ),
        )

        val result = service.listNodes(target)

        assertThat(result.isSuccess).isTrue()
        val nodes = result.getOrNull()!!
        assertThat(nodes).hasSize(1)
        assertThat(nodes[0].node).isEqualTo("pve1")
        assertThat(nodes[0].status).isEqualTo("online")
    }

    @Test
    fun `listNodes 401 returns AuthenticationError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"denied"}"""))

        val result = service.listNodes(target)

        assertThat(result.isFailure).isTrue()
        val err = (result.exceptionOrNull() as AppErrorException).error
        assertThat(err).isInstanceOf(AppError.AuthenticationError::class.java)
    }

    @Test
    fun `listVms success filters templates`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[
                    {"vmid":100,"name":"vm-a","status":"running"},
                    {"vmid":200,"name":"tpl","status":"stopped","template":1}
                ]}""",
            ),
        )

        val result = service.listVms(target, "pve1")

        assertThat(result.isSuccess).isTrue()
        val vms = result.getOrNull()!!
        assertThat(vms).hasSize(2)
        assertThat(vms.count { it.template == 1 }).isEqualTo(1)
    }

    @Test
    fun `startVm success returns upid`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":"UPID:pve1:00001234:start"}"""),
        )

        val result = service.startVm(target, "pve1", 100)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("UPID:pve1:00001234:start")
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api2/json/nodes/pve1/qemu/100/status/start")
    }

    @Test
    fun `stopVm success returns upid`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":"UPID:pve1:00001234:stop"}"""),
        )

        val result = service.stopVm(target, "pve1", 100)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("UPID:pve1:00001234:stop")
    }

    @Test
    fun `cloneVm with request sends form body`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":"UPID:pve1:clone"}"""),
        )

        val result = service.cloneVm(
            target,
            "pve1",
            templateVmid = 9000,
            request = ProxmoxCloneRequest(newid = 101, name = "cloned", full = 1, target = "pve1"),
        )

        assertThat(result.isSuccess).isTrue()
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertThat(body).contains("newid=101")
        assertThat(body).contains("name=cloned")
        assertThat(body).contains("full=1")
        assertThat(body).contains("target=pve1")
        assertThat(recorded.path).isEqualTo("/api2/json/nodes/pve1/qemu/9000/clone")
    }

    @Test
    fun `getTaskStatus running returns running`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"status":"running"}}"""),
        )

        val result = service.getTaskStatus(target, "pve1", "UPID:x")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.status).isEqualTo("running")
    }

    @Test
    fun `getTaskStatus failed returns error status`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"status":"stopped","exitstatus":"command failed"}}"""),
        )

        val result = service.getTaskStatus(target, "pve1", "UPID:x")

        assertThat(result.isSuccess).isTrue()
        val status = result.getOrNull()!!
        assertThat(status.status).isEqualTo("stopped")
        assertThat(status.exitstatus).isEqualTo("command failed")
    }

    @Test
    fun `deleteVm success returns upid`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":"UPID:pve1:delete"}"""),
        )

        val result = service.deleteVm(target, "pve1", 100)

        assertThat(result.isSuccess).isTrue()
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
    }

    @Test
    fun `authHeader includes PVEAPIToken`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"data":[]}"""),
        )

        service.listNodes(target)

        val recorded = server.takeRequest()
        val auth = recorded.getHeader("Authorization")
        assertThat(auth).isEqualTo("PVEAPIToken=root@pam!mytoken=abc-secret")
    }
}
