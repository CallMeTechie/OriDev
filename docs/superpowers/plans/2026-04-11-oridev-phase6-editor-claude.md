# Ori:Dev Phase 6: Code Editor & Claude Code Integration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an integrated Code Editor with Sora-Editor (syntax highlighting for 10+ languages, git diff gutter) and Claude Code Integration (Anthropic API client, "Send to Claude" from terminal, Session Recorder, Code Preview in File Manager, Snippet Manager, Inline Diff Viewer).

**Architecture:** feature-editor uses Sora-Editor library (Apache 2.0) wrapped in AndroidView for Compose. A new `core-ai` module contains the Anthropic API client (OkHttp + custom HTTP, since no official Kotlin SDK is stable). feature-terminal gets a "Send to Claude" overlay backed by ClaudeApiService. Session logging wires into TerminalViewModel's reader coroutine. feature-filemanager gets a FilePreviewSheet for code preview.

**Tech Stack:** Sora-Editor (io.github.Rosemoe.sora-editor:editor + language-textmate + bundled themes), OkHttp + Moshi (already deps) for Anthropic API, Compose + Hilt.

**Depends on:** Phase 3 (FileSystemRepository for file content), Phase 4 (Terminal with reader coroutine), Phase 5 optional.

---

## Key Design Decisions

1. **No Anthropic Kotlin SDK** -- As of 2026-04, there is no stable first-party Anthropic SDK for Kotlin/JVM. We build a minimal client using OkHttp + Moshi for the specific endpoints we need (`/v1/messages` with streaming).

2. **Claude API key storage** -- Uses existing `KeyStoreManager` (AES-256-GCM). Stored as a single app-level secret, not per-server (Claude runs in the cloud, not on SSH hosts).

3. **Sora-Editor** -- Apache 2.0, TextMate grammars, 30+ languages. Wrapped in `AndroidView {}` Compose composable.

4. **Session Recorder** -- Writes terminal output to a local file in app-private storage (`filesDir/sessions/{sessionId}.log`). SessionLogEntity tracks the metadata. Export as Markdown on demand.

5. **Code Preview vs Code Editor** -- Free tier = read-only preview via Sora-Editor, Premium = full edit. This is handled via Feature Gate (Phase 9).

---

## File Structure

```
core/
├── core-ai/                          # NEW MODULE
│   ├── build.gradle.kts
│   ├── src/main/kotlin/dev/ori/core/ai/
│   │   ├── ClaudeApiClient.kt        # OkHttp-based
│   │   ├── ClaudeApiService.kt       # Interface
│   │   ├── ClaudeApiServiceImpl.kt
│   │   ├── model/
│   │   │   ├── ClaudeMessage.kt
│   │   │   ├── ClaudeRequest.kt
│   │   │   └── ClaudeResponse.kt
│   │   └── di/
│   │       └── AiModule.kt

domain/src/main/kotlin/dev/ori/domain/
├── model/
│   └── SessionRecording.kt
├── repository/
│   ├── ClaudeRepository.kt
│   └── SessionRecordingRepository.kt
├── usecase/
│   ├── SendToClaudeUseCase.kt
│   ├── StartSessionRecordingUseCase.kt
│   ├── StopSessionRecordingUseCase.kt
│   └── ExportSessionRecordingUseCase.kt

data/src/main/kotlin/dev/ori/data/
├── repository/
│   ├── ClaudeRepositoryImpl.kt
│   └── SessionRecordingRepositoryImpl.kt
├── di/
│   └── AiModule.kt

feature-editor/src/main/kotlin/dev/ori/feature/editor/
├── ui/
│   ├── CodeEditorScreen.kt
│   ├── CodeEditorViewModel.kt
│   ├── CodeEditorUiState.kt
│   ├── SoraEditorView.kt             # AndroidView wrapper
│   └── LanguageDetector.kt
├── navigation/
│   └── EditorNavigation.kt

feature-terminal/src/main/kotlin/dev/ori/feature/terminal/
├── ui/
│   ├── SendToClaudeSheet.kt          # NEW
│   ├── SessionRecorderIndicator.kt   # NEW
│   └── (existing files get updates)

feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/
├── ui/
│   ├── FilePreviewSheet.kt           # NEW
```

---

