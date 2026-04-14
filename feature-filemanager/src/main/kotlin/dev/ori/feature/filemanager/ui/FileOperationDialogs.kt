package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ori.domain.model.FileItem

/**
 * Phase 11 P4.1 — dialogs that back the three Material-stubbed context menu
 * entries (Rename, Permissions, New folder). Each dialog is a thin wrapper
 * around [AlertDialog] with a single validated input field and explicit
 * confirm/dismiss buttons. No primitive extraction: Settings' AlertDialog is
 * still the project default for transient modals, and these follow the same
 * visual register.
 */

/**
 * Rename the selected [file] in-place. Confirms with the full new path (same
 * parent directory, new leaf name). Trimmed-empty input disables Rename.
 */
@Composable
fun RenameDialog(
    file: FileItem,
    onConfirm: (newPath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf(file.name) }
    val trimmed = newName.trim()
    val enabled = trimmed.isNotEmpty() && trimmed != file.name && !trimmed.contains('/')
    val parent = file.path.substringBeforeLast('/', missingDelimiterValue = "")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column {
                Text(
                    text = "Renaming ${file.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled,
                onClick = {
                    val newPath = if (parent.isEmpty()) trimmed else "$parent/$trimmed"
                    onConfirm(newPath)
                },
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Change Unix permissions on [file]. Input is a 3- or 4-digit octal string
 * (e.g. `755`, `0644`); anything else disables the confirm button.
 */
@Composable
fun ChmodDialog(
    file: FileItem,
    onConfirm: (permissions: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var permissions by remember { mutableStateOf("644") }
    val enabled = isValidOctalPermissions(permissions)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions") },
        text = {
            Column {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = permissions,
                    onValueChange = { permissions = it.take(OCTAL_MAX_LENGTH) },
                    label = { Text("Octal (e.g. 755)") },
                    singleLine = true,
                    isError = permissions.isNotEmpty() && !enabled,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled,
                onClick = { onConfirm(permissions) },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Create a new subdirectory under [parentPath]. Trimmed-empty input or a
 * name containing a path separator disables Create.
 */
@Composable
fun MkdirDialog(
    parentPath: String,
    onConfirm: (newPath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val enabled = trimmed.isNotEmpty() && !trimmed.contains('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            Column {
                Text(
                    text = "In $parentPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled,
                onClick = {
                    val newPath = if (parentPath.endsWith('/')) {
                        "$parentPath$trimmed"
                    } else {
                        "$parentPath/$trimmed"
                    }
                    onConfirm(newPath)
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val OCTAL_MIN_LENGTH = 3
private const val OCTAL_MAX_LENGTH = 4

private fun isValidOctalPermissions(input: String): Boolean {
    if (input.length !in OCTAL_MIN_LENGTH..OCTAL_MAX_LENGTH) return false
    return input.all { it in '0'..'7' }
}
