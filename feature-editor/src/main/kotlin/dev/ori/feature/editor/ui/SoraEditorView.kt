package dev.ori.feature.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Compose wrapper around Sora-Editor's [CodeEditor].
 *
 * Highlighting is a no-op stub until grammar assets are bundled (see [TextMateLoader]).
 */
@Composable
fun SoraEditorView(
    content: String,
    filename: String,
    readOnly: Boolean = false,
    onContentChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                }
            }
        },
        update = { editor ->
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
            editor.editable = !readOnly
        },
    )
}

private const val DEFAULT_TEXT_SIZE_SP = 14f
