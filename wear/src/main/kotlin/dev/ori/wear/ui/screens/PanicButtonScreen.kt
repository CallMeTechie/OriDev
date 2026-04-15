package dev.ori.wear.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.WearAppViewModel
import kotlinx.coroutines.delay

private const val PANIC_HOLD_DURATION_MS = 1500L
private const val PANIC_TICK_MS = 16L
private const val PANIC_RING_SIZE_DP = 160

@Composable
fun PanicButtonScreen(
    navController: NavHostController,
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    var pressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    LaunchedEffect(pressing) {
        if (pressing && !triggered) {
            val startTime = System.currentTimeMillis()
            while (pressing) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed.toFloat() / PANIC_HOLD_DURATION_MS).coerceAtMost(1f)
                if (progress >= 1f) {
                    triggered = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.sendPanicDisconnect()
                    navController.popBackStack()
                    break
                }
                delay(PANIC_TICK_MS)
            }
            if (!triggered) progress = 0f
        }
    }

    // Phase 11 P3.2 (T2c) — panic hold target stays a full-screen ring rather
    // than an OriWearCard because the mockup shows a circular hold surface,
    // not a rectangular list row. All colors (error ring, HOLD TO / PANIC
    // text) resolve through MaterialTheme.colorScheme, which is populated
    // from OriDevWearColors via OriDevWearTheme, so the palette stays in
    // sync with the rest of the Wear screens without hardcoded hex.
    ScreenScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressing = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            tryAwaitRelease()
                            pressing = false
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(PANIC_RING_SIZE_DP.dp),
                colors = androidx.wear.compose.material3.ProgressIndicatorDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.error,
                ),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "HOLD TO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "PANIC",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
