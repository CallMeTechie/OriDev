package dev.ori.feature.proxmox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private const val FINGERPRINT_GROUP_SIZE = 4

@Composable
fun CertificateTrustDialog(
    request: CertificateTrustRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Untrusted Certificate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Host: ${request.host}:${request.port}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Fingerprint (SHA-256):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatFingerprint(request.fingerprint),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                )
                Text(
                    text = "This is a self-signed certificate. " +
                        "Only trust if you recognize the fingerprint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Trust and Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatFingerprint(fingerprint: String): String {
    // Strip existing separators then regroup as 4-char chunks, 8 per line.
    val normalized = fingerprint.replace(":", "").replace(" ", "").replace("\n", "")
    val groups = normalized.chunked(FINGERPRINT_GROUP_SIZE)
    return groups.chunked(GROUPS_PER_LINE).joinToString("\n") { line ->
        line.joinToString(" ")
    }
}

private const val GROUPS_PER_LINE = 8
