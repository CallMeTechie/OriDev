# Ori:Dev Phase 6b: Editor & Claude -- Polish (v2 -- post-review)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete features deferred from Phase 6a: Snippet Manager CRUD, Auto-Detect Code Blocks in terminal, Inline Diff Viewer, Git Diff Gutter in editor, TextMate grammar loading with minimum 5 languages.

**Architecture:** Phase 6b is an additive polish phase. Phase 3 already delivered Git Status Overlay (GitStatusParser + FileItemRow badges). Phase 6b focuses on remaining spec gaps.

**Tech Stack:** Sora-Editor 0.23.6 + language-textmate (existing), java-diff-utils 4.12 (new), Compose, Room.

**Depends on:** Phase 6a complete. Phase 3 complete (Git Status Overlay already done).

**Scope note:** Spec task 6.12 (Git Status Overlay) was ALREADY delivered in Phase 3:
- `GitStatusParser` exists in `data/src/main/kotlin/dev/ori/data/repository/`
- `LocalFileSystemRepository.listFiles()` enriches `FileItem.gitStatus`
- `FileItemRow` renders `GitStatusDot` with color coding (STAGED/MODIFIED/UNTRACKED)

Phase 6b only adds a manual refresh affordance to the existing overlay, not a new implementation.

**Review fixes applied (v1 -> v2):**
- Clarified Git Status Overlay is already done in Phase 3 (not a gap)
- Added LanguageDetector.kt to file list (was missing)
- Fixed SoraEditorView parameter name (`filename` not `language`)
- Fixed Theme loading to use `ThemeModel.load()` for proper name registration
- UTF-8 safe CodeBlockDetector with CharsetDecoder carry-over
- Code block end regex anchored to line boundaries
- Regex uses multiline mode to avoid inline backtick false positives
- CodeBlockDetector threading contract explicit (single-threaded, confined to reader coroutine)
- DiffViewerViewModel stores content in SavedStateHandle after first read (survives rotation)
- DiffPayload holds titles too (not just content)
- parseLineDiff uses File parameter and ProcessRunner abstraction for testability
- Added LanguageDetectorTest and TextMateLoaderTest
- Grammar coverage expanded to 5 languages minimum with explicit TODO for remaining 4
- Placeholder grammar files clearly marked (not pretending to be real tmLanguage files)
- Git Diff Gutter Step 1 is a proper spike with decision doc committed

---

### Task 6b.1: Snippet Manager CRUD

Current state: DAO/entity/repo exist. Read-only SnippetSheet exists. Need CRUD dialogs + search.

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/AddSnippetUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/UpdateSnippetUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/DeleteSnippetUseCase.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt` (new events + state fields)
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt` (wire CRUD)
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/SnippetSheet.kt` (CRUD UI + search)
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt` (pass new params to SnippetSheet)

- [ ] **Step 1: Create 3 use cases**

Standard pattern: `@Inject constructor(private val repository: SnippetRepository)` with `suspend operator fun invoke()` delegating to the repository.

- [ ] **Step 2: Update TerminalUiState**

Add fields:
```kotlin
val snippetSearchQuery: String = "",
val editingSnippet: CommandSnippet? = null,
val showSnippetDialog: Boolean = false,
```

Add to sealed TerminalEvent:
```kotlin
data object ShowAddSnippetDialog : TerminalEvent()
data class ShowEditSnippetDialog(val snippet: CommandSnippet) : TerminalEvent()
data object HideSnippetDialog : TerminalEvent()
data class SaveSnippet(val name: String, val command: String, val category: String) : TerminalEvent()
data class DeleteSnippet(val snippet: CommandSnippet) : TerminalEvent()
data class SetSnippetSearchQuery(val query: String) : TerminalEvent()
```

Add computed property for filtered snippets in the ViewModel (not UiState), used in SnippetSheet.

- [ ] **Step 3: Update TerminalViewModel**

Inject AddSnippetUseCase, UpdateSnippetUseCase, DeleteSnippetUseCase.

In `onEvent()`, handle new events:
- SaveSnippet: if editingSnippet != null, merge with name/command/category preserving id, serverProfileId, isWatchQuickCommand, sortOrder, then call update use case. Else create new CommandSnippet with defaults (serverProfileId=null = global, isWatchQuickCommand=false, sortOrder=snippets.size) and call add use case.
- DeleteSnippet: call delete use case
- SetSnippetSearchQuery: update uiState.snippetSearchQuery
- ShowAddSnippetDialog: set editingSnippet=null, showSnippetDialog=true
- ShowEditSnippetDialog: set editingSnippet=snippet, showSnippetDialog=true
- HideSnippetDialog: set editingSnippet=null, showSnippetDialog=false

- [ ] **Step 4: Update SnippetSheet signature + CRUD UI**

