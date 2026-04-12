package dev.ori.feature.editor.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LanguageDetectorTest {

    @Test
    fun detect_kotlin() {
        assertThat(LanguageDetector.detect("Main.kt").id).isEqualTo("kotlin")
    }

    @Test
    fun detect_kotlinScript() {
        assertThat(LanguageDetector.detect("build.gradle.kts").id).isEqualTo("kotlin")
    }

    @Test
    fun detect_java() {
        assertThat(LanguageDetector.detect("Main.java").id).isEqualTo("java")
    }

    @Test
    fun detect_python() {
        assertThat(LanguageDetector.detect("script.py").id).isEqualTo("python")
    }

    @Test
    fun detect_javascript() {
        assertThat(LanguageDetector.detect("app.js").id).isEqualTo("javascript")
    }

    @Test
    fun detect_typescript() {
        assertThat(LanguageDetector.detect("app.ts").id).isEqualTo("typescript")
    }

    @Test
    fun detect_tsx() {
        assertThat(LanguageDetector.detect("App.tsx").id).isEqualTo("typescript")
    }

    @Test
    fun detect_json() {
        assertThat(LanguageDetector.detect("config.json").id).isEqualTo("json")
    }

    @Test
    fun detect_yaml() {
        assertThat(LanguageDetector.detect("ci.yml").id).isEqualTo("yaml")
    }

    @Test
    fun detect_markdown() {
        assertThat(LanguageDetector.detect("README.md").id).isEqualTo("markdown")
    }

    @Test
    fun detect_rust() {
        assertThat(LanguageDetector.detect("main.rs").id).isEqualTo("rust")
    }

    @Test
    fun detect_go() {
        assertThat(LanguageDetector.detect("main.go").id).isEqualTo("go")
    }

    @Test
    fun detect_cpp() {
        assertThat(LanguageDetector.detect("main.cpp").id).isEqualTo("cpp")
    }

    @Test
    fun detect_shell() {
        assertThat(LanguageDetector.detect("install.sh").id).isEqualTo("shell")
    }

    @Test
    fun detect_html() {
        assertThat(LanguageDetector.detect("index.html").id).isEqualTo("html")
    }

    @Test
    fun detect_css() {
        assertThat(LanguageDetector.detect("style.css").id).isEqualTo("css")
    }

    @Test
    fun detect_unknown_returnsPlain() {
        assertThat(LanguageDetector.detect("data.xyz").id).isEqualTo("plain")
    }

    @Test
    fun detect_noExtension_returnsPlain() {
        assertThat(LanguageDetector.detect("Makefile").id).isEqualTo("plain")
    }

    // --- scopeForFile / infoForFile (Phase 6b TextMate loading) ---

    @Test
    fun scopeForFile_kotlin_returnsKotlinScope() {
        assertThat(LanguageDetector.scopeForFile("Main.kt")).isEqualTo("source.kotlin")
    }

    @Test
    fun scopeForFile_kts_returnsKotlinScope() {
        assertThat(LanguageDetector.scopeForFile("build.gradle.kts")).isEqualTo("source.kotlin")
    }

    @Test
    fun scopeForFile_json_returnsJsonScope() {
        assertThat(LanguageDetector.scopeForFile("config.json")).isEqualTo("source.json")
    }

    @Test
    fun scopeForFile_unknown_returnsNull() {
        assertThat(LanguageDetector.scopeForFile("data.xyz")).isNull()
    }

    @Test
    fun scopeForFile_caseInsensitive() {
        assertThat(LanguageDetector.scopeForFile("Main.KT")).isEqualTo("source.kotlin")
        assertThat(LanguageDetector.scopeForFile("README.MD")).isEqualTo("text.html.markdown")
    }

    @Test
    fun scopeForFile_noExtension_returnsNull() {
        assertThat(LanguageDetector.scopeForFile("Makefile")).isNull()
    }

    @Test
    fun scopeForFile_markdown_handlesMdAndMarkdown() {
        assertThat(LanguageDetector.scopeForFile("a.md")).isEqualTo("text.html.markdown")
        assertThat(LanguageDetector.scopeForFile("a.markdown")).isEqualTo("text.html.markdown")
    }

    @Test
    fun scopeForFile_shell_handlesShAndBash() {
        assertThat(LanguageDetector.scopeForFile("install.sh")).isEqualTo("source.shell")
        assertThat(LanguageDetector.scopeForFile("install.bash")).isEqualTo("source.shell")
    }

    @Test
    fun scopeForFile_yaml_handlesYmlAndYaml() {
        assertThat(LanguageDetector.scopeForFile("ci.yml")).isEqualTo("source.yaml")
        assertThat(LanguageDetector.scopeForFile("ci.yaml")).isEqualTo("source.yaml")
    }

    @Test
    fun allLanguages_returnsDistinctList() {
        val all = LanguageDetector.allLanguages()
        // 5 distinct languages: kotlin, json, markdown, shell, yaml
        assertThat(all).hasSize(5)
        assertThat(all.map { it.scopeName }).containsExactly(
            "source.kotlin",
            "source.json",
            "text.html.markdown",
            "source.shell",
            "source.yaml",
        )
    }
}
