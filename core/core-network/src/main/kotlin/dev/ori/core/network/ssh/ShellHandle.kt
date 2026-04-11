package dev.ori.core.network.ssh

import java.io.InputStream
import java.io.OutputStream

data class ShellHandle(
    val shellId: String,
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val onResize: (cols: Int, rows: Int) -> Unit,
    val onClose: () -> Unit,
)
