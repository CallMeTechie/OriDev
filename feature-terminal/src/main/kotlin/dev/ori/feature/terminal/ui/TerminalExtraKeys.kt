package dev.ori.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.ori.core.ui.icons.lucide.ChevronDown
import dev.ori.core.ui.icons.lucide.ChevronLeft
import dev.ori.core.ui.icons.lucide.ChevronRight
import dev.ori.core.ui.icons.lucide.ChevronUp
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Gray100
import dev.ori.core.ui.theme.Gray200

/**
 * Phase 14 Task 14.4 — sticky extra-keys row that sits above the
 * system IME in HYBRID keyboard mode. Horizontally-scrollable strip
 * of terminal-specific modifier keys (Esc, Tab, Ctrl, Alt, arrows,
 * Fn, `|`, `/`, `~`, backtick, Home, End, PgUp, PgDn) — 17 keys
 * total — that complement the platform keyboard while a user is
 * typing into the terminal.
 *
 * The row reads [ModifierState] (Task 14.3) for the Ctrl/Alt
 * latched-state visual and the sticky-dot indicator:
 * - Latched Ctrl/Alt: key background switches to
 *   `primaryContainer` / `onPrimaryContainer` content colour.
 * - Sticky (`ModifierState.sticky == true`) AND that modifier
 *   active: small 5 dp filled-circle dot in the key's top-right
 *   corner. If sticky is true but neither Ctrl nor Alt is active,
 *   no dot is drawn — sticky only "matters" alongside an active
 *   modifier.
 *
 * Interaction:
 * - Tap Ctrl/Alt → [TerminalEvent.ToggleCtrl] / [TerminalEvent.ToggleAlt].
 * - Long-press Ctrl/Alt → [TerminalEvent.ToggleStickyModifier].
 * - Tap any non-modifier key → [TerminalEvent.SendInput] with the
 *   correct raw bytes (see [keyToBytes]).
 *
 * Visual tokens match [CustomKeyboard] for consistency: [Gray100]
 * bar background, [Color.White] idle key surface, [Gray200]
 * 1 dp border. The whole row is exactly 52 dp tall.
 *
 * Task 14.5 will wire this composable into `KeyboardHost`; this
 * composable deliberately does NOT modify any other file.
 */
@Composable
fun TerminalExtraKeys(
    modifierState: ModifierState,
    onEvent: (TerminalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ExtraKeysRowHeight)
            .background(Gray100)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AllExtraKeys.forEach { key ->
            when (key) {
                ExtraKey.Ctrl -> ModifierKeyCell(
                    key = key,
                    isActive = modifierState.ctrl,
                    showStickyDot = modifierState.sticky && modifierState.ctrl,
                    onTap = { onEvent(TerminalEvent.ToggleCtrl) },
                    onLongPress = { onEvent(TerminalEvent.ToggleStickyModifier) },
                )
                ExtraKey.Alt -> ModifierKeyCell(
                    key = key,
                    isActive = modifierState.alt,
                    showStickyDot = modifierState.sticky && modifierState.alt,
                    onTap = { onEvent(TerminalEvent.ToggleAlt) },
                    onLongPress = { onEvent(TerminalEvent.ToggleStickyModifier) },
                )
                else -> PlainKeyCell(
                    key = key,
                    onTap = {
                        keyToBytes(key)?.let { onEvent(TerminalEvent.SendInput(it)) }
                    },
                )
            }
        }
    }
}

internal val ExtraKeysRowHeight = 52.dp
private val KeyShape = RoundedCornerShape(8.dp)
private val KeyBackground = Color.White
private val KeyBorder = Gray200
private const val ESC_CHAR = "\u001b"

/**
 * The 17 keys rendered by [TerminalExtraKeys], in display order.
 * Order matches the plan: Esc, Tab, Ctrl, Alt, arrows, Fn, `|`,
 * `/`, `~`, backtick, Home, End, PgUp, PgDn.
 */
internal sealed interface ExtraKey {
    data object Esc : ExtraKey
    data object Tab : ExtraKey
    data object Ctrl : ExtraKey
    data object Alt : ExtraKey
    data object ArrowUp : ExtraKey
    data object ArrowDown : ExtraKey
    data object ArrowLeft : ExtraKey
    data object ArrowRight : ExtraKey
    data object Fn : ExtraKey
    data object Pipe : ExtraKey
    data object Slash : ExtraKey
    data object Tilde : ExtraKey
    data object Backtick : ExtraKey
    data object Home : ExtraKey
    data object End : ExtraKey
    data object PgUp : ExtraKey
    data object PgDn : ExtraKey
}

internal val AllExtraKeys: List<ExtraKey> = listOf(
    ExtraKey.Esc,
    ExtraKey.Tab,
    ExtraKey.Ctrl,
    ExtraKey.Alt,
    ExtraKey.ArrowUp,
    ExtraKey.ArrowDown,
    ExtraKey.ArrowLeft,
    ExtraKey.ArrowRight,
    ExtraKey.Fn,
    ExtraKey.Pipe,
    ExtraKey.Slash,
    ExtraKey.Tilde,
    ExtraKey.Backtick,
    ExtraKey.Home,
    ExtraKey.End,
    ExtraKey.PgUp,
    ExtraKey.PgDn,
)

/**
 * Pure lookup table from [ExtraKey] to the raw bytes that should be
 * sent to the shell when that key is tapped. Returns `null` for
 * the modifier keys ([ExtraKey.Ctrl], [ExtraKey.Alt]) — those are
 * state-toggle events, not byte-emitting keys — and for [ExtraKey.Fn]
 * which is a future toggle affordance (no assigned byte yet).
 *
 * Byte encodings:
 * - `Esc` → `0x1B`
 * - `Tab` → `0x09`
 * - Arrows → CSI sequences `ESC [ A/B/C/D` (up/down/right/left)
 * - `|` `/` `~` `` ` `` → UTF-8 bytes of the char
 * - `Home` → `ESC [ H`
 * - `End`  → `ESC [ F`
 * - `PgUp` → `ESC [ 5 ~`
 * - `PgDn` → `ESC [ 6 ~`
 */
