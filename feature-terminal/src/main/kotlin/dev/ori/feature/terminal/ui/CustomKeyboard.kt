package dev.ori.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ori.core.ui.theme.Gray100
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Indigo500
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Phase 11 P2.1-polish — replaced hardcoded hex #F3F4F6 / #E5E7EB with the
// Gray100 / Gray200 theme tokens per the no-hardcoded-colours convention.
private val KeyboardBackground = Gray100
private val KeyBackground = Color.White
private val KeyBorder = Gray200
private val KeyShape = RoundedCornerShape(8.dp)

private const val ESC = "\u001b"

private fun keyDescription(label: String): String = when (label) {
    "Esc" -> "Escape-Taste"
    "Tab" -> "Tabulator-Taste"
    "Ctrl" -> "Steuerungstaste"
    "Alt" -> "Alt-Taste"
    "Shift" -> "Umschalttaste"
    "Enter" -> "Eingabetaste"
    "Fn" -> "Funktionstasten umschalten"
    "Home" -> "Pos1-Taste"
    "End" -> "Ende-Taste"
    "PgUp" -> "Bild auf"
    "PgDn" -> "Bild ab"
    "Ins" -> "Einfügen-Taste"
    "Del" -> "Entfernen-Taste"
    "\u232B" -> "Rücktaste"
    "\u2191" -> "Pfeil nach oben"
    "\u2193" -> "Pfeil nach unten"
    "\u2190" -> "Pfeil nach links"
    "\u2192" -> "Pfeil nach rechts"
    else -> "Taste $label"
}