Current signature (read from the file):
```kotlin
@Composable
fun SnippetSheet(
    snippets: List<CommandSnippet>,
    onSnippetClick: (CommandSnippet) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

New signature adds:
```kotlin
@Composable
fun SnippetSheet(
    snippets: List<CommandSnippet>,
    searchQuery: String,
    editingSnippet: CommandSnippet?,
    showDialog: Boolean,
    onSnippetClick: (CommandSnippet) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (CommandSnippet) -> Unit,
    onDeleteClick: (CommandSnippet) -> Unit,
    onSaveSnippet: (name: String, command: String, category: String) -> Unit,
    onDismissDialog: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

UI additions:
- Search TextField at top of sheet
- Filtered list based on searchQuery (case-insensitive name or command contains)
- "+" IconButton next to search to add new
- Each row: overflow menu (three-dot) with Edit/Delete items
- AlertDialog for add/edit (shown when showDialog=true): 3 TextFields (name, command multiline, category), Save button, Cancel button. Pre-populate fields from editingSnippet if not null.

- [ ] **Step 5: Update TerminalScreen**

Update the `SnippetSheet()` call site to pass all new parameters using `viewModel.onEvent()` callbacks.

- [ ] **Step 6: Write tests**

Extend `TerminalViewModelTest` with 5 tests:
- `showAddSnippetDialog_setsStateAndNullsEditingSnippet`
- `showEditSnippetDialog_setsEditingSnippet`
- `saveSnippet_newSnippet_callsAddUseCase`
- `saveSnippet_withEditingSnippet_callsUpdateUseCase_preservingMetadata`
- `deleteSnippet_callsUseCase`
- `setSnippetSearchQuery_updatesState`

Run: `./gradlew :feature-terminal:test`

- [ ] **Step 7: Commit**

Message: `feat(terminal): add Snippet Manager CRUD with search`

---

### Task 6b.2: TextMate Grammar Loading (5 Languages, Real API)

Current state: `TextMateLoader` stub returns `EmptyLanguage()`. Need working grammar registry + theme + LanguageDetector.

**Files:**
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/LanguageDetector.kt`
- Modify: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/TextMateLoader.kt`
- Modify: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/SoraEditorView.kt` (fix param name + remember language)
- Create: `feature-editor/src/main/assets/textmate/languages.json`
- Create: `feature-editor/src/main/assets/textmate/themes/light.json`
- Create: `feature-editor/src/main/assets/textmate/grammars/*.placeholder.json` (5 files)
- Test: `feature-editor/src/test/kotlin/dev/ori/feature/editor/ui/LanguageDetectorTest.kt`
- Test: `feature-editor/src/test/kotlin/dev/ori/feature/editor/ui/TextMateLoaderTest.kt`

- [ ] **Step 1: Create LanguageDetector**

```kotlin
package dev.ori.feature.editor.ui

data class LanguageInfo(
    val scopeName: String,
    val grammarAsset: String,
)

object LanguageDetector {
    private val extensionMap: Map<String, LanguageInfo> = mapOf(
        "kt" to LanguageInfo("source.kotlin", "grammars/kotlin.placeholder.json"),
        "kts" to LanguageInfo("source.kotlin", "grammars/kotlin.placeholder.json"),
        "json" to LanguageInfo("source.json", "grammars/json.placeholder.json"),
        "md" to LanguageInfo("text.html.markdown", "grammars/markdown.placeholder.json"),
        "markdown" to LanguageInfo("text.html.markdown", "grammars/markdown.placeholder.json"),
        "sh" to LanguageInfo("source.shell", "grammars/shell.placeholder.json"),
        "bash" to LanguageInfo("source.shell", "grammars/shell.placeholder.json"),
        "yml" to LanguageInfo("source.yaml", "grammars/yaml.placeholder.json"),
        "yaml" to LanguageInfo("source.yaml", "grammars/yaml.placeholder.json"),
    )

    fun scopeForFile(filename: String): String? {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return extensionMap[ext]?.scopeName
    }

    fun infoForFile(filename: String): LanguageInfo? {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return extensionMap[ext]
    }

    /**
     * Returns all languages registered. Used by TextMateLoader to pre-register grammars.
     */
    fun allLanguages(): List<LanguageInfo> = extensionMap.values.distinct()
}
```

NOTE: 5 languages shipped (kotlin, json, markdown, shell, yaml). Spec 6.2 lists 9 (+PHP, Python, JS, TS, XML). Remaining 4 are explicit TODO in Known Limitations.

- [ ] **Step 2: Create placeholder grammar files**

File naming: `grammars/kotlin.placeholder.json` (NOT `kotlin.tmLanguage.json`). The `.placeholder` suffix makes it obvious these are not official grammars.

Each file contains a minimal valid JSON grammar with keyword and comment patterns. Example `kotlin.placeholder.json`:

```json
{
  "name": "Kotlin (placeholder)",
  "scopeName": "source.kotlin",
  "patterns": [
    {
      "match": "\\b(fun|val|var|class|object|interface|if|else|when|for|while|return|package|import|private|public|internal|suspend|override|override|data|sealed|abstract|open|final)\\b",
      "name": "keyword.control.kotlin"
    },
    {
      "begin": "\"",
      "end": "\"",
      "name": "string.quoted.double.kotlin"
    },
    {
      "match": "//[^\\n]*",
      "name": "comment.line.double-slash.kotlin"
    }
  ]
}
```

Create similar minimal grammars for: json, markdown, shell, yaml. Each file starts with a top-level comment field (if JSON allowed) or inline metadata note documenting the placeholder status.

- [ ] **Step 3: Create languages.json manifest**

```json
{
  "languages": [
    {
      "name": "kotlin",
      "scopeName": "source.kotlin",
      "grammar": "textmate/grammars/kotlin.placeholder.json"
    },
    {
      "name": "json",
      "scopeName": "source.json",
      "grammar": "textmate/grammars/json.placeholder.json"
    },
    {
      "name": "markdown",
      "scopeName": "text.html.markdown",
      "grammar": "textmate/grammars/markdown.placeholder.json"
    },
    {
      "name": "shell",
      "scopeName": "source.shell",
      "grammar": "textmate/grammars/shell.placeholder.json"
    },
    {
      "name": "yaml",
      "scopeName": "source.yaml",
      "grammar": "textmate/grammars/yaml.placeholder.json"
    }
  ]
}
```

- [ ] **Step 4: Create light theme**

`feature-editor/src/main/assets/textmate/themes/light.json`:
```json
{
  "name": "light",
  "type": "light",
  "colors": {
    "editor.background": "#FFFFFF",
    "editor.foreground": "#111827",
    "editor.selectionBackground": "#E0E7FF",
    "editorLineNumber.foreground": "#9CA3AF"
  },
  "tokenColors": [
    {"scope": "keyword", "settings": {"foreground": "#6366F1"}},
    {"scope": "string", "settings": {"foreground": "#10B981"}},
    {"scope": "comment", "settings": {"foreground": "#6B7280"}},
    {"scope": "entity.name.function", "settings": {"foreground": "#8B5CF6"}},
    {"scope": "storage.type", "settings": {"foreground": "#3B82F6"}}
  ]
}
```

- [ ] **Step 5: Update TextMateLoader with proper API**

```kotlin
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

