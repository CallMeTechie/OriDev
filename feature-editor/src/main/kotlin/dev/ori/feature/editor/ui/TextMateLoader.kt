package dev.ori.feature.editor.ui

import android.content.Context
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initializes TextMate grammar support for [io.github.rosemoe.sora.widget.CodeEditor].
 *
 * TODO: Full TextMate grammar files need to be added to `src/main/assets/textmate/`
 * (grammar `.json` / `.plist` files per language, plus theme `.json` files). Once the
 * asset pipeline is in place, this loader can call `FileProviderRegistry.getInstance()
 * .addFileProvider(AssetsFileResolver(context.assets))` and then resolve a
 * `TextMateLanguage` from the registered grammar registry. For now, the editor uses
 * [EmptyLanguage] as a safe fallback and renders text in the default monospace face
 * without syntax highlighting.
 */
object TextMateLoader {

    private val initialized = AtomicBoolean(false)

    /** Idempotent -- safe to call on every editor creation. */
    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        // Intentional stub. Real implementation will:
        //   1. FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
        //   2. GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        //   3. ThemeRegistry.getInstance().loadTheme(...)
    }

    /**
     * Returns a [Language] instance suitable for the given filename. Until the grammar
     * assets are bundled this always returns [EmptyLanguage].
     */
    @Suppress("UNUSED_PARAMETER")
    fun loadLanguageForFile(filename: String): Language = EmptyLanguage()
}
