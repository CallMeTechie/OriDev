package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SshShellManagerTest {

    private val manager = SshShellManager()

    @Test
    fun `closeShell nonExistentId does not throw`() {
        // Should complete without exception
        manager.closeShell("non-existent-id")
    }

    @Test
    fun `getSession nonExistentId returns null`() {
        val session = manager.getSession("non-existent-id")
        assertThat(session).isNull()
    }

    @Test
    fun `isShellOpen nonExistentId returns false`() {
        val isOpen = manager.isShellOpen("non-existent-id")
        assertThat(isOpen).isFalse()
    }
}
