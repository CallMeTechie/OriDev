package dev.ori.feature.onboarding.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import dev.ori.core.ui.theme.OriDevTheme

@Composable
fun BatteryOptimizationScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val alreadyIgnoring = remember(context) { isIgnoringBatteryOptimizations(context) }

    LaunchedEffect(alreadyIgnoring) {
        if (alreadyIgnoring) {
            onContinue()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Akku-Optimierung",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Damit SFTP-\u00dcbertragungen im Hintergrund nicht abgebrochen " +
                    "werden, sollte Ori:Dev von der Akku-Optimierung ausgenommen werden. " +
                    "Du kannst diese Einstellung jederzeit in den System-Einstellungen \u00e4ndern.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = {
                    runCatching {
                        @Suppress("BatteryLife")
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ausnahme hinzuf\u00fcgen")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\u00dcberspringen")
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService<PowerManager>() ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Preview(showBackground = true)
@Composable
private fun BatteryOptimizationScreenPreview() {
    OriDevTheme {
        BatteryOptimizationScreen(onContinue = {})
    }
}
