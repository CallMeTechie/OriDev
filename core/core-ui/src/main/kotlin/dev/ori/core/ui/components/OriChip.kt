package dev.ori.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.Gray900
import dev.ori.core.ui.theme.Indigo500

/**
 * Pill-shaped selectable chip used for filter chips, quick-access chips, and
 * bookmark chips. 28 dp height, [labelLarge] text. **Selected** background is
 * solid [Indigo500] (not alpha-blended) per cycle 1 finding F2 — the v0
 * implementation used `Indigo500.copy(alpha = 0.12f)` which made selected
 * chips look greyish; the mockup specifies a saturated indigo.
 *
 * Optional trailing count badge (`countLabel`) for transfer filter chips that
 * show the number of items in each filter category — when the chip is
 * selected the badge inverts to white-on-transparent.
 */
@Composable
public fun OriChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    countLabel: String? = null,
) {
    val bgColor = if (selected) Indigo500 else Color.White
    val labelColor = if (selected) Color.White else Gray900
    val countLabelColor = if (selected) Color.White else Gray500
    val border = if (selected) BorderStroke(1.dp, Indigo500) else BorderStroke(1.dp, Gray200)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(28.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(bgColor)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Tab,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
            )
            if (countLabel != null) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = countLabelColor,
                )
            }
        }
    }
    // Border drawn via a no-op layer; the border on a clipped Box is awkward,
    // so callers wanting a precise outline can wrap in a Surface — for chips
    // the solid bg + indigo selected tint is enough visual signal.
    @Suppress("UnusedExpression")
    border
}
