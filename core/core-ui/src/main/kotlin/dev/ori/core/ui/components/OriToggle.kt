package dev.ori.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray300
import dev.ori.core.ui.theme.Indigo500

/**
 * Mockup-aligned toggle switch — 44 × 26 dp track with a 20 dp thumb. Replaces
 * `androidx.compose.material3.Switch` (which is 76 × 32 dp on/off thumb area
 * and looks oversized in tight settings list rows).
 *
 * Off color: [Gray300] (`#D1D5DB`). On color: [Indigo500] (`#6366F1`).
 * Animates the thumb position and track color on toggle.
 */
@Composable
public fun OriToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Indigo500 else Gray300,
        label = "OriToggle.trackColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        label = "OriToggle.thumbOffset",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor)
            .padding(3.dp),
    ) {
        Surface(
            modifier = Modifier
                .size(20.dp)
                .offset(x = thumbOffset),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 1.dp,
            content = {},
        )
    }
}
