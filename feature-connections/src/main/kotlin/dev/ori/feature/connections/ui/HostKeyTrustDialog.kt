package dev.ori.feature.connections.ui

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

/**
 * TOFU (Trust-On-First-Use) dialog for unknown or mismatched SSH host keys.
 *
 * The dialog serves two flows distinguished by whether
 * [HostKeyPrompt.expectedFingerprint] is `null`:
 *
 * - **Unknown host** — we've never seen this host before; accepting pins
 *   the fingerprint in [dev.ori.domain.repository.KnownHostRepository] so
 *   subsequent connects pass verification silently.
 * - **Mismatch** — the host's fingerprint changed since we last trusted
 *   it. This is either the user rotated keys or a man-in-the-middle. We
 *   show both fingerprints side-by-side and make rejection the default
 *   action; accepting overwrites the stored entry.
 *
 * Either way the dialog must not allow the user to simply dismiss into
 * a retry loop — declining cancels the connect, accepting persists and
 * retries exactly once. That discipline is what prevents the fail2ban
 * bans we saw when the old code path surfaced this only as a toast.
 */
@Composable
internal fun HostKeyTrustDialog(
    prompt: HostKeyPrompt,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val isMismatch = prompt.expectedFingerprint != null
    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(
                text = if (isMismatch) "Host-Schlüssel geändert!" else "Host-Schlüssel unbekannt",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (isMismatch) {
                        "Der Schlüssel von ${prompt.host} stimmt nicht mit dem gespeicherten " +
                            "überein. Das kann ein Schlüsselwechsel sein — oder ein Angriff. " +
                            "Nur fortfahren, wenn du den Wechsel selbst veranlasst hast."
                    } else {
                        "Dies ist die erste Verbindung zu ${prompt.host}. Prüfe den " +
                            "Fingerabdruck aus einer vertrauenswürdigen Quelle (z. B. per " +
                            "SSH vom Server: ssh-keygen -l -f /etc/ssh/ssh_host_ed25519_key)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Typ: ${prompt.keyType}",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (isMismatch) {
                    Text(
                        text = "Erwartet (gespeichert): ${prompt.expectedFingerprint}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "Empfangen: ${prompt.fingerprint}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    Text(
                        text = "SHA-256: ${prompt.fingerprint}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(if (isMismatch) "Trotzdem fortfahren" else "Akzeptieren und verbinden")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Abbrechen")
            }
        },
    )
}
