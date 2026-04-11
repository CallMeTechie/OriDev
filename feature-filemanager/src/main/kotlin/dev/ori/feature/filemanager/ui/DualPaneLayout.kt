package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Indigo400

@Composable
fun DualPaneLayout(
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    leftPane: @Composable () -> Unit,
    rightPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dragState: DragState = DragState(),
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = constraints.maxWidth.toFloat()

        Row(modifier = Modifier.fillMaxSize()) {
            // Left pane
            val leftHighlight = dragState.isDragging && dragState.sourcePane == ActivePane.RIGHT
            Box(
                modifier = Modifier
                    .weight(splitRatio)
                    .fillMaxHeight()
                    .then(
                        if (leftHighlight) {
                            Modifier.border(
                                width = 2.dp,
                                color = Indigo400,
                                shape = RoundedCornerShape(4.dp),
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                leftPane()
            }

            // Draggable divider
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = dragAmount.x / totalWidth
                            val newRatio = (splitRatio + delta).coerceIn(0.2f, 0.8f)
                            onSplitRatioChange(newRatio)
                        }
                    },
            )

            // Right pane
            val rightHighlight = dragState.isDragging && dragState.sourcePane == ActivePane.LEFT
            Box(
                modifier = Modifier
                    .weight(1f - splitRatio)
                    .fillMaxHeight()
                    .then(
                        if (rightHighlight) {
                            Modifier.border(
                                width = 2.dp,
                                color = Indigo400,
                                shape = RoundedCornerShape(4.dp),
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                rightPane()
            }
        }
    }
}
