package dev.ori.feature.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.PremiumGold
import dev.ori.domain.model.BandwidthLimit
import kotlin.math.roundToInt

@Composable
fun BandwidthThrottleSlider(
    currentKbps: Int?,
    isPremium: Boolean,
    onValueChange: (Int?) -> Unit,
    onUpgradeTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = BandwidthLimit.PRESETS
    // Index 0..presets.size => 0..size-1 are presets, size = unlimited
    val totalSteps = presets.size
    val currentIndex = if (currentKbps == null || currentKbps == 0) {
        totalSteps.toFloat()
    } else {
        presets.indexOf(currentKbps).takeIf { it >= 0 }?.toFloat() ?: totalSteps.toFloat()
    }

    var sliderPosition by remember(currentIndex) { mutableFloatStateOf(currentIndex) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Bandwidth-Limit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!isPremium) {
                Text(
                    text = "PREMIUM",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumGold,
                    modifier = Modifier
                        .background(
                            color = PremiumGold.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box {
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    val idx = sliderPosition.roundToInt()
                    val value = if (idx >= presets.size) null else presets[idx]
                    onValueChange(value)
                },
                valueRange = 0f..totalSteps.toFloat(),
                steps = totalSteps - 1,
                enabled = isPremium,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isPremium) 1f else 0.45f),
                colors = SliderDefaults.colors(
                    thumbColor = Indigo500,
                    activeTrackColor = Indigo500,
                ),
            )
            if (!isPremium) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClick = onUpgradeTap)
                        .background(Color.Transparent),
                )
            }
        }

        val label = run {
            val idx = sliderPosition.roundToInt()
            if (idx >= presets.size) {
                "Unbegrenzt"
            } else {
                val kbps = presets[idx]
                if (kbps >= 1024) "${kbps / 1024} MB/s" else "$kbps KB/s"
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
