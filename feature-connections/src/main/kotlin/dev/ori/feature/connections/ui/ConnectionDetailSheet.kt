package dev.ori.feature.connections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.EllipsisVertical
import dev.ori.core.ui.icons.lucide.Folder
import dev.ori.core.ui.icons.lucide.Link2Off
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.PenLine
import dev.ori.core.ui.icons.lucide.Server
import dev.ori.core.ui.icons.lucide.Terminal
import dev.ori.core.ui.icons.lucide.Trash2
import dev.ori.domain.model.ServerProfile

/**
 * Two-CTA connection detail sheet (spec Section 5).
 *
 * Replaces the v0 six-button soup (Connect / Disconnect / Terminal /
 * Files / Edit / Delete). New layout:
 *
 *  - Header: profile name + `user@host:port` subtitle.
 *  - Primary CTA "Terminal öffnen" (Indigo).
 *  - Secondary CTA "Dateien öffnen" (outlined).
 *  - Destructive "Trennen" row — only rendered when [isConnected].
 *  - Overflow menu (⋯): "Bearbeiten" / "Löschen".
 *
 * The sheet no longer owns the connect step. Both primary CTAs route
 * through `ConnectionListViewModel.openProfile(profileId, target)`,
 * which calls `sessionRegistry.connect()` on-demand and emits a nav
 * effect that the host Screen collects to focus the session and
 * navigate to the bottom tab (Terminal / Files). If connect fails,
 * the sheet stays open and the error surfaces via
 * [ConnectionListUiState.error].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionDetailSheet(
    profile: ServerProfile,
    isConnected: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Verbindung löschen") },
            text = {
                Text(
                    "Verbindung \"${profile.name}\" wirklich löschen? " +
                        "Dieser Vorgang kann nicht rückgängig gemacht werden.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Abbrechen")
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header row: server icon + name/subtitle on the left, ⋯
            // overflow menu anchor on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = LucideIcons.Server,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${profile.username}@${profile.host}:${profile.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(
                            imageVector = LucideIcons.EllipsisVertical,
                            contentDescription = "Weitere Aktionen",
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bearbeiten") },
                            leadingIcon = {
                                Icon(
                                    imageVector = LucideIcons.PenLine,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Löschen",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = LucideIcons.Trash2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                showDeleteConfirmation = true
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // Primary CTA — first tap triggers sessionRegistry.connect()
            // via the ViewModel. The sheet stays open only until the
            // effect fires; the caller clears `selectedProfile` on tap.
            Button(
                onClick = onOpenTerminal,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(imageVector = LucideIcons.Terminal, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Terminal öffnen")
            }

            // Secondary CTA — same connect-on-demand path, routed to
            // the Files tab via OpenTarget.FILES.
            OutlinedButton(
                onClick = onOpenFiles,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = LucideIcons.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dateien öffnen")
            }

            // Disconnect — visible only when a session is actually open
            // for this profile, so users never accidentally "disconnect"
            // a profile that was never connected.
            if (isConnected) {
                TextButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(imageVector = LucideIcons.Link2Off, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trennen")
                }
            }
        }
    }
}
