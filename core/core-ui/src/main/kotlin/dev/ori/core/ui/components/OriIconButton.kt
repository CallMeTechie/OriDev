package dev.ori.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 32 × 32 dp visual icon button, with a 48 dp accessibility tap target enforced
 * via [Modifier.minimumInteractiveComponentSize]. Per Phase 11 plan v6 §P0.5 +
 * cycle 1 finding F19, this is *visually* compact (matching the mockup density)
 * while remaining accessibility-compliant.
 *
 * Use this everywhere a feature module previously reached for `IconButton` from
 * Material 3 — `IconButton` is 48 dp visual, which is too large for the tight
 * mockup layouts.
 */
@Composable
public fun OriIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = OriIconButtonDefaults.Size,
    iconSize: Dp = OriIconButtonDefaults.IconSize,
    tint: Color = LocalContentColor.current,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = tint,
        )
    }
}

public object OriIconButtonDefaults {
    /** Default visual size matching the mockup spec. */
    public val Size: Dp = 32.dp

    /** Default icon stroke size inside the button. */
    public val IconSize: Dp = 18.dp
}