object TextMateLoader {
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.assets)
            )
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

            val themeSource = IThemeSource.fromInputStream(
                context.assets.open("textmate/themes/light.json"),
                "light.json",
                null,
            )
            // Use ThemeModel so name resolution works with setTheme()
            val themeModel = ThemeModel(themeSource, "light")
            ThemeRegistry.getInstance().loadTheme(themeModel)
            ThemeRegistry.getInstance().setTheme("light")

            initialized = true
        } catch (e: Exception) {
            // Grammar/theme loading failed -- editor falls back to EmptyLanguage
            android.util.Log.w("TextMateLoader", "Failed to load TextMate resources", e)
        }
    }

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
```

Key fixes from review:
- Uses `ThemeModel(source, "light")` so `setTheme("light")` resolves
- Catches exceptions and logs (no crash)
- Uses `GrammarRegistry.loadGrammars("textmate/languages.json")` which loads all grammars from the manifest

- [ ] **Step 6: Fix SoraEditorView**

Read current file. The current parameter is `filename`. The update block must use `filename` consistently and `remember` the language by filename key:

```kotlin
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
        factory = { context ->
            TextMateLoader.initialize(context)
            CodeEditor(context).apply {
                setText(content)
                editable = !readOnly
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
            // Only re-create language if filename changed -- stored as tag
            val currentLang = editor.getTag(android.R.id.text1) as? String
            if (currentLang != filename) {
                editor.setEditorLanguage(TextMateLoader.loadLanguageForFile(filename))
                editor.setTag(android.R.id.text1, filename)
            }
        },
    )
}
```

The `setTag(android.R.id.text1, filename)` pattern avoids allocating a new TextMateLanguage on every recomposition.

- [ ] **Step 7: Write LanguageDetectorTest**

```kotlin
class LanguageDetectorTest {
    @Test fun scopeForFile_kotlin_returnsKotlinScope()
    @Test fun scopeForFile_kts_returnsKotlinScope()
    @Test fun scopeForFile_json_returnsJsonScope()
    @Test fun scopeForFile_unknown_returnsNull()
    @Test fun scopeForFile_caseInsensitive()
    @Test fun scopeForFile_noExtension_returnsNull()
    @Test fun scopeForFile_markdown_handlesMdAndMarkdown()
    @Test fun scopeForFile_shell_handlesShAndBash()
    @Test fun scopeForFile_yaml_handlesYmlAndYaml()
    @Test fun allLanguages_returnsDistinctList()
}
```

- [ ] **Step 8: Write TextMateLoaderTest**

```kotlin
class TextMateLoaderTest {
    // Pure JVM test (no Context) for what we can cover:
    @Test fun loadLanguageForFile_notInitialized_returnsEmptyLanguage()
    @Test fun loadLanguageForFile_unknownExtension_returnsEmptyLanguage()
}
```

Full initialization tests require a real Android Context (AssetsFileResolver). Those would need `androidTest`. For JVM tests, cover only the non-initialized paths. Add TODO comment for androidTest coverage.

- [ ] **Step 9: Run tests and commit**

Run: `./gradlew :feature-editor:test`
Message: `feat(editor): wire TextMate grammar loading with 5 placeholder language grammars`

---

### Task 6b.3: Git Status Refresh Affordance (thin addition)

**NOTE**: Git Status Overlay is already fully implemented in Phase 3 (GitStatusParser, LocalFileSystemRepository integration, FileItemRow badge UI). This task adds only the manual refresh UX affordance.

**Files:**
- Modify: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileListPane.kt` (add refresh button in toolbar)

