package dev.ori.core.network.ftp

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FtpClientImplTest {

    @Test
    fun `can be instantiated`() {
        val client = FtpClientImpl()
        assertThat(client).isNotNull()
    }

    @Test
    fun `isConnected returns false initially`() {
        val client = FtpClientImpl()
        assertThat(client.isConnected).isFalse()
    }
}
