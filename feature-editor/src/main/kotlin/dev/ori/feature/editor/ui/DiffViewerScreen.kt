package dev.ori.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ori.core.ui.components.OriIconButton
import dev.ori.core.ui.components.OriTopBar
import dev.ori.core.ui.components.OriTopBarDefaults
import dev.ori.core.ui.icons.lucide.ChevronLeft
import dev.ori.core.ui.icons.lucide.Grid2x2
import dev.ori.core.ui.icons.lucide.List
import dev.ori.core.ui.icons.lucide.LucideIcons

private val AddedBackground = Color(0xFFD1FAE5)
private val RemovedBackground = Color(0xFFFEE2E2)

@Composable
fun DiffViewerScreen(
    viewModel: DiffViewerViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val title = if (uiState.oldTitle.isNotEmpty() || uiState.newTitle.isNotEmpty()) {
        "${uiState.oldTitle} -> ${uiState.newTitle}"
    } else {
        "Diff"
    }

    Scaffold(
        topBar = {
            // Phase 11 carry-over D — replaces deprecated OriDevTopBar with the
            // 40 dp HeightDense OriTopBar primitive, matching CodeEditorScreen.
            // Material ViewColumn/ViewList icons are swapped for Lucide
            // Grid2x2/List per the Phase 11 forbidden-imports policy.
            OriTopBar(
                title = title,
                height = OriTopBarDefaults.HeightDense,
                navigationIcon = if (onNavigateBack != null) {
                    {
                        OriIconButton(
                            icon = LucideIcons.ChevronLeft,
                            contentDescription = "Zurück",
                            onClick = onNavigateBack,
                        )
                    }
                } else {
                    null
                },
                actions = {
                    val isUnified = uiState.viewMode == DiffViewMode.UNIFIED
                    OriIconButton(
                        icon = if (isUnified) LucideIcons.Grid2x2 else LucideIcons.List,
                        contentDescription = if (isUnified) {
                            "Zu Seite-an-Seite-Ansicht wechseln"
                        } else {
                            "Zu einheitlicher Ansicht wechseln"
                        },
                        onClick = {
                            val next = if (isUnified) {
                                DiffViewMode.SIDE_BY_SIDE
                            } else {
                                DiffViewMode.UNIFIED
                            }
                            viewModel.onEvent(DiffViewerEvent.SetViewMode(next))
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    ErrorContent(
                        message = uiState.error ?: "",
                        onBack = onNavigateBack,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.diffLines.isEmpty() -> {
                    Text(
                        text = "No differences",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.viewMode == DiffViewMode.SIDE_BY_SIDE -> {
                    SideBySideDiff(lines = uiState.diffLines)
                }
                else -> {
                    UnifiedDiff(lines = uiState.diffLines)
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        if (onBack != null) {
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun UnifiedDiff(lines: List<DiffLine>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(lines) { line ->
            DiffLineRow(line = line)
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val background = when (line.type) {
        DiffType.ADDED -> AddedBackground
        DiffType.REMOVED -> RemovedBackground
        DiffType.MODIFIED -> AddedBackground
        DiffType.CONTEXT -> Color.Transparent
    }
    val prefix = when (line.type) {
        DiffType.ADDED, DiffType.MODIFIED -> "+"
        DiffType.REMOVED -> "-"
        DiffType.CONTEXT -> " "
    }
    val typeLabel = when (line.type) {
        DiffType.ADDED -> "Hinzugefügte Zeile"
        DiffType.REMOVED -> "Entfernte Zeile"
        DiffType.MODIFIED -> "Geänderte Zeile"
        DiffType.CONTEXT -> "Kontextzeile"
    }
    val lineNumberLabel = line.newLineNumber?.toString() ?: line.oldLineNumber?.toString() ?: ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .semantics(mergeDescendants = true) {
                contentDescription = "$typeLabel $lineNumberLabel, ${line.content}"
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = (line.oldLineNumber?.toString() ?: "").padStart(4),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = (line.newLineNumber?.toString() ?: "").padStart(4),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = prefix,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = line.content,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SideBySideDiff(lines: List<DiffLine>) {
    // Simplified side-by-side: render unified list but visually grouped.
    // A full side-by-side requires pairing REMOVED/ADDED lines; for now we
    // show two columns where each row shows old content (if any) and new
    // content (if any).
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(lines) { line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (line.type == DiffType.REMOVED || line.type == DiffType.MODIFIED) {
                                RemovedBackground
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (line.oldLineNumber != null) {
                            "${line.oldLineNumber.toString().padStart(4)}  ${line.content}"
                        } else {
                            ""
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (line.type == DiffType.ADDED || line.type == DiffType.MODIFIED) {
                                AddedBackground
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (line.newLineNumber != null) {
                            "${line.newLineNumber.toString().padStart(4)}  ${line.content}"
                        } else {
                            ""
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
