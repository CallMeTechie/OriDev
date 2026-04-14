package dev.ori.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.Check
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.theme.Gray300
import dev.ori.core.ui.theme.Indigo500

/**
 * Custom 16 × 16 dp checkbox for the file manager multi-select column and any
 * other compact list selection. 2 dp border in [Gray300] when unchecked, fills
 * with [Indigo500] when checked, with a 12 dp white check icon.
 *
 * Replaces `androidx.compose.material3.Checkbox` which has a fixed 20 dp visual
 * size and a 48 dp touch target — too big for the tight `FileItemRow` layout
 * but the touch target is preserved here via [Modifier.minimumInteractiveComponentSize].
 *
 * Cycle 4 finding #16: definitively added to P0.5 (was previously left as a
 * "retroactive if needed" question in v3).
 */
@Composable
public fun OriCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .size(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (checked) Indigo500 else Color.White)
            .border(
                width = 2.dp,
                color = if (checked) Indigo500 else Gray300,
                shape = RoundedCornerShape(3.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = LucideIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.White,
            )
        }
    }
}
