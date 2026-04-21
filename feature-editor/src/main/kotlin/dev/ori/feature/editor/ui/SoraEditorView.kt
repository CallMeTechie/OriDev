@file:Suppress("MatchingDeclarationName")

package dev.ori.feature.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Imperative handle into a [SoraEditorView]. Lets parent composables drive
 * the editor with commands that don't round-trip through the content
 * StateFlow — currently `undo()` / `redo()`, plus observable `canUndo` and
 * `canRedo` state backing the top-bar button enabled flags.
 *
 * Phase 11 P4.4 — factored out of [SoraEditorView] so the editor top bar
 * can fire undo/redo without the ViewModel having to shadow Sora's internal
 * edit stack.
 */
class SoraEditorController {
    internal var editor: CodeEditor? = null
        set(value) {
            field = value
            refresh()
        }

    /** True when [undo] would apply a change. Tracked via ContentChangeEvent. */
    var canUndo: Boolean by mutableStateOf(false)
        private set

    /** True when [redo] would re-apply a change. Tracked via ContentChangeEvent. */
    var canRedo: Boolean by mutableStateOf(false)
        private set

    fun undo() {
        editor?.undo()
        refresh()
    }

    fun redo() {
        editor?.redo()
        refresh()
    }

    internal fun refresh() {
        val e = editor
        canUndo = e?.canUndo() ?: false
        canRedo = e?.canRedo() ?: false
    }
}

/**
 * Compose wrapper around Sora-Editor's [CodeEditor].
 *
 * Highlighting is a no-op stub until grammar assets are bundled (see [TextMateLoader]).
 *
 * Phase 11 P4.4 — added an optional [controller] parameter. When provided,
 * the inner [CodeEditor] publishes itself to the controller and refreshes
 * the undo/redo flags on every ContentChangeEvent.
 */
@Composable
fun SoraEditorView(
    content: String,
    filename: String,
    readOnly: Boolean = false,
    onContentChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    controller: SoraEditorController? = null,
) {
    // Cache the last filename we handed to the editor so we only rebuild the
    // TextMate language when the filename actually changes. Previously we
    // stashed this via `View.setTag(android.R.id.text1, filename)` — but
    // `android.R.id.*` is a system resource (upper byte 0x01) and on
    // Android 16 (API 36) the tightened `View.setTag(int,Object)` key
    // validation (CompatChange 376561342/395521150) rejects any key whose
    // upper byte is < 0x02 with `IllegalArgumentException: The key must be
    // an application-specific resource id`. That crashed the app the
    // instant the Sora CodeEditor attached to the window. Holding the
    // state in a Compose `remember` avoids the View.setTag path entirely.
    var lastFilename by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextMateLoader.initialize(ctx)
            CodeEditor(ctx).apply {
                setText(content)
                editable = !readOnly
                setTextSize(DEFAULT_TEXT_SIZE_SP)
                setEditorLanguage(TextMateLoader.loadLanguageForFile(filename))
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    onContentChange(text.toString())
                    controller?.refresh()
                }
                controller?.editor = this
            }
            // `lastFilename` mirrors what the factory just applied so the
            // first `update` pass doesn't needlessly re-create the language.
                .also { lastFilename = filename }
        },
        update = { editor ->
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
            editor.editable = !readOnly
            if (lastFilename != filename) {
                editor.setEditorLanguage(TextMateLoader.loadLanguageForFile(filename))
                lastFilename = filename
            }
            controller?.refresh()
        },
        onRelease = { editor ->
            if (controller?.editor === editor) {
                controller.editor = null
            }
        },
    )
}

private const val DEFAULT_TEXT_SIZE_SP = 14f