### Task 6.1: core-ai -- New Module for Anthropic API

**Files:**
- Create: `core/core-ai/build.gradle.kts`
- Modify: `settings.gradle.kts` (add `:core:core-ai`)
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/model/ClaudeMessage.kt`
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/model/ClaudeRequest.kt`
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/model/ClaudeResponse.kt`
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/ClaudeApiService.kt`
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/ClaudeApiServiceImpl.kt`
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/di/AiModule.kt`
- Test: `core/core-ai/src/test/kotlin/dev/ori/core/ai/ClaudeApiServiceImplTest.kt`

- [ ] **Step 1: Create core-ai module**

Add to `settings.gradle.kts`:
```kotlin
include(":core:core-ai")
```

Create `core/core-ai/build.gradle.kts` as an Android library module with:
- Hilt, KSP, core-common dependency
- OkHttp, OkHttp logging-interceptor, Moshi, Moshi codegen (already in catalog)
- Test deps (junit5, mockk, truth, mockwebserver)

Java 21, compileSdk 36, minSdk 34, namespace `dev.ori.core.ai`.

- [ ] **Step 2: Create Claude data models**

```kotlin
// ClaudeMessage.kt
package dev.ori.core.ai.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClaudeMessage(
    val role: String, // "user" or "assistant"
    val content: String,
)
```

```kotlin
// ClaudeRequest.kt
package dev.ori.core.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClaudeRequest(
    val model: String = "claude-opus-4-6",
    @Json(name = "max_tokens") val maxTokens: Int = 4096,
    val messages: List<ClaudeMessage>,
    val system: String? = null,
    val stream: Boolean = false,
)
```

```kotlin
// ClaudeResponse.kt
package dev.ori.core.ai.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @Json(name = "stop_reason") val stopReason: String?,
    val usage: Usage,
) {
    @JsonClass(generateAdapter = true)
    data class ContentBlock(
        val type: String,
        val text: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class Usage(
        @Json(name = "input_tokens") val inputTokens: Int,
        @Json(name = "output_tokens") val outputTokens: Int,
    )
}
```

- [ ] **Step 3: Create ClaudeApiService interface**

```kotlin
package dev.ori.core.ai

import dev.ori.core.ai.model.ClaudeMessage
import dev.ori.core.ai.model.ClaudeResponse
import dev.ori.core.common.result.AppResult

interface ClaudeApiService {
    suspend fun sendMessage(
        apiKey: String,
        messages: List<ClaudeMessage>,
        system: String? = null,
        model: String = "claude-opus-4-6",
    ): AppResult<ClaudeResponse>
}
```

- [ ] **Step 4: Create ClaudeApiServiceImpl**

Wraps OkHttp + Moshi. Endpoint: `https://api.anthropic.com/v1/messages`. Headers:
- `x-api-key: <apiKey>`
- `anthropic-version: 2023-06-01`
- `content-type: application/json`

Handles:
- 200 -> parse ClaudeResponse, return appSuccess
- 401 -> appFailure(AppError.AuthenticationError("Invalid API key"))
- 429 -> appFailure(AppError.NetworkError("Rate limit exceeded"))
- Other -> appFailure(AppError.NetworkError("Claude API error: $code"))
- Network exceptions -> appFailure(AppError.NetworkError(...))

- [ ] **Step 5: Create AiModule (Hilt)**

@Module @InstallIn(SingletonComponent) providing:
- OkHttpClient (singleton) with 60s timeout
- Moshi instance
- @Binds ClaudeApiServiceImpl -> ClaudeApiService

- [ ] **Step 6: Write ClaudeApiServiceImplTest**

Use MockWebServer to test:
- sendMessage_success_returnsResponse
- sendMessage_401_returnsAuthError
- sendMessage_429_returnsNetworkError
- sendMessage_networkFailure_returnsError

- [ ] **Step 7: Run tests and commit**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew :core:core-ai:test`
Message: `feat(core-ai): add Claude Anthropic API client with OkHttp`

---

### Task 6.2: domain -- Claude and Session Recording

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/model/SessionRecording.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/ClaudeRepository.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/SessionRecordingRepository.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/SendToClaudeUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/StartSessionRecordingUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/StopSessionRecordingUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/ExportSessionRecordingUseCase.kt`