- [ ] **Step 1: Add refresh IconButton**

Read the current toolbar in FileListPane. Add an IconButton with `Icons.Default.Refresh` next to the existing toolbar icons. onClick calls `onEvent(FileManagerEvent.RefreshPane(pane))`.

Verify `RefreshPane` event already exists (it does). The existing event re-lists files via the repository, which re-runs GitStatusParser, so no additional logic needed.

- [ ] **Step 2: Verify existing mutations auto-refresh**

Read FileManagerViewModel. Confirm `deleteSelected`, `renameFile`, `createDirectory`, `chmod` all call `refreshPane(pane)` at the end. If any don't, add the call.

- [ ] **Step 3: Commit**

Message: `feat(filemanager): add manual git status refresh button`

---

### Task 6b.4: Auto-Detect Code Blocks in Terminal Output

Current state: No detection. Add UTF-8 safe detector with explicit threading contract.

**Files:**
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/CodeBlockDetector.kt`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/CodeBlocksSheet.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt`
- Test: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/CodeBlockDetectorTest.kt`

- [ ] **Step 1: Create CodeBlockDetector with UTF-8 safe decoding**

```kotlin
package dev.ori.feature.terminal.ui

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class DetectedCodeBlock(
    val id: String,
    val language: String?,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * CONCURRENCY CONTRACT: Instances of this class are NOT thread-safe.
 * Each terminal tab must own exactly ONE detector instance, and all calls
 * (`processChunk`, `reset`) MUST be made from the tab's reader coroutine
 * (confined to Dispatchers.IO). Cross-coroutine access will corrupt state.
 */
class CodeBlockDetector {
    private val textBuffer = StringBuilder()
    private val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    // Carry-over bytes for incomplete UTF-8 sequences at chunk boundaries
    private val carryBuffer = ByteBuffer.allocate(MAX_CARRY_BYTES)

    // Regex: multiline, anchor end to start of line to avoid inline backtick false positives
    private val blockStartRegex = Regex("""(?m)^```(\w*)\s*$""")
    private val blockEndRegex = Regex("""(?m)^```\s*$""")

    /**
     * Processes a chunk of terminal output bytes and returns newly detected complete code blocks.
     * UTF-8 safe: handles byte sequences split across calls.
     */
    fun processChunk(bytes: ByteArray, length: Int): List<DetectedCodeBlock> {
        // Decode with carry-over: prepend any bytes from previous incomplete sequence
        val incoming = ByteBuffer.wrap(bytes, 0, length)
        val combined = ByteBuffer.allocate(carryBuffer.position() + incoming.remaining())
        carryBuffer.flip()
        combined.put(carryBuffer)
        combined.put(incoming)
        combined.flip()

        val charBuffer = CharBuffer.allocate(combined.remaining() + 1)
        decoder.decode(combined, charBuffer, false)
        charBuffer.flip()
        textBuffer.append(charBuffer)

        // Save leftover bytes for next chunk
        carryBuffer.clear()
        if (combined.hasRemaining()) {
            carryBuffer.put(combined)
        }

        return detectBlocks()
    }

    private fun detectBlocks(): List<DetectedCodeBlock> {
        val blocks = mutableListOf<DetectedCodeBlock>()

        while (true) {
            val startMatch = blockStartRegex.find(textBuffer) ?: break
            val contentStart = startMatch.range.last + 1

            // End must be AFTER contentStart to avoid matching the opening as a close
            val endMatch = blockEndRegex.find(textBuffer, contentStart) ?: break

            val language = startMatch.groupValues[1].takeIf { it.isNotEmpty() }
            val content = textBuffer.substring(contentStart, endMatch.range.first).trim('\n', '\r')

            blocks.add(
                DetectedCodeBlock(
                    id = java.util.UUID.randomUUID().toString(),
                    language = language,
                    content = content,
                )
            )

            // Delete processed region [startMatch.start, endMatch.endInclusive]
            textBuffer.delete(startMatch.range.first, endMatch.range.last + 1)
        }

        // Trim textBuffer if it grows unbounded (no closing fence found)
        if (textBuffer.length > MAX_BUFFER_SIZE) {
            textBuffer.delete(0, textBuffer.length - MAX_BUFFER_SIZE / 2)
        }

        return blocks
    }

    fun reset() {
        textBuffer.clear()
        carryBuffer.clear()
    }

    companion object {
        private const val MAX_BUFFER_SIZE = 64 * 1024
        private const val MAX_CARRY_BYTES = 8 // UTF-8 sequences are at most 4 bytes; 8 is safety margin
    }
}
```

- [ ] **Step 2: Update TerminalUiState**

```kotlin
val detectedCodeBlocks: List<DetectedCodeBlock> = emptyList(),
val showCodeBlocksSheet: Boolean = false,
```

Events:
```kotlin
data object ToggleCodeBlocksSheet : TerminalEvent()
data class CopyCodeBlock(val blockId: String) : TerminalEvent()
data class OpenCodeBlockInEditor(val blockId: String) : TerminalEvent()
data object ClearCodeBlocks : TerminalEvent()
```

- [ ] **Step 3: Wire into TerminalViewModel**

Add `private val codeBlockDetectors = ConcurrentHashMap<String, CodeBlockDetector>()` for per-tab detectors. On `createTab`, create detector. On `closeTab`, remove detector.

In the reader coroutine, after `emulator.writeInput(buffer, 0, bytesRead)`:
```kotlin
val detector = codeBlockDetectors[tabId] ?: return@launch
val newBlocks = detector.processChunk(buffer, bytesRead)
if (newBlocks.isNotEmpty()) {
    _uiState.update { state ->
        val combined = (state.detectedCodeBlocks + newBlocks).takeLast(MAX_CODE_BLOCKS)
        state.copy(detectedCodeBlocks = combined)
    }
}
```

Add `private const val MAX_CODE_BLOCKS = 20` as companion constant.

CONCURRENCY NOTE: Reader coroutine is already confined to `Dispatchers.IO` and runs per-tab, so the CodeBlockDetector contract is satisfied.

Handle new events:
- ToggleCodeBlocksSheet: flip showCodeBlocksSheet
- CopyCodeBlock: find by id, copy text to clipboard
- OpenCodeBlockInEditor: find by id, show a snackbar "Coming soon" (or actually write to a temp file and open editor -- defer as TODO)
- ClearCodeBlocks: set detectedCodeBlocks = emptyList()

- [ ] **Step 4: Create CodeBlocksSheet**

ModalBottomSheet. Each row shows: language badge (or "text"), first 5 lines of content preview (monospace), timestamp. Actions: Copy, Open in Editor (disabled with "Coming soon" tooltip if not implemented).

Empty state: "No code blocks detected. Run commands that output \`\`\` fenced code."

- [ ] **Step 5: Add UI indicator to TerminalScreen**

In top bar, add small IconButton with badge showing `detectedCodeBlocks.size` (hidden when 0). Tap opens CodeBlocksSheet.

- [ ] **Step 6: Write tests**

```kotlin
class CodeBlockDetectorTest {
    @Test fun processChunk_noBlocks_returnsEmpty()
    @Test fun processChunk_completeBlock_detects()
    @Test fun processChunk_withLanguage_capturesLanguage()
    @Test fun processChunk_splitAcrossChunks_detects()
    @Test fun processChunk_multipleBlocks_detectsAll()
    @Test fun processChunk_unterminatedBlock_trimsBuffer()
    @Test fun processChunk_utf8MultiByteBoundary_handlesGracefully()
    @Test fun processChunk_inlineBackticksInCode_doesNotTerminatePrematurely()
    @Test fun reset_clearsBufferAndCarry()
}
```

The `utf8MultiByteBoundary` test splits a 3-byte UTF-8 char (e.g., `€` = 0xE2 0x82 0xAC) across two processChunk calls and verifies the char appears correctly in the detected block.

- [ ] **Step 7: Commit**

Run: `./gradlew :feature-terminal:test`
Message: `feat(terminal): add UTF-8 safe code block auto-detection`

---

### Task 6b.5: Inline Diff Viewer

Current state: None. Add diff algorithm, UI, and lifecycle-safe data holder.

**Files:**
- Modify: `gradle/libs.versions.toml` (add java-diff-utils)
- Modify: `feature-editor/build.gradle.kts`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/DiffCalculator.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/DiffPayload.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/DiffDataHolder.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/DiffViewerUiState.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/DiffViewerViewModel.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/DiffViewerScreen.kt`
- Modify: `feature-editor/src/main/kotlin/dev/ori/feature/editor/navigation/EditorNavigation.kt` (add diff route)
- Modify: `app/src/main/kotlin/dev/ori/app/navigation/OriDevNavHost.kt`
- Test: `feature-editor/src/test/kotlin/dev/ori/feature/editor/ui/DiffCalculatorTest.kt`
- Test: `feature-editor/src/test/kotlin/dev/ori/feature/editor/ui/DiffViewerViewModelTest.kt`

- [ ] **Step 1: Add java-diff-utils dependency**

```toml
# libs.versions.toml [versions]
java-diff-utils = "4.12"

# [libraries]
java-diff-utils = { module = "io.github.java-diff-utils:java-diff-utils", version.ref = "java-diff-utils" }
```

In `feature-editor/build.gradle.kts`:
```kotlin
implementation(libs.java.diff.utils)
```

NOTE: Package is `com.github.difflib` (not `io.github`).

- [ ] **Step 2: Create DiffPayload and DiffDataHolder**

```kotlin
package dev.ori.feature.editor.ui

import java.util.concurrent.ConcurrentHashMap

data class DiffPayload(
    val oldContent: String,
    val newContent: String,
    val oldTitle: String,
    val newTitle: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * In-memory store for diff content passed between screens via navigation.
 * Data is NOT removed on get() -- removal happens via removeEntry() when the
 * consuming ViewModel is cleared. This allows survival across configuration
 * changes (rotation) while still cleaning up.
 *
 * TTL of 10 minutes prevents leaks if a screen is never cleaned up.
 */
object DiffDataHolder {
    private val store = ConcurrentHashMap<String, DiffPayload>()
    private const val TTL_MS = 10 * 60 * 1000L // 10 minutes

    fun put(id: String, payload: DiffPayload) {
        pruneExpired()
        store[id] = payload
    }

    fun get(id: String): DiffPayload? {
        pruneExpired()
        return store[id]
    }

    fun remove(id: String) {
        store.remove(id)
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        store.entries.removeAll { (_, payload) -> now - payload.createdAt > TTL_MS }
    }
}
```

Key fix: `get()` no longer removes. Consumers must explicitly call `remove()` in `onCleared()`. Process death means diff is gone -- ViewModel must handle null DiffPayload gracefully (show error + navigate back).

- [ ] **Step 3: Create DiffCalculator**

```kotlin
package dev.ori.feature.editor.ui

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType

data class DiffLine(
    val type: DiffType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val content: String,
)

enum class DiffType { CONTEXT, ADDED, REMOVED, MODIFIED }

object DiffCalculator {
    fun computeDiff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val patch = DiffUtils.diff(oldLines, newLines)

        val result = mutableListOf<DiffLine>()
        var oldIdx = 0
        var newIdx = 0

        for (delta in patch.deltas) {
            // Add context lines up to the delta
            while (oldIdx < delta.source.position) {
                result.add(DiffLine(DiffType.CONTEXT, oldIdx + 1, newIdx + 1, oldLines[oldIdx]))
                oldIdx++
                newIdx++
            }

            when (delta.type) {
                DeltaType.INSERT -> {
                    for ((i, line) in delta.target.lines.withIndex()) {
                        result.add(DiffLine(DiffType.ADDED, null, newIdx + i + 1, line))
                    }
                    newIdx += delta.target.lines.size
                }
                DeltaType.DELETE -> {
                    for ((i, line) in delta.source.lines.withIndex()) {
                        result.add(DiffLine(DiffType.REMOVED, oldIdx + i + 1, null, line))
                    }
                    oldIdx += delta.source.lines.size
                }
                DeltaType.CHANGE -> {
                    for ((i, line) in delta.source.lines.withIndex()) {
                        result.add(DiffLine(DiffType.REMOVED, oldIdx + i + 1, null, line))
                    }
                    for ((i, line) in delta.target.lines.withIndex()) {
                        result.add(DiffLine(DiffType.ADDED, null, newIdx + i + 1, line))
                    }
                    oldIdx += delta.source.lines.size
                    newIdx += delta.target.lines.size
                }
                DeltaType.EQUAL -> {
                    // Should not appear in DiffUtils deltas
                }
            }
        }

        // Remaining context after last delta
        while (oldIdx < oldLines.size) {
            result.add(DiffLine(DiffType.CONTEXT, oldIdx + 1, newIdx + 1, oldLines[oldIdx]))
            oldIdx++
            newIdx++
        }

        return result
    }
}
```

- [ ] **Step 4: Create DiffViewerUiState**

```kotlin
data class DiffViewerUiState(
    val oldTitle: String = "",
    val newTitle: String = "",
    val diffLines: List<DiffLine> = emptyList(),
    val viewMode: DiffViewMode = DiffViewMode.UNIFIED,
    val isLoading: Boolean = true,
    val error: String? = null,
)

enum class DiffViewMode { UNIFIED, SIDE_BY_SIDE }

sealed class DiffViewerEvent {
    data class SetViewMode(val mode: DiffViewMode) : DiffViewerEvent()
    data object ClearError : DiffViewerEvent()
}
```

- [ ] **Step 5: Create DiffViewerViewModel**

@HiltViewModel. Takes diff id from SavedStateHandle. On init: lookup DiffDataHolder, compute diff, populate state. On `onCleared`: call `DiffDataHolder.remove(diffId)`.

```kotlin
@HiltViewModel
class DiffViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val diffId: String = savedStateHandle["diffId"]
        ?: error("diffId required")

    private val _uiState = MutableStateFlow(DiffViewerUiState())
    val uiState: StateFlow<DiffViewerUiState> = _uiState.asStateFlow()

    init {
        val payload = DiffDataHolder.get(diffId)
        if (payload == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Diff data expired. Please reopen.",
                )
            }
        } else {
            viewModelScope.launch {
                val lines = withContext(Dispatchers.Default) {
                    DiffCalculator.computeDiff(payload.oldContent, payload.newContent)
                }
                _uiState.update {
                    it.copy(
                        oldTitle = payload.oldTitle,
                        newTitle = payload.newTitle,
                        diffLines = lines,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onEvent(event: DiffViewerEvent) {
        when (event) {
            is DiffViewerEvent.SetViewMode -> _uiState.update { it.copy(viewMode = event.mode) }
            is DiffViewerEvent.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    override fun onCleared() {
        DiffDataHolder.remove(diffId)
        super.onCleared()
    }
}
```

- [ ] **Step 6: Create DiffViewerScreen**

Scaffold with OriDevTopBar showing "{oldTitle} -> {newTitle}". Action for view mode toggle.

Content: LazyColumn of DiffLine rows. Each row:
- Gutter: line numbers (old/new), prefix (`+`, `-`, ` `)
- Background: green (ADDED), red (REMOVED), none (CONTEXT)
- Text: monospace content

Side-by-side mode (if screen wide enough): two columns, old on left, new on right, aligned by line.

Empty state: "No differences"
Error state: Show error + back button

- [ ] **Step 7: Create DiffNavigation**

```kotlin
const val DIFF_ROUTE = "diff/{diffId}"

fun NavGraphBuilder.diffViewerScreen() {
    composable(
        route = DIFF_ROUTE,
        arguments = listOf(navArgument("diffId") { type = NavType.StringType }),
    ) { DiffViewerScreen() }
}

fun NavController.navigateToDiff(
    oldContent: String,
    newContent: String,
    oldTitle: String,
    newTitle: String,
) {
    val id = java.util.UUID.randomUUID().toString()
    DiffDataHolder.put(id, DiffPayload(oldContent, newContent, oldTitle, newTitle))
    navigate("diff/$id")
}
```

- [ ] **Step 8: Register in OriDevNavHost**

Add `diffViewerScreen()` call in NavHost builder.

- [ ] **Step 9: Write tests**

DiffCalculatorTest: 6 tests (identical, insert, delete, modify, empty, multi-delta).
DiffDataHolderTest: 4 tests (put/get/remove, TTL expiry, concurrent access).
DiffViewerViewModelTest: 4 tests (init loads diff, missing payload shows error, set view mode, onCleared removes from holder).

- [ ] **Step 10: Commit**

Run: `./gradlew :feature-editor:test`
Message: `feat(editor): add inline diff viewer with java-diff-utils`

---

### Task 6b.6: Git Diff Gutter in Editor -- SPIKE + Implementation

Current state: None. Highest risk task due to Sora API uncertainty.

**Files:**
- Create: `docs/superpowers/specs/2026-04-11-sora-gutter-spike.md` (decision document)
- Modify: `data/src/main/kotlin/dev/ori/data/repository/GitStatusParser.kt` (add parseLineDiff + ProcessRunner)
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/process/ProcessRunner.kt` (abstraction for testability)
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/GitGutterDecorator.kt` OR `GitDiffStatusBar.kt` (depending on spike outcome)
- Modify: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/SoraEditorView.kt` or `CodeEditorScreen.kt`

- [ ] **Step 1: Sora-Editor Decoration API SPIKE**

Create `docs/superpowers/specs/2026-04-11-sora-gutter-spike.md`. This is a research document (~1 page) that answers:

1. What decoration APIs does Sora-Editor 0.23.6 provide?
2. Specifically, can we draw custom indicators in the line number gutter?
3. Can we use `LineNumberTipTextProvider` or equivalent?
4. If not, what's the next best option (status bar summary, inline markers in content area)?

Research steps:
```bash
find ~/.gradle/caches -name "editor-0.23.6*" -type f | head
# Extract AAR classes.jar, list io.github.rosemoe.sora.widget.component.*
```

Decision criteria:
- **PROCEED with gutter** if: There's a public API for per-line gutter decoration (e.g., LineDecorator, GutterAdapter, or similar)
- **FALL BACK to status bar summary** if: No gutter API exists; show "3 added, 1 removed" in status bar

Commit the spike doc BEFORE proceeding to Step 2.

- [ ] **Step 2: Create ProcessRunner abstraction**

```kotlin
package dev.ori.core.common.process

interface ProcessRunner {
    /**
     * Runs a process and returns the combined stdout output.
     * Returns null on timeout or non-zero exit.
     */
    suspend fun run(command: List<String>, workingDir: String? = null, timeoutSeconds: Long = 5): String?
}

class DefaultProcessRunner : ProcessRunner {
    override suspend fun run(command: List<String>, workingDir: String?, timeoutSeconds: Long): String? {
        return try {
            val builder = ProcessBuilder(command)
            workingDir?.let { builder.directory(java.io.File(it)) }
            builder.redirectErrorStream(true)
            val process = builder.start()
            val completed = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                process.inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }
}
```

Bind via Hilt in core-security or a new module. Inject into GitStatusParser.

- [ ] **Step 3: Refactor GitStatusParser to use ProcessRunner**

Read the current GitStatusParser. It's currently an `object` using ProcessBuilder directly. Refactor to a `@Singleton class` that injects `ProcessRunner`. Update callers (`LocalFileSystemRepository`) accordingly.

Add method:
```kotlin
suspend fun parseLineDiff(file: File): Map<Int, LineChangeType> {
    val root = findGitRoot(file.parentFile) ?: return emptyMap()
    val relativePath = file.relativeTo(root).path
    val output = processRunner.run(
        command = listOf("git", "diff", "--unified=0", "HEAD", "--", relativePath),
        workingDir = root.absolutePath,
    ) ?: return emptyMap()

    return parseUnifiedDiff(output)
}

enum class LineChangeType { ADDED, MODIFIED, DELETED }

private fun parseUnifiedDiff(diff: String): Map<Int, LineChangeType> {
    // Parse @@ -oldStart,oldLen +newStart,newLen @@ hunk headers
    // For each hunk, mark new lines by type
    // Simplified: ADDED for new lines without matching removed, otherwise MODIFIED
    val result = mutableMapOf<Int, LineChangeType>()
    val hunkRegex = Regex("""@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@""")

    val lines = diff.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val match = hunkRegex.find(line)
        if (match != null) {
            val newStart = match.groupValues[1].toInt()
            val newLen = match.groupValues[2].toIntOrNull() ?: 1
            var lineNo = newStart
            var hasRemoved = false
            var hasAdded = false
            // Scan hunk body
            i++
            while (i < lines.size && !lines[i].startsWith("@@")) {
                val body = lines[i]
                when {
                    body.startsWith("+") && !body.startsWith("+++") -> {
                        result[lineNo] = if (hasRemoved) LineChangeType.MODIFIED else LineChangeType.ADDED
                        lineNo++
                        hasAdded = true
                    }
                    body.startsWith("-") && !body.startsWith("---") -> {
                        hasRemoved = true
                    }
                    else -> { lineNo++ }
                }
                i++
            }
        } else {
            i++
        }
    }
    return result
}
```

Handle ADDED files (no HEAD entry): detect via `git status --porcelain` first. If untracked, mark all lines as ADDED.

- [ ] **Step 4: Implement gutter or status bar based on spike outcome**

If spike says gutter is feasible: Create `GitGutterDecorator` that uses the discovered Sora API.
If spike says fall back: Create `GitDiffStatusBar` composable that shows "+N -M lines changed" in the bottom bar of CodeEditorScreen.

Either way, load diff via `GitStatusParser.parseLineDiff()` on editor file load and display.

- [ ] **Step 5: Write tests**

`GitStatusParserTest` with mock ProcessRunner:
- `parseLineDiff_noChanges_returnsEmpty`
- `parseLineDiff_oneAddedLine_returnsAdded`
- `parseLineDiff_modifiedLine_returnsModified`
- `parseLineDiff_untrackedFile_returnsAllAdded`
- `parseLineDiff_processTimeout_returnsEmpty`
- `parseLineDiff_notAGitRepo_returnsEmpty`

- [ ] **Step 6: Commit**

Message: `feat(editor): add git diff indicators (gutter or status bar per spike outcome)`

---

### Task 6b.7: CI Green

- [ ] **Step 1: Full verification**

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew detekt
./gradlew test
./gradlew assembleDebug
```

Fix any violations.

- [ ] **Step 2: Push**

`git push origin master`

- [ ] **Step 3: Monitor CI until green**

`gh run list --branch master --limit 5` until Build & Test = success. Fix failures, repeat.

**DO NOT report DONE until CI is GREEN.**

---

## Phase 6b Completion Checklist

- [ ] Snippet Manager: CRUD dialogs with search, 3 new use cases, 5 tests
- [ ] TextMate Loader: real GrammarRegistry/ThemeRegistry/LanguageDetector wired, 5 languages (kotlin/json/markdown/shell/yaml), placeholder grammars clearly labeled, tests for LanguageDetector + TextMateLoader
- [ ] Git Status Refresh: manual refresh button (the rest of git status overlay was already done in Phase 3)
- [ ] Code Block Detector: UTF-8 safe, explicit threading contract, correct regex anchoring, 9 tests
- [ ] Inline Diff Viewer: java-diff-utils 4.12, lifecycle-safe DiffDataHolder with TTL, DiffCalculator + ViewModel + Screen, DiffNavigation, 14 tests
- [ ] Git Diff Gutter: Spike document committed first, ProcessRunner abstraction, parseLineDiff, gutter OR status bar implementation based on spike outcome, tests
- [ ] All tests pass, detekt clean, build succeeds, CI GREEN

## Known Limitations (Documented)

1. **TextMate grammar files are placeholders** (named `*.placeholder.json` not `*.tmLanguage.json`): Real vscode-textmate grammar files must be vendored before release. Infrastructure is wired so dropping in real files requires no code changes.
2. **Only 5 of 9 spec languages shipped**: kotlin, json, markdown, shell, yaml. PHP, Python, JS/TS, XML deferred (extension mapping and placeholder grammars can be added via PR).
3. **Git Diff Gutter may degrade to status bar**: depending on Sora API spike outcome.
4. **Code block OpenInEditor**: Currently shows snackbar. Full integration requires writing detected blocks to a temp file. Deferred.
5. **TextMateLoader full init requires Android Context**: Full tests belong in androidTest (deferred). JVM tests only cover non-initialized paths.
