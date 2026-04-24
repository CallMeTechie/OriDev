package dev.ori.feature.terminal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Plus
import dev.ori.core.ui.icons.lucide.X
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.StatusConnected
import dev.ori.core.ui.theme.StatusDisconnected

// Phase 11 P2.1-polish — replaced hardcoded hex #E5E7EB with the Gray200
// theme token so the tab bar border tracks the rest of the palette.
private val TabBarBackground = Color.White
private val TabBarBorder = Gray200

/**
 * Foldable split-terminal Task 12 — dual-underline tab bar with
 * auto-scroll and long-press "move to pane" popup.
 *
 * Underline rules (in split mode):
 * - Tab bound to the FOCUSED pane → 2 dp primary underline.
 * - Tab bound to the OTHER pane   → 1 dp outlineVariant underline.
 * - Any other tab                 → no underline.
 *
 * In single-pane mode (!isSplitActive) the focused-pane tab is the
 * classic activeTabIndex-selected one, so the 2 dp underline still
 * shows a single focus indicator and the other-pane underline path is
 * inert.
 *
 * Auto-scroll: whenever the focused pane's tab changes, the LazyRow
 * animates to the matching index so the user never has to scroll to
 * see which tab is live.
 *
 * Long-press (split-only): opens a [DropdownMenu] anchored at the
 * long-pressed tab with "move to left", "move to right", "close"
 * actions. Dismissed automatically when the device folds back to
 * single-pane via the [LaunchedEffect] guarded on [isSplitActive].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalTabBar(
    tabs: List<TerminalTabState>,
    activeTabIndex: Int,
    onTabSelect: (Int) -> Unit,
    onTabClose: (String) -> Unit,
    onAddTab: () -> Unit,
    leftPaneTabId: String?,
    rightPaneTabId: String?,
    activePaneIndex: Int,
    isSplitActive: Boolean,
    onMoveTabToPane: (tabId: String, pane: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusedPaneTabId = when (activePaneIndex) {
        1 -> rightPaneTabId
        else -> leftPaneTabId
    }
    val otherPaneTabId = when (activePaneIndex) {
        1 -> leftPaneTabId
        else -> rightPaneTabId
    }

    val listState = rememberLazyListState()
    LaunchedEffect(focusedPaneTabId, tabs) {
        if (focusedPaneTabId != null) {
            val index = tabs.indexOfFirst { it.id == focusedPaneTabId }
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }

    var popupTabId by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss the long-press popup the moment the device folds back
    // to single-pane — the "move to pane" actions are meaningless there.
    LaunchedEffect(isSplitActive) {
        if (!isSplitActive) popupTabId = null
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(TabBarBackground)
            .border(width = 1.dp, color = TabBarBorder)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items = tabs, key = { it.id }) { tab ->
            val index = tabs.indexOfFirst { it.id == tab.id }
            val isSingleActive = !isSplitActive && index == activeTabIndex

            val underlineColor = when (tab.id) {
                focusedPaneTabId -> MaterialTheme.colorScheme.primary
                otherPaneTabId -> MaterialTheme.colorScheme.outlineVariant
                else -> if (isSingleActive) MaterialTheme.colorScheme.primary else Color.Transparent
            }
            val underlineHeight = when (tab.id) {
                focusedPaneTabId -> 2.dp
                otherPaneTabId -> 1.dp
                else -> if (isSingleActive) 2.dp else 0.dp
            }

            val isHighlighted = tab.id == focusedPaneTabId || isSingleActive

            Box {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = { onTabSelect(index) },
                            onLongClick = { if (isSplitActive) popupTabId = tab.id },
                        )
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
                        color = if (isHighlighted) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )

                    IconButton(
                        onClick = { onTabClose(tab.id) },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            imageVector = LucideIcons.X,
                            contentDescription = "Close tab",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Dual-underline — painted as a bottom-aligned Spacer so
                // height=0 (unbound tabs) takes zero layout space. Color
                // Transparent keeps the Spacer invisible when no pane
                // claims the tab.
                Spacer(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(underlineHeight)
                        .background(underlineColor),
                )

                // Long-press popup — anchored to this tab's Box so it
                // positions over the pressed cell. Rendered inline so
                // DropdownMenu's positioning picks up the cell's bounds.
                val currentPopupTabId = popupTabId
                if (currentPopupTabId == tab.id && isSplitActive) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { popupTabId = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("In linken Pane bewegen") },
                            onClick = {
                                onMoveTabToPane(currentPopupTabId, 0)
                                popupTabId = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("In rechten Pane bewegen") },
                            onClick = {
                                onMoveTabToPane(currentPopupTabId, 1)
                                popupTabId = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Schließen") },
                            onClick = {
                                onTabClose(currentPopupTabId)
                                popupTabId = null
                            },
                        )
                    }
                }
            }
        }

        // Add tab button
        item(key = "__add_tab__") {
            IconButton(
                onClick = onAddTab,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = LucideIcons.Plus,
                    contentDescription = "New tab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
