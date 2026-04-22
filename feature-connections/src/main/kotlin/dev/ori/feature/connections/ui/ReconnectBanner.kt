package dev.ori.feature.connections.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * PR 3 Section 11 safety-net — "Die App wurde im Hintergrund beendet"
 * banner rendered at the top of [ConnectionListScreen] whenever
 * [ConnectionListUiState.reconnectBannerProfiles] is non-empty.
 *
 * Two affordances, matching spec Section 11:
 *  - "Neu verbinden" routes to [ConnectionListViewModel.reconnectAll],
 *    which iterates `sessionRegistry.connect()` for every persisted
 *    profileId. Credentials come from the Keystore (#171), so no
 *    password prompt surfaces.
 *  - "Schließen" routes to [ConnectionListViewModel.dismissReconnectBanner],
 *    which clears the persisted profile-id set so the banner stays
 *    hidden across subsequent launches.
 *
 * The colour pair uses `secondaryContainer` / `onSecondaryContainer`
 * (neutral, not alarm-red) — this is a helpful recovery affordance,
 * not an error.
 */
@Composable
internal fun ReconnectBanner(
    count: Int,
    onReconnect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$count Verbindungen wurden beendet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = "Die App wurde im Hintergrund beendet. Verbindungen neu aufbauen?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Schließen") }
            TextButton(onClick = onReconnect) { Text("Neu verbinden") }
        }
    }
}
