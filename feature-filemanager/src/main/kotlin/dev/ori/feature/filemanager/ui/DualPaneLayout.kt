package dev.ori.feature.filemanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun DualPaneLayout(
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    leftPane: @Composable () -> Unit,
    rightPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = constraints.maxWidth.toFloat()

        Row(modifier = Modifier.fillMaxSize()) {
            // Left pane
            Box(
                modifier = Modifier
                    .weight(splitRatio)
                    .fillMaxHeight(),
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
            Box(
                modifier = Modifier
                    .weight(1f - splitRatio)
                    .fillMaxHeight(),
            ) {
                rightPane()
            }
        }
    }
}
