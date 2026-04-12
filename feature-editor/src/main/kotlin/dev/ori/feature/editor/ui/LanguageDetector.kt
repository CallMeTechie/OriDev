package dev.ori.feature.editor.ui

/**
 * Maps file extensions to a TextMate scope name and a human-readable language label.
 *
 * The scope names match the ones used in the bundled TextMate grammars we intend to
 * ship under `src/main/assets/textmate/<lang>/` (see [TextMateLoader] for the loading
 * strategy).
 */
object LanguageDetector {

    data class Language(
        val id: String,
        val displayName: String,
        val scopeName: String,
    )

    private val PLAIN = Language(id = "plain", displayName = "Plain Text", scopeName = "text.plain")

    private val BY_EXTENSION: Map<String, Language> = mapOf(
        "kt" to Language("kotlin", "Kotlin", "source.kotlin"),
        "kts" to Language("kotlin", "Kotlin Script", "source.kotlin"),
        "java" to Language("java", "Java", "source.java"),
        "py" to Language("python", "Python", "source.python"),
        "js" to Language("javascript", "JavaScript", "source.js"),
        "mjs" to Language("javascript", "JavaScript", "source.js"),
        "cjs" to Language("javascript", "JavaScript", "source.js"),
        "ts" to Language("typescript", "TypeScript", "source.ts"),
        "tsx" to Language("typescript", "TypeScript (TSX)", "source.tsx"),
        "jsx" to Language("javascript", "JavaScript (JSX)", "source.js.jsx"),
        "php" to Language("php", "PHP", "source.php"),
        "sh" to Language("shell", "Shell", "source.shell"),
        "bash" to Language("shell", "Bash", "source.shell"),
        "zsh" to Language("shell", "Zsh", "source.shell"),
        "yml" to Language("yaml", "YAML", "source.yaml"),
        "yaml" to Language("yaml", "YAML", "source.yaml"),
        "json" to Language("json", "JSON", "source.json"),
        "xml" to Language("xml", "XML", "text.xml"),
        "md" to Language("markdown", "Markdown", "text.html.markdown"),
        "markdown" to Language("markdown", "Markdown", "text.html.markdown"),
        "html" to Language("html", "HTML", "text.html.basic"),
        "htm" to Language("html", "HTML", "text.html.basic"),
        "css" to Language("css", "CSS", "source.css"),
        "rs" to Language("rust", "Rust", "source.rust"),
        "go" to Language("go", "Go", "source.go"),
        "c" to Language("c", "C", "source.c"),
        "h" to Language("c", "C Header", "source.c"),
        "cpp" to Language("cpp", "C++", "source.cpp"),
        "cc" to Language("cpp", "C++", "source.cpp"),
        "cxx" to Language("cpp", "C++", "source.cpp"),
        "hpp" to Language("cpp", "C++ Header", "source.cpp"),
    )

    /** Detect language from a full filename (e.g. `Main.kt`) or a bare extension (e.g. `kt`). */
    fun detect(filename: String): Language {
        val ext = filename.substringAfterLast('.', missingDelimiterValue = filename).lowercase()
        return BY_EXTENSION[ext] ?: PLAIN
    }
}
