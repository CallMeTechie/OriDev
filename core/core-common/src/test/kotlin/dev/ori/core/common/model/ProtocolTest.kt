package dev.ori.core.common.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ProtocolTest {

    @Test
    fun ssh_isSshBased_returnsTrue() {
        assertThat(Protocol.SSH.isSshBased).isTrue()
        assertThat(Protocol.SFTP.isSshBased).isTrue()
        assertThat(Protocol.SCP.isSshBased).isTrue()
    }

    @Test
    fun ftp_isSshBased_returnsFalse() {
        assertThat(Protocol.FTP.isSshBased).isFalse()
        assertThat(Protocol.FTPS.isSshBased).isFalse()
        assertThat(Protocol.PROXMOX.isSshBased).isFalse()
    }

    @Test
    fun ftp_requiresEncryption_returnsFalse() {
        assertThat(Protocol.FTP.requiresEncryption).isFalse()
    }

    @Test
    fun allOtherProtocols_requireEncryption_returnsTrue() {
        Protocol.entries
            .filter { it != Protocol.FTP }
            .forEach { assertThat(it.requiresEncryption).isTrue() }
    }

    @Test
    fun ssh_defaultPort_is22() {
        assertThat(Protocol.SSH.defaultPort).isEqualTo(22)
    }

    @Test
    fun proxmox_defaultPort_is8006() {
        assertThat(Protocol.PROXMOX.defaultPort).isEqualTo(8006)
    }
}
