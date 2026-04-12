package dev.ori.feature.editor.ui

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType

/**
 * A single line of a computed diff, displayed in the unified diff viewer.
 */
data class DiffLine(
    val type: DiffType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val content: String,
)

enum class DiffType { CONTEXT, ADDED, REMOVED, MODIFIED }

/**
 * Computes a unified diff between two texts using java-diff-utils.
 *
 * The output is a flat list of [DiffLine]s in display order. CHANGE deltas are
 * expanded into REMOVED-then-ADDED line pairs so the caller can render both
 * old and new content.
 */
object DiffCalculator {
    @Suppress("NestedBlockDepth", "LongMethod", "CyclomaticComplexMethod")
    fun computeDiff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val patch = DiffUtils.diff(oldLines, newLines)

        val result = mutableListOf<DiffLine>()
        var oldIdx = 0
        var newIdx = 0

        for (delta in patch.deltas) {
            while (oldIdx < delta.source.position) {
                result.add(
                    DiffLine(
                        type = DiffType.CONTEXT,
                        oldLineNumber = oldIdx + 1,
                        newLineNumber = newIdx + 1,
                        content = oldLines[oldIdx],
                    ),
                )
                oldIdx++
                newIdx++
            }

            when (delta.type) {
                DeltaType.INSERT -> {
                    for ((i, line) in delta.target.lines.withIndex()) {
                        result.add(
                            DiffLine(
                                type = DiffType.ADDED,
                                oldLineNumber = null,
                                newLineNumber = newIdx + i + 1,
                                content = line,
                            ),
                        )
                    }
                    newIdx += delta.target.lines.size
                }
                DeltaType.DELETE -> {
                    for ((i, line) in delta.source.lines.withIndex()) {
                        result.add(
                            DiffLine(
                                type = DiffType.REMOVED,
                                oldLineNumber = oldIdx + i + 1,
                                newLineNumber = null,
                                content = line,
                            ),
                        )
                    }
                    oldIdx += delta.source.lines.size
                }
                DeltaType.CHANGE -> {
                    for ((i, line) in delta.source.lines.withIndex()) {
                        result.add(
                            DiffLine(
                                type = DiffType.REMOVED,
                                oldLineNumber = oldIdx + i + 1,
                                newLineNumber = null,
                                content = line,
                            ),
                        )
                    }
                    for ((i, line) in delta.target.lines.withIndex()) {
                        result.add(
                            DiffLine(
                                type = DiffType.ADDED,
                                oldLineNumber = null,
                                newLineNumber = newIdx + i + 1,
                                content = line,
                            ),
                        )
                    }
                    oldIdx += delta.source.lines.size
                    newIdx += delta.target.lines.size
                }
                DeltaType.EQUAL -> {
                    // EQUAL deltas are not emitted by DiffUtils.diff().
                }
            }
        }

        while (oldIdx < oldLines.size) {
            result.add(
                DiffLine(
                    type = DiffType.CONTEXT,
                    oldLineNumber = oldIdx + 1,
                    newLineNumber = newIdx + 1,
                    content = oldLines[oldIdx],
                ),
            )
            oldIdx++
            newIdx++
        }

        return result
    }
}
