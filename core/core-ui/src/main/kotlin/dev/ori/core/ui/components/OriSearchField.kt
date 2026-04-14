package dev.ori.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Search
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Gray400
import dev.ori.core.ui.theme.Gray900
import dev.ori.core.ui.theme.Indigo500

/**
 * 40 dp height search input with a leading 16 dp Lucide [Search] icon. 8 dp
 * border radius, 1 dp [Gray200] border that becomes [Indigo500] on focus.
 *
 * Replaces `OutlinedTextField` for search use cases — `OutlinedTextField` is
 * 56 dp tall by default which doesn't match the mockup `connection-manager.html`
 * spec.
 */
@Composable
public fun OriSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) Indigo500 else Gray200

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = LucideIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Gray400,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray400,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    interactionSource = interactionSource,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyMedium.copy(color = Gray900),
                    ),
                    cursorBrush = SolidColor(Indigo500),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
