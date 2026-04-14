package dev.ori.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray500
import dev.ori.core.ui.theme.Gray900
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.IndigoBg

/**
 * Single settings list row matching the `settings.html` mockup spec:
 * - 52 dp min height
 * - 14 / 16 dp padding (vertical / horizontal)
 * - 32 dp icon container with 6 dp radius, [IndigoBg] background, [Indigo500] icon
 * - Title in `bodyMedium`, subtitle in `labelMedium` tertiary
 * - Optional trailing slot for a toggle / dropdown / chevron / version label
 *
 * Used by all 7 settings sections (Account/Premium, Appearance, Terminal,
 * Transfers, Security, Notifications, About). The trailing slot is
 * intentionally generic — callers pass an `OriToggle`, `OriDropdown`, plain
 * `Text`, or `OriIconButton(ChevronRight, ...)` depending on the row type.
 */
@Composable
public fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
        .padding(horizontal = 16.dp, vertical = 14.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(IndigoBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Indigo500,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Gray900,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Gray500,
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}
