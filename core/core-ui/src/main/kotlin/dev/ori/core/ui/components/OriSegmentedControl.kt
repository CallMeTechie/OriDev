package dev.ori.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Gray700
import dev.ori.core.ui.theme.Indigo500

/**
 * Pill-bordered segmented control used for the auth-method toggle in the
 * Add/Edit Connection form. Each segment is a button; the selected segment
 * has an indigo background and white text.
 *
 * Replaces `androidx.compose.material3.SegmentedButton` (which has a fixed
 * Material chrome that doesn't match the mockup spec).
 */
@Composable
public fun <T> OriSegmentedControl(
    options: List<T>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .border(width = 1.dp, color = Gray200, shape = MaterialTheme.shapes.small),
    ) {
        options.forEach { option ->
            val selected = option == selectedValue
            val bgColor = if (selected) Indigo500 else Color.Transparent
            val textColor = if (selected) Color.White else Gray700
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(bgColor)
                    .clickable(role = Role.Tab) { onValueChange(option) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = optionLabel(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                )
            }
        }
    }
    @Suppress("UnusedExpression")
    Arrangement.SpaceEvenly
}
