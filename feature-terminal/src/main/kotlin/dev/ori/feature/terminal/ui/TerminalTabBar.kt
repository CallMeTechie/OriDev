package dev.ori.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Plus
import dev.ori.core.ui.icons.lucide.X
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Indigo500
import dev.ori.core.ui.theme.StatusConnected
import dev.ori.core.ui.theme.StatusDisconnected

// Phase 11 P2.1-polish — replaced hardcoded hex #E5E7EB with the Gray200
// theme token so the tab bar border tracks the rest of the palette.
private val TabBarBackground = Color.White
private val TabBarBorder = Gray200

@Composable
fun TerminalTabBar(
    tabs: List<TerminalTabState>,
    activeTabIndex: Int,
    onTabSelect: (Int) -> Unit,
    onTabClose: (String) -> Unit,
    onAddTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(TabBarBackground)
            .border(width = 1.dp, color = TabBarBorder)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isActive = index == activeTabIndex
            val indicatorColor = Indigo500

            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (isActive) {
                            Modifier.drawBehind {
                                drawLine(
                                    color = indicatorColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 3.dp.toPx(),
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onTabSelect(index) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (tab.isConnected) StatusConnected else StatusDisconnected,
                            shape = CircleShape,
                        ),
                )

                Text(
                    text = tab.serverName,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                IconButton(
                    onClick = { onTabClose(tab.id) },
                    modifier = Modifier.size(20.dp),
                ) {
                    // Phase 11 P2.1-polish — LucideIcons.X replaces Material Close
                    // (forbidden-imports policy).
                    Icon(
                        imageVector = LucideIcons.X,
                        contentDescription = "Close tab",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Add tab button
        IconButton(
            onClick = onAddTab,
            modifier = Modifier.size(36.dp),
        ) {
            // Phase 11 P2.1-polish — LucideIcons.Plus replaces Material Add.
            Icon(
                imageVector = LucideIcons.Plus,
                contentDescription = "New tab",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
