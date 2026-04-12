package dev.ori.feature.editor.ui

import android.content.Context
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * Initializes TextMate grammar support for [io.github.rosemoe.sora.widget.CodeEditor].
 *
 * Loads bundled placeholder grammars (kotlin, json, markdown, shell, yaml) and a light
 * theme from `src/main/assets/textmate/`. The grammars are minimal -- they highlight
 * keywords, strings, and comments only. Real grammars (PHP, Python, JS, TS, XML) are
 * tracked as a TODO in the Phase 6b plan known limitations.
 */
object TextMateLoader {

    @Volatile
    private var initialized = false

    /** Idempotent -- safe to call on every editor creation. */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                FileProviderRegistry.getInstance().addFileProvider(
                    AssetsFileResolver(context.applicationContext.assets),
                )
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

                val themeSource = IThemeSource.fromInputStream(
                    context.applicationContext.assets.open("textmate/themes/light.json"),
                    "light.json",
                    null,
                )
                val themeModel = ThemeModel(themeSource, "light")
                ThemeRegistry.getInstance().loadTheme(themeModel)
                ThemeRegistry.getInstance().setTheme("light")

                initialized = true
            } catch (e: Exception) {
                android.util.Log.w("TextMateLoader", "Failed to load TextMate resources", e)
            }
        }
    }

    /**
     * Returns a [Language] instance suitable for the given filename. Falls back to
     * [EmptyLanguage] when the loader is not initialized, the file extension is unknown,
     * or grammar creation throws.
     */
    fun loadLanguageForFile(filename: String): Language {
        if (!initialized) return EmptyLanguage()
        val scope = LanguageDetector.scopeForFile(filename) ?: return EmptyLanguage()
        return try {
            TextMateLanguage.create(scope, true)
        } catch (e: Exception) {
            android.util.Log.w("TextMateLoader", "Failed to create language for $filename", e)
            EmptyLanguage()
        }
    }
}
