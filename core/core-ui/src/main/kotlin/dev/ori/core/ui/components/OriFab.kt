package dev.ori.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Indigo500

/**
 * 52 × 52 dp icon-only floating action button with a 16 dp corner radius.
 * Replaces `androidx.compose.material3.FloatingActionButton` (which defaults to
 * 56 dp + 16 dp radius and a slightly different shadow). Sits inside a screen's
 * own `Scaffold { floatingActionButton = { OriFab(...) } }` slot — Phase 11
 * keeps Scaffold per-screen rather than centralising it.
 */
@Composable
public fun OriFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Indigo500)
            .clickable(role = Role.Button, onClick = onClick),
        color = Indigo500,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
    }
}
