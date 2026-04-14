package dev.ori.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.GreenBg
import dev.ori.core.ui.theme.StatusConnected

/**
 * Phase 11 P4.7 — compact pill shown in [OriTopBar]'s `indicator` slot when
 * a foreground service (active transfers, background sync, etc.) is running.
 *
 * Renders a 6 dp pulsing dot in [StatusConnected] green (pulse between 0.5
 * and 1.0 alpha over 1200 ms, infinite) and a short label like "2 transfers"
 * inside a [GreenBg]-tinted pill.
 *
 * Accessibility: the whole pill merges into one node with a German
 * contentDescription reading out [count] and [label].
 */
@Composable
public fun OriServiceIndicator(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val transition = rememberInfiniteTransition(label = "OriServiceIndicator")
    val pulse by transition.animateFloat(
        initialValue = PULSE_MIN,
        targetValue = PULSE_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "OriServiceIndicator.pulse",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(GreenBg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$count $label aktiv"
            },
    ) {
        Box(
            modifier = Modifier
                .size(DOT_SIZE.dp)
                .alpha(pulse)
                .background(color = StatusConnected, shape = CircleShape),
        )
        Text(
            text = "  $count $label",
            style = MaterialTheme.typography.labelSmall,
            color = StatusConnected,
        )
    }
}

private const val DOT_SIZE = 6
private const val PULSE_MIN = 0.5f
private const val PULSE_MAX = 1.0f
private const val PULSE_DURATION_MS = 1200
