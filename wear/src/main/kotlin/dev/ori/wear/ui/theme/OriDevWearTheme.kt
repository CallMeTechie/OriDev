package dev.ori.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Wear OS theme wrapper. Uses the default Wear Material3 dark scheme which is
 * appropriate for OLED watch faces (true black saves battery).
 */
@Composable
fun OriDevWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
