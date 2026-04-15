package dev.ori.wear.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.WearAppViewModel
import dev.ori.wear.ui.component.OriWearCard
import kotlinx.coroutines.delay

private const val COUNTDOWN_TICK_MS = 500L
private const val MS_PER_SECOND = 1000L

@Composable
fun TwoFactorDialogScreen(
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val request by viewModel.pending2Fa.collectAsStateWithLifecycle()
    val current = request ?: return

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(current.requestId) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= current.expiresAtMillis) {
                viewModel.respondTo2Fa(current.requestId, approved = false)
                break
            }
            delay(COUNTDOWN_TICK_MS)
        }
    }

    val secondsRemaining = ((current.expiresAtMillis - nowMs) / MS_PER_SECOND)
        .coerceAtLeast(0L)

    // Phase 11 P3.2 (T2c) — 2FA prompt wrapped in OriWearCard with an accent
    // ring to match the `.s4-card.approval` highlight from Mockups/watch.html.
    // The host (monospace) uses the Indigo accent so the identifier being
    // approved stands out on the OLED background.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        OriWearCard(
            modifier = Modifier.fillMaxWidth(),
            accentBorder = true,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Approve connection?",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = current.serverName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = current.host,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Expires in ${secondsRemaining}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OriWearCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.respondTo2Fa(current.requestId, approved = true)
                            },
                        accentBorder = true,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Approve",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    OriWearCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.respondTo2Fa(current.requestId, approved = false)
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Deny",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
