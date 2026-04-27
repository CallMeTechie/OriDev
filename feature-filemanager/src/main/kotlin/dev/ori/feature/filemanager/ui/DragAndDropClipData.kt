package dev.ori.feature.filemanager.ui

import android.content.ClipData
import android.content.ClipDescription

/**
 * Bug G fix — multi-file drag-and-drop payload encoding.
 *
 * The Compose `dragAndDropSource` modifier ships its payload as an Android
 * [ClipData]. To support dragging a multi-selection from one file-manager
 * pane to the other, we pack each selected file path as a separate
 * [ClipData.Item] under a single ClipData with `MIMETYPE_TEXT_PLAIN`.
 *
 * The drop target reads each item back via [decodeClipData].
 *
 * The label is intentionally constant so the platform's overlay stays
 * stable regardless of how many files are dragged.
 */
internal const val DRAG_LABEL = "ori-filemanager-paths"

/**
 * Encode a list of file paths into a [ClipData] that can be wrapped in a
 * `DragAndDropTransferData` for [androidx.compose.foundation.draganddrop.dragAndDropSource].
 *
 * Empty inputs yield a ClipData with a single empty-text item — Android
 * requires at least one item, but [decodeClipData] filters empty items
 * back out so the drop target sees an empty list.
 */
internal fun encodeClipData(filePaths: List<String>): ClipData {
    val first = filePaths.firstOrNull().orEmpty()
    val clipData = ClipData.newPlainText(DRAG_LABEL, first)
    for (index in 1 until filePaths.size) {
        clipData.addItem(ClipData.Item(filePaths[index]))
    }
    return clipData
}

/**
 * Decode a [ClipData] produced by [encodeClipData] back into a list of
 * file paths. Items with null/blank text are skipped so a user dragging
 * an empty selection does not crash the drop handler.
 */
internal fun decodeClipData(clipData: ClipData?): List<String> {
    if (clipData == null) return emptyList()
    val texts = ArrayList<CharSequence?>(clipData.itemCount)
    for (i in 0 until clipData.itemCount) {
        texts.add(clipData.getItemAt(i).text)
    }
    return decodeClipItems(texts)
}

/**
 * Pure-Kotlin core of [decodeClipData], split out so unit tests can
 * exercise the filtering rules (null items, blank items, mixed payload)
 * without constructing an Android [ClipData] (which requires
 * Robolectric — not pulled in for the file-manager test sourceset).
 *
 * Skips null and empty texts; preserves the order of the surviving
 * items so a multi-file drag is dispatched in the same order the user
 * selected the files.
 */
internal fun decodeClipItems(items: List<CharSequence?>): List<String> {
    val out = ArrayList<String>(items.size)
    for (text in items) {
        val s = text?.toString().orEmpty()
        if (s.isNotEmpty()) {
            out.add(s)
        }
    }
    return out
}

/**
 * `true` if the drag-and-drop event carries a `text/plain` payload, which
 * is the only mime type our [encodeClipData] produces. Used as the
 * `shouldStartDragAndDrop` predicate on each pane's drop target so the
 * pane only accepts our own payloads (not, say, an arbitrary system
 * drag from another app).
 */
internal fun Set<String>.containsPlainText(): Boolean =
    contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
