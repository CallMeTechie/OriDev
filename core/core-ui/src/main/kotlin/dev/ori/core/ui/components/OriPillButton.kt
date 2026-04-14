@file:Suppress("MatchingDeclarationName")

package dev.ori.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Gray700
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.StatusDisconnected

/** Variant for [OriPillButton]. */
@Immutable
public enum class OriPillButtonVariant { Default, Primary, Danger }

/**
 * Small text-pill button used for transfer-queue actions ("Pause", "Resume",
 * "Cancel", "Retry", "Clear Completed"), VM action pills, and other compact
 * affordances. 11.5 sp / 500 weight, 4 dp × 12 dp padding, 8 dp radius. Variants
 * change the border + text color: [Default] is neutral, [Primary] uses
 * [Indigo500], [Danger] uses [StatusDisconnected].
 *
 * Replaces the v0 pattern of `IconButton` for transfer actions, which the
 * mockup explicitly shows as text labels — not icon-only.
 */
@Composable
public fun OriPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: OriPillButtonVariant = OriPillButtonVariant.Default,
    enabled: Boolean = true,
) {
    val (textColor, borderColor) = when (variant) {
        OriPillButtonVariant.Default -> Gray700 to Gray200
        OriPillButtonVariant.Primary -> Indigo500 to Indigo500
        OriPillButtonVariant.Danger -> StatusDisconnected to StatusDisconnected
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = textColor,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
    }
    @Suppress("UnusedExpression")
    BorderStroke(1.dp, borderColor)
}