@Composable
fun CustomKeyboard(
    modifierState: ModifierState,
    onEvent: (TerminalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Phase 14 Task 14.3 — Ctrl/Alt are now in TerminalUiState so the
    // latch survives keyboard-mode switches and is shared with the
    // upcoming TerminalExtraKeys row. Shift is still local: it is
    // keyboard-UI only (label casing on soft keys), irrelevant to the
    // ViewModel's byte stream.
    val ctrlActive = modifierState.ctrl
    val altActive = modifierState.alt
    var shiftActive by remember { mutableStateOf(false) }
    var showFunctionRow by remember { mutableStateOf(false) }

    // Character keys go through SendText so the ViewModel applies its
    // Ctrl/Alt translation table. No local Ctrl mapping here — that
    // was the old two-sources-of-truth design.
    fun sendKey(text: String) {
        onEvent(TerminalEvent.SendText(text))
        if (shiftActive) shiftActive = false
    }

    // Raw bytes (arrow keys, function keys, Esc, Tab char, backspace)
    // bypass the translator — they already are the final bytes the
    // terminal expects. Still clear shift so the next letter key
    // renders lowercase again.
    fun sendRaw(bytes: ByteArray) {
        onEvent(TerminalEvent.SendInput(bytes))
        if (shiftActive) shiftActive = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KeyboardBackground)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Row 1: Function keys (toggleable)
        if (showFunctionRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                KeyButton("Esc") { sendRaw((ESC).toByteArray()) }
                KeyButton("F1") { sendRaw("${ESC}OP".toByteArray()) }
                KeyButton("F2") { sendRaw("${ESC}OQ".toByteArray()) }
                KeyButton("F3") { sendRaw("${ESC}OR".toByteArray()) }
                KeyButton("F4") { sendRaw("${ESC}OS".toByteArray()) }
                KeyButton("F5") { sendRaw("$ESC[15~".toByteArray()) }
                KeyButton("F6") { sendRaw("$ESC[17~".toByteArray()) }
                KeyButton("F7") { sendRaw("$ESC[18~".toByteArray()) }
                KeyButton("F8") { sendRaw("$ESC[19~".toByteArray()) }
                KeyButton("F9") { sendRaw("$ESC[20~".toByteArray()) }
                KeyButton("F10") { sendRaw("$ESC[21~".toByteArray()) }
                KeyButton("F11") { sendRaw("$ESC[23~".toByteArray()) }
                KeyButton("F12") { sendRaw("$ESC[24~".toByteArray()) }
                KeyButton("Home") { sendRaw("$ESC[H".toByteArray()) }
                KeyButton("End") { sendRaw("$ESC[F".toByteArray()) }
                KeyButton("PgUp") { sendRaw("$ESC[5~".toByteArray()) }
                KeyButton("PgDn") { sendRaw("$ESC[6~".toByteArray()) }
                KeyButton("Ins") { sendRaw("$ESC[2~".toByteArray()) }
                KeyButton("Del") { sendRaw("$ESC[3~".toByteArray()) }
            }
        }

        // Row 2: Numbers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToggleKeyButton("Fn", showFunctionRow) { showFunctionRow = !showFunctionRow }
            for (c in "1234567890-=") {
                val label = if (shiftActive) {
                    when (c) {
                        '1' -> '!'
                        '2' -> '@'
                        '3' -> '#'
                        '4' -> '$'
                        '5' -> '%'
                        '6' -> '^'
                        '7' -> '&'
                        '8' -> '*'
                        '9' -> '('
                        '0' -> ')'
                        '-' -> '_'
                        '=' -> '+'
                        else -> c
                    }.toString()
                } else {
                    c.toString()
                }
                KeyButton(label) { sendKey(label) }
            }
        }

        // Row 3: QWERTY top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KeyButton("Tab") { sendKey("\t") }
            for (c in "qwertyuiop") {
                val label = if (shiftActive) c.uppercase() else c.toString()
                KeyButton(label) { sendKey(label) }
            }
            val lBracket = if (shiftActive) "{" else "["
            val rBracket = if (shiftActive) "}" else "]"
            KeyButton(lBracket) { sendKey(lBracket) }
            KeyButton(rBracket) { sendKey(rBracket) }
        }

        // Row 4: QWERTY mid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToggleKeyButton("Ctrl", ctrlActive) { onEvent(TerminalEvent.ToggleCtrl) }
            for (c in "asdfghjkl") {
                val label = if (shiftActive) c.uppercase() else c.toString()
                KeyButton(label) { sendKey(label) }
            }
            val semi = if (shiftActive) ":" else ";"
            val quote = if (shiftActive) "\"" else "'"
            KeyButton(semi) { sendKey(semi) }
            KeyButton(quote) { sendKey(quote) }
            KeyButton("Enter") { sendKey("\r") }
        }

        // Row 5: QWERTY bottom
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToggleKeyButton("Shift", shiftActive) { shiftActive = !shiftActive }
            for (c in "zxcvbnm,.") {
                val label = if (shiftActive) {
                    when (c) {
                        ',' -> '<'
                        '.' -> '>'
                        else -> c.uppercase().first()
                    }.toString()
                } else {
                    c.toString()
                }
                KeyButton(label) { sendKey(label) }
            }
            val slash = if (shiftActive) "?" else "/"
            val backslash = if (shiftActive) "|" else "\\"
            KeyButton(slash) { sendKey(slash) }
            KeyButton(backslash) { sendKey(backslash) }
            RepeatKeyButton("\u2191") { sendRaw("$ESC[A".toByteArray()) }
            KeyButton("\u232B") { sendRaw(byteArrayOf(0x7F)) }
        }

        // Row 6: Bottom
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToggleKeyButton("Alt", altActive) { onEvent(TerminalEvent.ToggleAlt) }
            KeyButton("~") { sendKey("~") }
            KeyButton("`") { sendKey("`") }
            KeyButton("|") { sendKey("|") }
            SpaceKeyButton { sendKey(" ") }
            RepeatKeyButton("\u2190") { sendRaw("$ESC[D".toByteArray()) }
            RepeatKeyButton("\u2193") { sendRaw("$ESC[B".toByteArray()) }
            RepeatKeyButton("\u2192") { sendRaw("$ESC[C".toByteArray()) }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit,
) {
    val description = keyDescription(label)
    Box(
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 32.dp)
            .background(KeyBackground, KeyShape)
            .border(1.dp, KeyBorder, KeyShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(label) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = Color.Black,
        )
    }
}

@Composable
private fun ToggleKeyButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isActive) Indigo500 else KeyBackground
    val textColor = if (isActive) Color.White else Color.Black
    val description = keyDescription(label)
    val toggleState = if (isActive) "aktiv" else "inaktiv"
    Box(
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 40.dp)
            .background(bg, KeyShape)
            .border(1.dp, KeyBorder, KeyShape)
            .semantics {
                contentDescription = description
                stateDescription = toggleState
                role = Role.Switch
            }
            .pointerInput(label) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = textColor,
        )
    }
}

@Composable
private fun RepeatKeyButton(
    label: String,
    onPress: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    val description = keyDescription(label)

    Box(
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 36.dp)
            .background(KeyBackground, KeyShape)
            .border(1.dp, KeyBorder, KeyShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        repeatJob = scope.launch {
                            delay(200)
                            while (true) {
                                onPress()
                                delay(50)
                            }
                        }
                        tryAwaitRelease()
                        repeatJob?.cancel()
                        repeatJob = null
                    },
                )
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = Color.Black,
        )
    }
}

@Composable
private fun RowScope.SpaceKeyButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .weight(1f)
            .background(KeyBackground, KeyShape)
            .border(1.dp, KeyBorder, KeyShape)
            .semantics {
                contentDescription = "Leertaste"
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SPACE",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = Color.Black,
        )
    }
}
