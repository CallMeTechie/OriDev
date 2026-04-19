package dev.ori.feature.onboarding.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.icons.lucide.Bell
import dev.ori.core.ui.icons.lucide.Check
import dev.ori.core.ui.icons.lucide.FingerprintPattern
import dev.ori.core.ui.icons.lucide.FolderOpen
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.OriDevTheme
import dev.ori.core.ui.theme.StatusConnected
import dev.ori.feature.onboarding.OnboardingViewModel

/**
 * Phase 11 P4.8 / Phase 15 Task 15.6 — onboarding permissions screen.
 *
 * v0 only surfaced `POST_NOTIFICATIONS`. Phase 11 expanded it to a list of
 * three cards (Notifications, Biometric, File access) but the file-access
 * card was a dead-end "deep-link to app settings" placeholder — the user
 * had no way to actually grant storage access from onboarding.
 *
 * Phase 15 Task 15.6 replaces that placeholder with a real Storage Access
 * Framework picker:
 *
 * 1. **Mitteilungen** (`POST_NOTIFICATIONS`, runtime on API 33+).
 * 2. **Biometrie** (informational).
 * 3. **Speicherordner** — SAF `ActivityResultContracts.OpenDocumentTree`
 *    launcher. On success we persist the URI via
 *    [OnboardingViewModel.grantStorageTree] (same code path as Settings
 *    and the File Manager). Card flips to the green-check "granted"
 *    state once at least one tree is recorded.
 *
 * The footer still keeps the "Überspringen" fallback for users who want
 * to defer permission grants to later — they can always come back via
 * Settings → Speicherzugriff.
 */
@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }

    val grantedTrees by viewModel.grantedTrees.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val grantedByLauncher = results[Manifest.permission.POST_NOTIFICATIONS] == true
        notificationsGranted = grantedByLauncher || hasNotificationPermission(context)
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.grantStorageTree(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        notificationsGranted = hasNotificationPermission(context)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Berechtigungen",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Ori:Dev benötigt einige Berechtigungen, damit Übertragungen, " +
                    "Benachrichtigungen und biometrische Entsperrung funktionieren.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray500,
            )
            Spacer(Modifier.height(8.dp))

            PermissionCard(
                icon = LucideIcons.Bell,
                title = "Mitteilungen",
                description = "Fortschritt laufender Übertragungen und Verbindungs-Warnungen anzeigen.",
                granted = notificationsGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    } else {
                        notificationsGranted = true
                    }
                },
            )

            PermissionCard(
                icon = LucideIcons.FingerprintPattern,
                title = "Biometrie",
                description = "Passwörter und SSH-Schlüssel mit Fingerabdruck/Gesicht entsperren. " +
                    "Wird aktiviert, sobald die erste Verbindung gespeichert wird.",
                granted = true,
                onRequest = null,
            )

            // Phase 15 Task 15.6 — real SAF picker card.
            PermissionCard(
                icon = LucideIcons.FolderOpen,
                title = "Speicherordner",
                description = if (grantedTrees.isEmpty()) {
                    "Ori:Dev braucht einen Ordner, den es lesen/schreiben darf. " +
                        "Tippe auf \"Ordner auswählen\", um den Systemdialog zu öffnen. " +
                        "Du kannst später in den Einstellungen weitere hinzufügen oder entfernen."
                } else {
                    val names = grantedTrees.take(3).joinToString(", ") { it.displayName }
                    val suffix = if (grantedTrees.size > 3) " +${grantedTrees.size - 3}" else ""
                    "Freigegeben: $names$suffix"
                },
                granted = grantedTrees.isNotEmpty(),
                onRequest = { safLauncher.launch(null) },
                actionLabel = if (grantedTrees.isEmpty()) "Ordner auswählen" else "Weiteren hinzufügen",
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Weiter")
            }
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Überspringen")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onRequest: (() -> Unit)?,
    actionLabel: String = "Erlauben",
) {
    OriCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500,
                )
                // Phase 15 Task 15.6 — even when "granted", the storage
                // card keeps its action button so the user can add more
                // folders. This is the same launcher pattern as the
                // Settings section.
                if (granted && onRequest != null && actionLabel != "Erlauben") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequest) {
                        Text(actionLabel)
                    }
                }
            }
            if (granted) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideIcons.Check,
                        contentDescription = "Erteilt",
                        tint = StatusConnected,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else if (onRequest != null) {
                Button(onClick = onRequest) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    OriDevTheme {
        PermissionsScreen(onContinue = {})
    }
}
