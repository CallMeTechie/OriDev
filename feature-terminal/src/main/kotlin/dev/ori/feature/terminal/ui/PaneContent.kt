package dev.ori.feature.terminal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.Grid2x2
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Plus

/**
 * Renders a single pane's terminal body. Stateless: the focus flag +
 * active-flag are passed in; onTap requests a focus switch from the
 * caller's ViewModel.
 *
 * When [tab] is null, renders an empty-state with "Neuer Tab hier
 * öffnen" as the first row of a picker BottomSheet, then all tabs not
 * currently bound to a pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaneContent(
    tab: TerminalTabState?,
    isFocused: Boolean,
    isSplitActive: Boolean,
    allTabs: List<TerminalTabState>,
    leftPaneTabId: String?,
    rightPaneTabId: String?,
    paneContentDescription: String,
    traversalPriority: Float,
    onTap: () -> Unit,
    onPickTab: (String) -> Unit,
    onNewTabInThisSlot: () -> Unit,
    modifier: Modifier = Modifier,
    sessionBody: @Composable (TerminalTabState) -> Unit,
) {
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isFocused) 2.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(width = borderWidth, color = borderColor)
            .clickable(onClick = onTap)
            .semantics {
                contentDescription = paneContentDescription
                traversalIndex = traversalPriority
                if (!isFocused && isSplitActive) liveRegion = LiveRegionMode.Polite
            },
    ) {
        if (tab == null) {
            var pickerOpen by remember { mutableStateOf(false) }
            EmptyPaneBody(onPickTap = { pickerOpen = true })
            if (pickerOpen) {
                val sheetState = rememberModalBottomSheetState()
                ModalBottomSheet(
                    onDismissRequest = { pickerOpen = false },
                    sheetState = sheetState,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = {
                            onNewTabInThisSlot()
                            pickerOpen = false
                        }) {
                            Icon(LucideIcons.Plus, contentDescription = null)
                            Text(
                                text = "Neuer Tab hier öffnen",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        val candidates = allTabs.filter {
                            it.id != leftPaneTabId && it.id != rightPaneTabId
                        }
                        candidates.forEach { candidate ->
                            TextButton(onClick = {
                                onPickTab(candidate.id)
                                pickerOpen = false
                            }) {
                                Text(text = candidate.displayLabel())
                            }
                        }
                    }
                }
            }
        } else {
            sessionBody(tab)
        }
    }
}

@Composable
private fun EmptyPaneBody(
    onPickTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onPickTap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = LucideIcons.Grid2x2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "Tab auswählen",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Display-label helper — reads the human-readable field from
 * [TerminalTabState]. Keeps the field reference in one place so the
 * rest of the composable stays decoupled from the data-class shape.
 */
private fun TerminalTabState.displayLabel(): String = this.serverName
