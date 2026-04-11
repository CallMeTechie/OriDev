package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ShellHandleTest {

    @Test
    fun shellHandle_holdsStreams() {
        val input = ByteArrayInputStream(byteArrayOf())
        val output = ByteArrayOutputStream()
        val handle = ShellHandle(
            shellId = "test-id",
            inputStream = input,
            outputStream = output,
            onResize = { _, _ -> },
            onClose = {},
        )
        assertThat(handle.shellId).isEqualTo("test-id")
        assertThat(handle.inputStream).isSameInstanceAs(input)
        assertThat(handle.outputStream).isSameInstanceAs(output)
    }

    @Test
    fun shellHandle_onClose_callsCallback() {
        var closed = false
        val handle = ShellHandle(
            shellId = "id",
            inputStream = ByteArrayInputStream(byteArrayOf()),
            outputStream = ByteArrayOutputStream(),
            onResize = { _, _ -> },
            onClose = { closed = true },
        )
        handle.onClose()
        assertThat(closed).isTrue()
    }
}
