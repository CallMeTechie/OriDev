package dev.ori.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 8 × 8 dp coloured circle for connection / VM / generic status indication.
 * Optional pulse glow for the Wear connection screen (where the mockup shows
 * the status dot pulsing). Phone usage typically passes `pulse = false`.
 */
@Composable
public fun OriStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    pulse: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "OriStatusDot.pulse")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "OriStatusDot.alpha",
    )

    Canvas(modifier = modifier.size(size + if (pulse) 4.dp else 0.dp)) {
        if (pulse) {
            drawCircle(
                color = color.copy(alpha = glowAlpha * 0.4f),
                radius = this.size.minDimension / 2f,
                center = Offset(this.size.width / 2f, this.size.height / 2f),
            )
        }
        drawCircle(
            color = color,
            radius = size.toPx() / 2f,
            center = Offset(this.size.width / 2f, this.size.height / 2f),
        )
    }
}
