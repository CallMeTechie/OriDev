package dev.ori.feature.editor.ui

import com.google.common.truth.Truth.assertThat
import io.github.rosemoe.sora.lang.EmptyLanguage
import org.junit.jupiter.api.Test

/**
 * JVM tests for [TextMateLoader] -- limited to the non-initialized fallback paths.
 *
 * Full initialization tests require a real Android Context (AssetsFileResolver) and
 * belong in `androidTest` (TODO -- not yet wired in this module).
 */
class TextMateLoaderTest {

    @Test
    fun loadLanguageForFile_notInitialized_returnsEmptyLanguage() {
        val language = TextMateLoader.loadLanguageForFile("Main.kt")
        assertThat(language).isInstanceOf(EmptyLanguage::class.java)
    }

    @Test
    fun loadLanguageForFile_unknownExtension_returnsEmptyLanguage() {
        val language = TextMateLoader.loadLanguageForFile("data.xyz")
        assertThat(language).isInstanceOf(EmptyLanguage::class.java)
    }
}
