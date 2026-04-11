package dev.ori.feature.terminal.ui

import android.os.Looper
import androidx.compose.ui.graphics.Color
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import javax.inject.Inject

/**
 * Abstraction over [TerminalEmulatorFactory] to allow testing without native libs.
 */
interface TerminalEmulatorProvider {
    fun create(
        looper: Looper,
        initialRows: Int,
        initialCols: Int,
        defaultForeground: Color,
        defaultBackground: Color,
        onKeyboardInput: (ByteArray) -> Unit,
        onResize: ((TerminalDimensions) -> Unit)?,
        onClipboardCopy: ((String) -> Unit)?,
    ): TerminalEmulator
}

class DefaultTerminalEmulatorProvider @Inject constructor() : TerminalEmulatorProvider {
    override fun create(
        looper: Looper,
        initialRows: Int,
        initialCols: Int,
        defaultForeground: Color,
        defaultBackground: Color,
        onKeyboardInput: (ByteArray) -> Unit,
        onResize: ((TerminalDimensions) -> Unit)?,
        onClipboardCopy: ((String) -> Unit)?,
    ): TerminalEmulator {
        return TerminalEmulatorFactory.Companion.create(
            looper = looper,
            initialRows = initialRows,
            initialCols = initialCols,
            defaultForeground = defaultForeground,
            defaultBackground = defaultBackground,
            onKeyboardInput = onKeyboardInput,
            onResize = onResize,
            onClipboardCopy = onClipboardCopy,
        )
    }
}
