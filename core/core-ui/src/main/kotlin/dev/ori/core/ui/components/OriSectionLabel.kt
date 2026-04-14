package dev.ori.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray400
import dev.ori.core.ui.theme.OriTypography

/**
 * Section label used above grouped content blocks: "RECENT", "ALL SERVERS",
 * "FEATURE SCREENS", "ACCOUNT & PREMIUM", etc. Renders an uppercase 11 sp /
 * 600 weight / 0.08 em letter-spacing line in [Gray400] with a 4 dp left
 * padding and an 8 dp bottom margin (handled by callers via spacing).
 *
 * Uses the [OriTypography.overline] custom slot because Material 3 dropped
 * `overline` from its `Typography` shape when M3 launched.
 */
@Composable
public fun OriSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = OriTypography.overline,
        color = Gray400,
        modifier = modifier.padding(start = 4.dp),
    )
}
