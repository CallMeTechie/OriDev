package dev.ori.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.WearAppViewModel
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
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
            )
            Text(
                text = "Expires in ${secondsRemaining}s",
                style = MaterialTheme.typography.labelSmall,
            )
            FilledTonalButton(
                onClick = { viewModel.respondTo2Fa(current.requestId, approved = true) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Approve") },
            )
            Button(
                onClick = { viewModel.respondTo2Fa(current.requestId, approved = false) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                label = { Text("Deny") },
            )
        }
    }
}
