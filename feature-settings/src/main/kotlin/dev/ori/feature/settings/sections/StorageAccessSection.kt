package dev.ori.feature.settings.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.FolderOpen
import dev.ori.core.ui.icons.lucide.FolderPlus
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Trash2
import dev.ori.core.ui.theme.Gray500
import dev.ori.domain.model.GrantedTree
import dev.ori.feature.settings.components.SettingsCard
import dev.ori.feature.settings.components.SettingsRow

/**
 * Phase 15 Task 15.6 — Settings entry that lists the SAF-granted trees
 * and lets the user add or remove them.
 *
 * ## Why this section exists
 *
 * Before Task 15.6 there was nowhere in the app (outside the one-shot
 * onboarding step) to see which folders Ori:Dev could access, let alone
 * revoke them. The user-reported pain was literally *"In den
 * Einstellungen gibt es keinen Punkt um Berechtigungen nochmal nachträglich
 * zu setzen / prüfen."* This section is the durable answer.
 *
 * ## Launcher pattern
 *
 * The "Add folder…" button uses [ActivityResultContracts.OpenDocumentTree]
 * — the same contract the file-manager CTA uses. Result is dispatched
 * to [onGrantTree] which in turn calls
 * `SettingsViewModel.grantStorageTree`. The system picker takes care of
 * the permissions prompt; Ori:Dev just persists the URI.
 */
@Composable
internal fun StorageAccessSection(
    grantedTrees: List<GrantedTree>,
    onGrantTree: (String) -> Unit,
    onRevokeTree: (String) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            onGrantTree(uri.toString())
        }
    }

    SettingsCard(sectionLabel = "Speicherzugriff") {
        if (grantedTrees.isEmpty()) {
            SettingsRow(
                icon = LucideIcons.FolderOpen,
                title = "Kein Ordner freigegeben",
                subtitle = "Tippe auf \"Ordner hinzufügen\", um Ori:Dev Zugriff auf einen lokalen Ordner zu erteilen.",
            )
        } else {
            grantedTrees.forEach { tree ->
                SettingsRow(
                    icon = LucideIcons.FolderOpen,
                    title = tree.displayName,
                    subtitle = tree.uri,
                    trailing = {
                        IconButton(onClick = { onRevokeTree(tree.uri) }) {
                            Icon(
                                imageVector = LucideIcons.Trash2,
                                contentDescription = "Zugriff entziehen",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
            }
        }
        SettingsRow(
            icon = LucideIcons.FolderPlus,
            title = "Ordner hinzufügen…",
            subtitle = "System-Dialog zum Auswählen eines Ordners",
            onClick = { launcher.launch(null) },
        )
        // Design hint for screen-reader readers — explains why a "remove"
        // action is safe (the grant is revoked system-wide; no user data
        // is deleted).
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Zugriff wird nur für die ausgewählten Ordner erteilt. " +
                    "Beim Entfernen werden keine Dateien gelöscht, nur die " +
                    "Berechtigung zurückgezogen.",
                style = MaterialTheme.typography.labelSmall,
                color = Gray500,
            )
        }
    }
}
