package dev.ori.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ori.data.session.HostKeyPrompt

/**
 * App-level TOFU dialog that consumes the [HostKeyPrompt] emitted by
 * [dev.ori.data.session.ResumeCoordinator].
 *
 * A thin local copy of `:feature-connections`'s
 * [dev.ori.feature.connections.ui.HostKeyTrustDialog] because the two
 * `HostKeyPrompt` records carry different fields:
 * - the feature-connections prompt has `port`, `keyType`, and
 *   `expectedFingerprint` (for mismatch diffs)
 * - the coordinator prompt carries `id`, `profileName` instead
 *
 * Unifying them via a mapper would either drop the mismatch-diff
 * fields or force the coordinator's record to carry view data it does
 * not own. Since `:app` legitimately depends on `:feature-connections`
 * but must render the coordinator's type, we keep a focused copy here.
 *
 * Unlike the feature-connections variant this dialog handles only the
 * "unknown host" case: auto-resume re-uses [HostKeyPrompt] only for
 * [dev.ori.core.common.error.AppError.HostKeyUnknown] failures
 * (mismatches are treated as `handleFailure`, not a prompt — see
 * [dev.ori.data.session.ResumeCoordinator.connectWithHostKeyQueue]).
 */
@Composable
fun HostKeyTrustDialog(
    prompt: HostKeyPrompt,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = "Host-Schlüssel unbekannt",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Erste Verbindung zu ${prompt.profileName} (${prompt.host}). " +
                        "Prüfe den Fingerabdruck aus einer vertrauenswürdigen Quelle, " +
                        "bevor du fortfährst.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "SHA-256: ${prompt.fingerprint}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Akzeptieren und verbinden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Abbrechen")
            }
        },
    )
}