- [ ] **Step 1: Create SessionRecording domain model**

```kotlin
package dev.ori.domain.model

data class SessionRecording(
    val id: Long = 0,
    val serverProfileId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val logFilePath: String,
)
```

- [ ] **Step 2: Create ClaudeRepository interface**

```kotlin
package dev.ori.domain.repository

import dev.ori.core.common.result.AppResult

interface ClaudeRepository {
    suspend fun hasApiKey(): Boolean
    suspend fun setApiKey(apiKey: String)
    suspend fun sendPrompt(userMessage: String, context: String? = null): AppResult<String>
}
```

- [ ] **Step 3: Create SessionRecordingRepository interface**

```kotlin
package dev.ori.domain.repository

import dev.ori.domain.model.SessionRecording
import kotlinx.coroutines.flow.Flow

interface SessionRecordingRepository {
    suspend fun startRecording(serverProfileId: Long): SessionRecording
    suspend fun appendOutput(recordingId: Long, data: ByteArray)
    suspend fun stopRecording(recordingId: Long)
    suspend fun exportAsMarkdown(recordingId: Long): String
    fun getRecordingsForServer(serverProfileId: Long): Flow<List<SessionRecording>>
}
```

- [ ] **Step 4: Create use cases**

All 4 use cases follow the standard pattern with @Inject constructor injecting the respective repository.

- [ ] **Step 5: Commit**

Message: `feat(domain): add Claude and session recording repositories and use cases`

---

### Task 6.3: data -- ClaudeRepositoryImpl + SessionRecordingRepositoryImpl

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/repository/ClaudeRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/repository/SessionRecordingRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/di/AiModule.kt`
- Modify: `data/build.gradle.kts` (add core-ai dependency)

- [ ] **Step 1: Add core-ai dependency to data module**

```kotlin
implementation(project(":core:core-ai"))
```

- [ ] **Step 2: Create ClaudeRepositoryImpl**

@Singleton. Injects:
- `ClaudeApiService` (from core-ai)
- `EncryptedPrefsManager` (from core-security) -- for API key storage
- `KeyStoreManager` (or CredentialStore) -- alternative

Key logic:
- `hasApiKey()` -- check EncryptedPrefs for key
- `setApiKey()` -- store in EncryptedPrefs (NOT Keystore since Claude API key is app-level)
- `sendPrompt()` -- build messages list, optionally include context, call ClaudeApiService, extract first content block text, return Result<String>

NOTE: The project currently has `KeyStoreManager` but may not have `EncryptedPrefsManager`. If not, use `KeyStoreManager.storeSshKey(alias = "claude_api_key", privateKey = apiKey.toByteArray())` as a workaround, OR create a new simpler store for app-level secrets.

- [ ] **Step 3: Create SessionRecordingRepositoryImpl**

@Singleton. Injects `SessionLogDao`, `@ApplicationContext Context`.

- `startRecording()`: create log file in `context.filesDir/sessions/{uuid}.log`, insert SessionLogEntity, return SessionRecording
- `appendOutput()`: append bytes to the log file via FileOutputStream(append=true)
- `stopRecording()`: update endedAt in entity
- `exportAsMarkdown()`: read log file, wrap in markdown code block with server name and timestamp header
- `getRecordingsForServer()`: Flow from DAO

- [ ] **Step 4: Create AiModule in data**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindClaudeRepository(impl: ClaudeRepositoryImpl): ClaudeRepository

    @Binds
    @Singleton
    abstract fun bindSessionRecordingRepository(impl: SessionRecordingRepositoryImpl): SessionRecordingRepository
}
```

- [ ] **Step 5: Commit**

Message: `feat(data): add Claude and session recording repository implementations`

---

### Task 6.4: feature-editor -- Sora-Editor Integration

