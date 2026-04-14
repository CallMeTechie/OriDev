package dev.ori.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray200

/**
 * Mockup-aligned card primitive. Sits on white (the new `surface` color after
 * PR 2's swap), bordered with a 1 dp [Gray200] outline, rounded with the
 * theme's `large` shape (14 dp per mockup `--radius` — corrected from M3's
 * default 16 dp in PR 2).
 *
 * Replaces every `androidx.compose.material3.Card` usage in feature modules
 * where the mockup shows a flat, bordered card (i.e. all of them — Phase 11
 * design has no elevation shadows).
 */
@Composable
public fun OriCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: BorderStroke = BorderStroke(1.dp, Gray200),
    color: Color = Color.White,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = if (onClick != null) {
            modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            modifier
        },
        shape = MaterialTheme.shapes.large,
        color = color,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}
