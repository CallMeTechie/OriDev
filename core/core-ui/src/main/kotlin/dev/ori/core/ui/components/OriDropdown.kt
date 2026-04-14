package dev.ori.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.ChevronDown
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Gray400
import dev.ori.core.ui.theme.Gray700
import dev.ori.core.ui.theme.Gray900

/**
 * Compact dropdown selector with the same visual envelope as [OriInput] (40 dp
 * height, 8 dp radius, 1 dp [Gray200] border, optional label above). Anchors a
 * [DropdownMenu] underneath itself when tapped.
 *
 * Replaces `ExposedDropdownMenuBox` which has a fixed 56 dp height and built-in
 * Material chrome that doesn't match the mockup form fields.
 */
@Composable
public fun <T> OriDropdown(
    selectedValue: T?,
    onValueChange: (T) -> Unit,
    options: List<T>,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Gray700,
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = Gray200,
                        shape = MaterialTheme.shapes.small,
                    )
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = selectedValue?.let(optionLabel) ?: placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedValue == null) Gray400 else Gray900,
                )
                Icon(
                    imageVector = LucideIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Gray400,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = optionLabel(option)) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