internal fun keyToBytes(key: ExtraKey): ByteArray? = when (key) {
    ExtraKey.Esc -> byteArrayOf(0x1B)
    ExtraKey.Tab -> byteArrayOf(0x09)
    ExtraKey.Ctrl -> null
    ExtraKey.Alt -> null
    ExtraKey.ArrowUp -> "$ESC_CHAR[A".toByteArray()
    ExtraKey.ArrowDown -> "$ESC_CHAR[B".toByteArray()
    ExtraKey.ArrowRight -> "$ESC_CHAR[C".toByteArray()
    ExtraKey.ArrowLeft -> "$ESC_CHAR[D".toByteArray()
    ExtraKey.Fn -> null
    ExtraKey.Pipe -> "|".toByteArray()
    ExtraKey.Slash -> "/".toByteArray()
    ExtraKey.Tilde -> "~".toByteArray()
    ExtraKey.Backtick -> "`".toByteArray()
    ExtraKey.Home -> "$ESC_CHAR[H".toByteArray()
    ExtraKey.End -> "$ESC_CHAR[F".toByteArray()
    ExtraKey.PgUp -> "$ESC_CHAR[5~".toByteArray()
    ExtraKey.PgDn -> "$ESC_CHAR[6~".toByteArray()
}

private fun textLabelFor(key: ExtraKey): String = when (key) {
    ExtraKey.Esc -> "Esc"
    ExtraKey.Tab -> "Tab"
    ExtraKey.Ctrl -> "Ctrl"
    ExtraKey.Alt -> "Alt"
    ExtraKey.Fn -> "Fn"
    ExtraKey.Pipe -> "|"
    ExtraKey.Slash -> "/"
    ExtraKey.Tilde -> "~"
    ExtraKey.Backtick -> "`"
    ExtraKey.Home -> "Home"
    ExtraKey.End -> "End"
    ExtraKey.PgUp -> "PgUp"
    ExtraKey.PgDn -> "PgDn"
    // Arrows use icons, never this path — keep a label for a11y.
    ExtraKey.ArrowUp -> "\u2191"
    ExtraKey.ArrowDown -> "\u2193"
    ExtraKey.ArrowLeft -> "\u2190"
    ExtraKey.ArrowRight -> "\u2192"
}

private fun contentDescriptionFor(key: ExtraKey): String = when (key) {
    ExtraKey.Esc -> "Escape-Taste"
    ExtraKey.Tab -> "Tabulator-Taste"
    ExtraKey.Ctrl -> "Steuerungstaste"
    ExtraKey.Alt -> "Alt-Taste"
    ExtraKey.ArrowUp -> "Pfeil nach oben"
    ExtraKey.ArrowDown -> "Pfeil nach unten"
    ExtraKey.ArrowLeft -> "Pfeil nach links"
    ExtraKey.ArrowRight -> "Pfeil nach rechts"
    ExtraKey.Fn -> "Funktionstasten umschalten"
    ExtraKey.Pipe -> "Pipe"
    ExtraKey.Slash -> "Schrägstrich"
    ExtraKey.Tilde -> "Tilde"
    ExtraKey.Backtick -> "Backtick"
    ExtraKey.Home -> "Pos1-Taste"
    ExtraKey.End -> "Ende-Taste"
    ExtraKey.PgUp -> "Bild auf"
    ExtraKey.PgDn -> "Bild ab"
}

private fun arrowIconFor(key: ExtraKey): ImageVector? = when (key) {
    // Lucide vendor pack ships ChevronUp/Down/Left/Right (not ArrowUp…).
    // Chevrons read as directional arrows at this size and match the
    // no-Material-icons rule enforced by .semgrep.yml for feature modules.
    ExtraKey.ArrowUp -> LucideIcons.ChevronUp
    ExtraKey.ArrowDown -> LucideIcons.ChevronDown
    ExtraKey.ArrowLeft -> LucideIcons.ChevronLeft
    ExtraKey.ArrowRight -> LucideIcons.ChevronRight
    else -> null
}

@Composable
private fun RowScope.PlainKeyCell(
    key: ExtraKey,
    onTap: () -> Unit,
) {
    val description = contentDescriptionFor(key)
    val icon = arrowIconFor(key)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 40.dp)
            .padding(vertical = 6.dp)
            .background(KeyBackground, KeyShape)
            .border(1.dp, KeyBorder, KeyShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(key) {
                detectTapGestures(onTap = { onTap() })
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = textLabelFor(key),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = Color.Black,
            )
        }
    }
}

@Composable
private fun RowScope.ModifierKeyCell(
    key: ExtraKey,
    isActive: Boolean,
    showStickyDot: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val description = contentDescriptionFor(key)
    val bg = if (isActive) MaterialTheme.colorScheme.primaryContainer else KeyBackground
    val textColor =
        if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black
    val toggleState = if (isActive) "aktiv" else "inaktiv"
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 40.dp)
            .padding(vertical = 6.dp)
            .background(bg, KeyShape)
            .border(1.dp, KeyBorder, KeyShape)
            .semantics {
                contentDescription = description
                stateDescription = toggleState
                role = Role.Switch
            }
            .pointerInput(key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = textLabelFor(key),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = textColor,
        )
        if (showStickyDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(5.dp)
                    .background(textColor, CircleShape)
                    .semantics { contentDescription = "Sticky aktiv" },
            )
        }
    }
}
