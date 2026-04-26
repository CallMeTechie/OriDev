package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SshScpClientImplTest {
    val store = mockk<SshSessionStore>(relaxed = true)
    val sshClient = SshScpClientImpl(store)

    @Test
    fun isConnected_delegates() = kotlinx.coroutines.test.runTest {
        every { store.isConnected("s1") } returns true
        assertThat(sshClient.isConnected("s1")).isTrue()
    }

    @Test
    fun uploadFileResumable_throws() = kotlinx.coroutines.test.runTest {
        val ex = assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { sshClient.uploadFileResumable("s1", "/local", "/remote", 0L) }
        }
        assertThat(ex.message).contains("SCP does not support resume")
    }

    @Test
    fun downloadFileResumable_throws() = kotlinx.coroutines.test.runTest {
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { sshClient.downloadFileResumable("s1", "/remote", "/local", 0L) }
        }
    }
}
