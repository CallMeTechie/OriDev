package dev.ori.feature.onboarding.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import dev.ori.core.ui.components.OriCard
import dev.ori.core.ui.icons.lucide.Bell
import dev.ori.core.ui.icons.lucide.Check
import dev.ori.core.ui.icons.lucide.FingerprintPattern
import dev.ori.core.ui.icons.lucide.FolderOpen
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.OriDevTheme
import dev.ori.core.ui.theme.StatusConnected

/**
 * Phase 11 P4.8 — expanded onboarding permissions screen.
 *
 * v0 only surfaced `POST_NOTIFICATIONS` in a single info blurb. The expanded
 * version renders a scrollable list of permission cards so the user sees
 * every sensitive capability Ori:Dev cares about before granting it:
 *
 * 1. **Mitteilungen** (`POST_NOTIFICATIONS`, runtime on API 33+)
 *    — requested via [ActivityResultContracts.RequestMultiplePermissions].
 * 2. **Biometrie** (informational — no runtime prompt; resolved the first
 *    time the user enables biometric unlock in Settings).
 * 3. **Dateizugriff** (informational — Android 11+ uses SAF, no broad
 *    storage permission to request. Deep-link to system settings for users
 *    who need all-files access.)
 *
 * Each card shows a Lucide icon, the permission name, a one-line rationale,
 * and a status chip (granted = green check, denied = "Erlauben" button). The
 * footer keeps the "Überspringen" fallback for users who want to defer
 * permission grants to later.
 */
@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val grantedByLauncher = results[Manifest.permission.POST_NOTIFICATIONS] == true
        notificationsGranted = grantedByLauncher || hasNotificationPermission(context)
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

            PermissionCard(
                icon = LucideIcons.FolderOpen,
                title = "Dateizugriff",
                description = "Lokale Datei-Pane nutzt den Android Storage Access Framework-Dialog. " +
                    "Für systemweiten Zugriff in den Einstellungen freischalten.",
                granted = false,
                onRequest = { openAppSettings(context) },
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
                    Text("Erlauben")
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

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    OriDevTheme {
        PermissionsScreen(onContinue = {})
    }
}