**Files:**
- Modify: `feature-editor/build.gradle.kts` (add sora-editor, language-textmate, bundled-theme)
- Modify: `gradle/libs.versions.toml` (add sora-editor language-textmate, editor bundled themes)
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/SoraEditorView.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/LanguageDetector.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/CodeEditorUiState.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/CodeEditorViewModel.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/CodeEditorScreen.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/navigation/EditorNavigation.kt`
- Modify: OriDevNavHost.kt

- [ ] **Step 1: Verify Sora-Editor library exists and add deps**

Check Maven Central:
```bash
curl -s "https://repo.maven.apache.org/maven2/io/github/Rosemoe/sora-editor/editor/maven-metadata.xml" | head -20
```

Sora-Editor artifact coordinates:
- `io.github.Rosemoe.sora-editor:editor:0.24.2`
- `io.github.Rosemoe.sora-editor:language-textmate:0.24.2` -- TextMate grammar engine
- `io.github.Rosemoe.sora-editor:editor-lsp:0.24.2` (optional, skip for now)

Add to `libs.versions.toml`:
```toml
# In [libraries]
sora-editor = { module = "io.github.Rosemoe.sora-editor:editor", version.ref = "sora-editor" }
sora-editor-textmate = { module = "io.github.Rosemoe.sora-editor:language-textmate", version.ref = "sora-editor" }
```

Add to `feature-editor/build.gradle.kts`:
```kotlin
implementation(project(":core:core-common"))
implementation(project(":core:core-ui"))
implementation(project(":domain"))
implementation(libs.sora.editor)
implementation(libs.sora.editor.textmate)
implementation(libs.compose.material.icons.extended)
// Compose + Hilt + ViewModel deps
```

Add `useJUnitPlatform()` to test task.

If Sora-Editor library does NOT resolve, fall back to a simple Compose-based editor (TextField with monospace) and document as a known limitation.

- [ ] **Step 2: Create LanguageDetector**

```kotlin
package dev.ori.feature.editor.ui

object LanguageDetector {
    fun detectLanguage(filename: String): String = when {
        filename.endsWith(".kt") || filename.endsWith(".kts") -> "kotlin"
        filename.endsWith(".java") -> "java"
        filename.endsWith(".py") -> "python"
        filename.endsWith(".js") || filename.endsWith(".mjs") -> "javascript"
        filename.endsWith(".ts") || filename.endsWith(".tsx") -> "typescript"
        filename.endsWith(".php") -> "php"
        filename.endsWith(".sh") || filename.endsWith(".bash") -> "shell"
        filename.endsWith(".yml") || filename.endsWith(".yaml") -> "yaml"
        filename.endsWith(".json") -> "json"
        filename.endsWith(".xml") -> "xml"
        filename.endsWith(".md") || filename.endsWith(".markdown") -> "markdown"
        filename.endsWith(".html") || filename.endsWith(".htm") -> "html"
        filename.endsWith(".css") -> "css"
        filename.endsWith(".rs") -> "rust"
        filename.endsWith(".go") -> "go"
        filename.endsWith(".c") || filename.endsWith(".h") -> "c"
        filename.endsWith(".cpp") || filename.endsWith(".hpp") -> "cpp"
        else -> "text"
    }
}
```

- [ ] **Step 3: Create SoraEditorView Composable**

AndroidView wrapper around `io.github.rosemoe.sora.widget.CodeEditor`. Parameters:
- `content: String`
- `language: String`
- `readOnly: Boolean = false`
- `onContentChange: (String) -> Unit`

Inside AndroidView.factory: create `CodeEditor(context)`. Inside update: set text if changed, update language (via TextMate grammar lookup), set read-only flag.

TextMate grammar loading: for each language, use the bundled grammars from sora-editor-textmate. The library provides `TextMateLanguage.create(scopeName, langServer = false)` but scope name needs grammar registry setup.

SIMPLER APPROACH: For this phase, skip TextMate and use `EmptyLanguage` with just monospace font. This gives a working editor without complex grammar setup. Document as TODO: "Full syntax highlighting via TextMate grammars deferred to follow-up task."

- [ ] **Step 4: Create CodeEditorUiState**

```kotlin
data class CodeEditorUiState(
    val filename: String = "",
    val content: String = "",
    val originalContent: String = "", // for dirty state detection
    val language: String = "text",
    val isLoading: Boolean = false,
    val isReadOnly: Boolean = false,
    val error: String? = null,
    val savedSuccessfully: Boolean = false,
)

val CodeEditorUiState.isDirty: Boolean get() = content != originalContent

