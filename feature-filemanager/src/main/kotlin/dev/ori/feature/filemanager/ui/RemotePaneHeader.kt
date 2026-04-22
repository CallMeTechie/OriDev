package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.ChevronDown
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.domain.model.Session

/**
 * PR 3 — remote-pane header that shows the focused session and lets the
 * user switch between all open sessions via a dropdown. Sits at the top
 * of the right (remote) pane, above the breadcrumb bar rendered by
 * [FileListPane]. When only one session is open the dropdown chevron is
 * hidden and the row is non-interactive — there is nothing to switch to.
 */
@Composable
internal fun RemotePaneHeader(
    openSessions: List<Session>,
    focusedSessionId: String?,
    onFocusSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val focused = openSessions.firstOrNull { it.id == focusedSessionId }
    val hasMultiple = openSessions.size > 1

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = hasMultiple) { expanded = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = focused?.profileName ?: "Keine Verbindung",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hasMultiple) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = LucideIcons.ChevronDown,
                contentDescription = "Server wechseln",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            openSessions.forEach { session ->
                DropdownMenuItem(
                    text = { Text("${session.profileName} (${session.host})") },
                    onClick = {
                        onFocusSession(session.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
