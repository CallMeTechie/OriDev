package dev.ori.feature.connections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import dev.ori.domain.model.ServerProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailSheet(
    profile: ServerProfile,
    isConnected: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFileManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Connection") },
            text = { Text("Are you sure you want to delete \"${profile.name}\"? This action cannot be undone.") },
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
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
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
            // Header
            Text(
                text = profile.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Details
            DetailRow(label = "Host", value = profile.host)
            DetailRow(label = "Port", value = profile.port.toString())
            DetailRow(label = "Protocol", value = profile.protocol.displayName)
            DetailRow(label = "Username", value = profile.username)
            DetailRow(label = "Auth Method", value = profile.authMethod.displayName)
            profile.lastConnected?.let { timestamp ->
                val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                DetailRow(label = "Last Connected", value = dateFormat.format(Date(timestamp)))
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // Connect / Disconnect button
            Button(
                onClick = { if (isConnected) onDisconnect() else onConnect() },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isConnected) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.LinkOff else Icons.Default.Link,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isConnected) "Disconnect" else "Connect")
            }

            // Action row: Edit, Terminal, File Manager
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = onOpenTerminal,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Terminal")
                }
                OutlinedButton(
                    onClick = onOpenFileManager,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Files")
                }
            }

            // Delete button
            TextButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete Connection")
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