sealed class CodeEditorEvent {
    data class ContentChanged(val newContent: String) : CodeEditorEvent()
    data object Save : CodeEditorEvent()
    data object ClearSavedMessage : CodeEditorEvent()
    data object ClearError : CodeEditorEvent()
}
```

- [ ] **Step 5: Create CodeEditorViewModel**

@HiltViewModel. Injects `FileSystemRepository` (local for now) via `@LocalFileSystem` qualifier, `SavedStateHandle` for filePath param.

- Init: load file content via `FileSystemRepository.getFileContent(path)`, detect language from filename, set uiState
- ContentChanged: update content in state
- Save: call `FileSystemRepository.writeFileContent(path, bytes)`, update originalContent, set savedSuccessfully flag

- [ ] **Step 6: Create CodeEditorScreen**

Scaffold with OriDevTopBar(filename + modified dot if dirty). Save button (enabled when dirty). SoraEditorView fills the content. Snackbar for errors and save confirmations.

- [ ] **Step 7: Create EditorNavigation**

```kotlin
const val EDITOR_ROUTE_BASE = "editor"
const val EDITOR_ROUTE = "$EDITOR_ROUTE_BASE/{filePath}"

fun NavGraphBuilder.editorScreen() {
    composable(
        route = EDITOR_ROUTE,
        arguments = listOf(navArgument("filePath") { type = NavType.StringType }),
    ) {
        CodeEditorScreen()
    }
}

fun NavController.navigateToEditor(filePath: String) {
    // URL-encode the path
    val encoded = java.net.URLEncoder.encode(filePath, "UTF-8")
    navigate("$EDITOR_ROUTE_BASE/$encoded")
}
```

- [ ] **Step 8: Update OriDevNavHost to register editor route**

Add `editorScreen()` call in NavHost builder. Import from feature-editor navigation.

- [ ] **Step 9: Remove .gitkeep, commit**

Message: `feat(editor): add Sora-Editor integration with CodeEditorScreen`

---

### Task 6.5: feature-terminal -- Session Recording + Send to Claude

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/SendToClaudeSheet.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt`
- Modify: `feature-terminal/build.gradle.kts` (add domain session recording access)

- [ ] **Step 1: Update TerminalUiState**

Add to TerminalUiState:
```kotlin
val isRecording: Boolean = false,
val recordingId: Long? = null,
val showSendToClaude: Boolean = false,
val claudeResponse: String? = null,
val claudeLoading: Boolean = false,
val selectedOutput: String? = null, // for "Send to Claude"
```

Add to TerminalEvent sealed class:
```kotlin
data object StartRecording : TerminalEvent()
data object StopRecording : TerminalEvent()
data object ExportRecording : TerminalEvent()
data class ShowSendToClaude(val selectedText: String) : TerminalEvent()
data object HideSendToClaude : TerminalEvent()
data class SendToClaude(val prompt: String) : TerminalEvent()
```

- [ ] **Step 2: Update TerminalViewModel**

Inject:
- `StartSessionRecordingUseCase`
- `StopSessionRecordingUseCase`
- `ExportSessionRecordingUseCase`
- `SendToClaudeUseCase`

In the reader coroutine that feeds bytes to termlib emulator, ALSO append bytes to the active recording (if `recordingId != null`) via `sessionRecordingRepository.appendOutput()`.

Handle new events:
- StartRecording: create recording via use case, store id in state
- StopRecording: call stop use case, update state
- ExportRecording: call export use case, show result as snackbar or share intent
- ShowSendToClaude: set selectedOutput + showSendToClaude
- SendToClaude: call SendToClaudeUseCase with prompt + context, set claudeLoading, then claudeResponse
- HideSendToClaude: clear dialog state

- [ ] **Step 3: Create SendToClaudeSheet**

ModalBottomSheet with:
- Header "Send to Claude"
- Selected terminal output (collapsible)
- TextField for user prompt (e.g., "Explain this error", "Review this code")
- Send button -> triggers SendToClaude event
- Response area (when claudeResponse set): scrollable text, monospace
- Copy response button

- [ ] **Step 4: Update TerminalScreen**

Add floating "Send to Claude" pill button (bottom-right corner, indigo). When terminal has selected text and user taps it, trigger ShowSendToClaude.

Add recording indicator to TerminalTabBar: red dot next to tab name if recording.

Toggle recording via menu action in top bar.

- [ ] **Step 5: Commit**

Message: `feat(terminal): add session recording and Send to Claude integration`

---

### Task 6.6: feature-filemanager -- File Preview Sheet

**Files:**
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FilePreviewSheet.kt`
- Modify: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileManagerViewModel.kt`

- [ ] **Step 1: Create FilePreviewSheet**

ModalBottomSheet showing:
- Filename + file info (size, permissions)
- SoraEditorView (read-only) with content loaded
- Actions: Open in Editor (navigates to CodeEditorScreen), Close

- [ ] **Step 2: Update FileManagerViewModel**

When user taps a FILE (not directory), instead of just showing FileInfoSheet, load content (first 100KB max for performance) and show preview with SoraEditorView.

Add to UiState: `previewFileContent: String? = null`

Add events: LoadFilePreview(path), ClosePreview

- [ ] **Step 3: Commit**

Message: `feat(filemanager): add file preview sheet with code editor integration`

---

### Task 6.7: Tests + Verify + Push + CI

**Files:**
- Test: `core/core-ai/src/test/kotlin/dev/ori/core/ai/ClaudeApiServiceImplTest.kt` (already from 6.1)
- Test: `feature-editor/src/test/kotlin/dev/ori/feature/editor/ui/CodeEditorViewModelTest.kt`
- Test: `data/src/test/kotlin/dev/ori/data/repository/SessionRecordingRepositoryImplTest.kt`

- [ ] **Step 1: Write CodeEditorViewModelTest**

6 tests: init loads content, contentChanged updates state and dirty flag, save calls writeFileContent, save success clears dirty, save failure sets error, language detected from filename.

- [ ] **Step 2: Write SessionRecordingRepositoryImplTest**

4 tests: startRecording creates file and entity, appendOutput writes to file, stopRecording updates entity, exportAsMarkdown wraps in markdown.

Use a temp directory for filesDir in tests (inject Context or use a test fake).

- [ ] **Step 3: Run all checks**

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew detekt
./gradlew test
./gradlew assembleDebug
```

All must pass. Fix any detekt violations (Compose functions, complexity, etc.).

- [ ] **Step 4: Commit and push**

Commits:
- `test(editor): add CodeEditorViewModel tests`
- `test(data): add SessionRecordingRepository tests`

Then: `git push origin master`

- [ ] **Step 5: Monitor CI until green**

`gh run list --branch master --limit 5` -- wait for Build & Test: success. If failure, read `gh run view <id> --log-failed`, fix, commit, push, repeat.

CRITICAL: Do not consider done until CI is green.

---

## Phase 6 Completion Checklist

- [ ] `core-ai` (new module): ClaudeApiService + Impl (OkHttp), data models, Hilt module, tests
- [ ] `domain`: SessionRecording model, ClaudeRepository + SessionRecordingRepository interfaces, 4 use cases
- [ ] `data`: ClaudeRepositoryImpl (API key via EncryptedPrefs), SessionRecordingRepositoryImpl (file-based), AiModule
- [ ] `feature-editor`: SoraEditorView, LanguageDetector, CodeEditorViewModel, CodeEditorScreen, navigation -- NOTE: TextMate grammars may be deferred
- [ ] `feature-terminal`: Session recording wired to reader coroutine, SendToClaudeSheet, recording indicator, TerminalViewModel updates
- [ ] `feature-filemanager`: FilePreviewSheet with SoraEditorView
- [ ] All tests pass, detekt clean, build succeeds, CI green

## Known Limitations (Documented)

1. **TextMate grammars deferred**: Full syntax highlighting requires loading grammar JSON files at runtime. For v1, Sora-Editor is used with plain text mode (monospace, no highlighting). Follow-up task will wire TextMate grammars per language.
2. **Claude streaming**: v1 uses non-streaming API. Response comes back all at once. Streaming deferred.
3. **API key setup UX**: No settings screen for Claude API key yet (Phase 9 has Settings). For v1, the key must be set programmatically via ClaudeRepository. Add a temporary dialog in Send to Claude sheet if no key is set.
4. **Git diff gutter in editor**: Sora-Editor supports gutter annotations but requires diff computation. Deferred to a follow-up.
5. **Auto-detect code blocks in terminal output**: Feature spec mentions detecting code blocks in terminal output. This requires real-time regex matching on terminal buffer. Deferred.
