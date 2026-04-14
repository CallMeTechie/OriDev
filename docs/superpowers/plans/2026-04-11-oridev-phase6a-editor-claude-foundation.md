# Ori:Dev Phase 6a: Code Editor & Claude Integration -- Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the FOUNDATION for Code Editor and Claude Integration: Sora-Editor with TextMate grammars (10+ languages), Claude API client with proper error handling, Session Recorder with channel-based writer, "Send to Claude" from terminal, SFTP-capable editor, search & replace, tab bar, file preview in file manager.

**Architecture:** New `core-ai` module with minimal OkHttp/Moshi Claude client. New Task 6.0 fixes `KeyStoreManager` persistence (was ConcurrentHashMap, now DataStore-backed AES/GCM). Editor supports BOTH local AND remote (SFTP) files via qualified `FileSystemRepository`. Session Recorder uses a dedicated Channel + writer coroutine to avoid blocking the terminal reader.

**Tech Stack:** Sora-Editor 0.24.2 + language-textmate + bundled themes (Maven Central verified), OkHttp + Moshi (existing), DataStore for API key persistence, Compose + Hilt.

**Depends on:** Phase 3 (FileSystemRepository + qualifiers), Phase 4 (Terminal with reader coroutine), Phase 5 not required.

**Scope split:** Phase 6a covers spec tasks 6.1-6.7 + 6.11. Phase 6b (separate follow-up plan) covers spec tasks 6.8 (Snippet Manager), 6.9 (Auto-Detect Code Blocks), 6.10 (Inline Diff Viewer), 6.12 (Git Status Overlay), and Git Diff Gutter in editor. This split keeps Phase 6a under ~150 commits worth of scope.

**Review fixes applied (v1 -> v2):**
- Added Task 6.0 to fix KeyStoreManager persistence BEFORE any API key usage
- TextMate grammars are NOT deferred -- included as mandatory Task 6.4b
- SFTP file loading in editor via both `@LocalFileSystem` and `@RemoteFileSystem` qualifiers
- Search & Replace and Tab Bar included in editor
- Session Recorder uses Channel + dedicated writer coroutine (non-blocking)
- Claude API: 429 retry-after parsing, 400 body parsing, 529 overload handling, structured error bodies
- Explicit `ksp(libs.moshi.codegen)` in core-ai build
- Explicit `useJUnitPlatform()` in feature-editor build
- Tests for ClaudeRepositoryImpl + all use cases
- KeyStore-only for API key (per CLAUDE.md rule), EncryptedPrefs dropped
- Stub FeatureGateManager interface in core-common for Phase 9 compatibility

---

### Task 6.0: Fix KeyStoreManager Persistence (PREREQUISITE)

**Files:**
- Modify: `core/core-security/src/main/kotlin/dev/ori/core/security/KeyStoreManager.kt`
- Create: `core/core-security/src/main/kotlin/dev/ori/core/security/EncryptedStore.kt`
- Modify: `core/core-security/build.gradle.kts` (add datastore-preferences)
- Test: `core/core-security/src/androidTest/kotlin/dev/ori/core/security/KeyStoreManagerTest.kt`

- [ ] **Step 1: Verify bug**

Read `/root/OriDev/core/core-security/src/main/kotlin/dev/ori/core/security/KeyStoreManager.kt`. Confirm it uses `ConcurrentHashMap<String, ByteArray>` for storage (not persisted).

- [ ] **Step 2: Add DataStore dependency**

In `core/core-security/build.gradle.kts` add:
```kotlin
implementation(libs.datastore.preferences)
```
(already in version catalog as `datastore = "1.1.4"`)

- [ ] **Step 3: Refactor KeyStoreManager to persist**

Replace the `ConcurrentHashMap` with `DataStore<Preferences>`. The AES/GCM wrapping via AndroidKeyStore stays; only the ciphertext storage changes. Keys become Preferences entries with Base64-encoded `[IV || ciphertext]`.

Key design:
- Master AES key remains in AndroidKeyStore under alias `oridev_master`
- Each credential is encrypted with the master key (AES-256-GCM, random 12-byte IV)
- Ciphertext + IV is Base64-encoded and stored in DataStore under `credential_<alias>`
- `storePassword`, `storeSshKey`, `getPassword`, `getSshKey`, `deleteCredential`, `hasCredential` all become DataStore operations

NOTE: This is a breaking change for any existing in-memory credentials, but since the app has never been released, migration is not needed.

- [ ] **Step 4: Write instrumented test**

`core/core-security/src/androidTest/kotlin/dev/ori/core/security/KeyStoreManagerTest.kt`:
- `storeAndRetrievePassword_succeedsAfterProcessRestart` -- test with a second KeyStoreManager instance (same process) that credentials survive
- `storeSshKey_persistsBytes`
- `deleteCredential_removesFromStore`
- `hasCredential_returnsTrueAfterStore`

- [ ] **Step 5: Verify existing ConnectionRepositoryImpl still works**

Read the repository to ensure the `CredentialStore` interface contract hasn't changed -- only the implementation.

- [ ] **Step 6: Run tests and commit**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew :core:core-security:test && ./gradlew assembleDebug`
Message: `fix(core-security): persist credentials via DataStore instead of in-memory map`

---

### Task 6.1: core-ai -- New Module for Anthropic API

**Files:**
- Modify: `settings.gradle.kts` (add `:core:core-ai`)
- Create: `core/core-ai/build.gradle.kts`
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/model/*.kt` (3 files)
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/ClaudeApiService.kt`
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/ClaudeApiServiceImpl.kt`
- Create: `core/core-ai/src/main/kotlin/dev/ori/core/ai/di/AiNetworkModule.kt`
- Test: `core/core-ai/src/test/kotlin/dev/ori/core/ai/ClaudeApiServiceImplTest.kt`

- [ ] **Step 1: Verify Sora-Editor and Moshi codegen available**

```bash
curl -s "https://repo.maven.apache.org/maven2/io/github/Rosemoe/sora-editor/editor/maven-metadata.xml" | grep -o '<latest>[^<]*</latest>'
curl -s "https://repo.maven.apache.org/maven2/com/squareup/moshi/moshi-kotlin-codegen/maven-metadata.xml" | grep -o '<latest>[^<]*</latest>'
```

If sora-editor latest differs from 0.24.2, update version catalog. If library unavailable, STOP and report.

- [ ] **Step 2: Create core-ai module**

Add to `settings.gradle.kts`:
```kotlin
include(":core:core-ai")
```

Create `core/core-ai/build.gradle.kts` as Android library:
- Namespace: `dev.ori.core.ai`
- Java 21, compileSdk 36, minSdk 34
- Dependencies: `:core:core-common`, OkHttp, OkHttp logging-interceptor, Moshi, Moshi kotlin
- KSP: `ksp(libs.moshi.codegen)` -- CRITICAL, without this @JsonClass fails
- Hilt + KSP processor
- Test: JUnit 5, MockK, Truth, MockWebServer
- `useJUnitPlatform()` in test task

- [ ] **Step 3: Create Claude data models (Moshi)**

Three files in `core-ai/src/main/kotlin/dev/ori/core/ai/model/`:

**ClaudeMessage.kt:** `@JsonClass(generateAdapter = true) data class ClaudeMessage(val role: String, val content: String)`

**ClaudeRequest.kt:** `@JsonClass(generateAdapter = true) data class ClaudeRequest(val model: String, @Json(name="max_tokens") val maxTokens: Int = 4096, val messages: List<ClaudeMessage>, val system: String? = null, val stream: Boolean = false)`

**ClaudeResponse.kt:** Full response with `content: List<ContentBlock>`, `Usage` nested class, `stopReason`, etc. Plus an `ErrorResponse` class for parsed error bodies: `data class ErrorResponse(val type: String, val error: ErrorDetail)` where ErrorDetail has `type` and `message`.

- [ ] **Step 4: Create ClaudeApiService interface**

```kotlin
interface ClaudeApiService {
    suspend fun sendMessage(
        apiKey: String,
        messages: List<ClaudeMessage>,
        system: String? = null,
        model: String = DEFAULT_MODEL,
    ): AppResult<ClaudeResponse>

    companion object {
        const val DEFAULT_MODEL = "claude-opus-4-6"
        // CRITICAL: Verify exact model ID via `curl https://api.anthropic.com/v1/models` before release.
        // Anthropic may use dated IDs like "claude-opus-4-5-20250929". If 404 at runtime, update this constant.
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
```

- [ ] **Step 5: Create ClaudeApiServiceImpl with proper error handling**

Key features (addresses review HIGH issues):
- Endpoint: `https://api.anthropic.com/v1/messages`
- Headers: `x-api-key`, `anthropic-version: 2023-06-01`, `content-type: application/json`
- OkHttpClient: connectTimeout=15s, readTimeout=120s, callTimeout=180s (non-streaming can take a while)
- **Retry logic** for 429/529/5xx: exponential backoff, parse `retry-after` header, max 3 attempts
- **Error body parsing**: on any 4xx/5xx, attempt to parse `ErrorResponse` from response body. Use `error.message` in the AppError message.
- **Error mapping**:
  - 200 -> parse and return success
  - 400 -> `AppError.FileOperationError("Claude request error: ${error.message}")` (semantic: client sent bad data)
  - 401/403 -> `AppError.AuthenticationError("Claude auth: ${error.message ?: "invalid key"}")`
  - 429 -> retry up to 3 times with backoff honoring retry-after; if still failing, `NetworkError("Rate limited: ${error.message}")`
  - 529 -> retry with backoff (Anthropic overloaded)
  - Other -> `NetworkError("Claude API error $code: ${error.message}")`
- **Request size guard**: if combined message bytes > 200KB, return `AppError.FileOperationError("Request too large, max 200KB")` without calling the API
- **Logging interceptor**: level = HEADERS (never BODY to avoid leaking API key)

- [ ] **Step 6: Create AiNetworkModule (Hilt)**

Provides: OkHttpClient singleton, Moshi instance, @Binds ClaudeApiServiceImpl -> ClaudeApiService.

- [ ] **Step 7: Write ClaudeApiServiceImplTest with MockWebServer**

Tests:
- `sendMessage_200_returnsSuccess`
- `sendMessage_401_returnsAuthError`
- `sendMessage_400_parsesErrorMessage`
- `sendMessage_429_retriesWithBackoff`
- `sendMessage_529_retriesWithBackoff`
- `sendMessage_networkFailure_returnsNetworkError`
- `sendMessage_oversizedRequest_returnsErrorWithoutCall`
- `sendMessage_headers_includesAnthropicVersion`

- [ ] **Step 8: Run tests and commit**

Run: `./gradlew :core:core-ai:test`
Message: `feat(core-ai): add Claude API client with retry-after, error body parsing, size guard`

---

### Task 6.2: core-common -- FeatureGateManager Stub (Phase 9 compat)

**Files:**
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/feature/FeatureGateManager.kt`

- [ ] **Step 1: Create FeatureGateManager interface + stub implementation**

```kotlin
package dev.ori.core.common.feature

/**
 * Feature gate for Free vs Premium features.
 * Phase 9 will provide a real implementation. For now, the stub returns true for everything.
 */
interface FeatureGateManager {
    fun isPremium(): Boolean
    fun isFeatureEnabled(feature: PremiumFeature): Boolean
    fun maxServerProfiles(): Int
    fun maxTerminalTabs(): Int
    fun maxParallelTransfers(): Int
}

enum class PremiumFeature {
    SESSION_RECORDER,
    SEND_TO_CLAUDE,
    CODE_EDITOR_WRITE,
    FILE_WATCHER,
    BIOMETRIC_UNLOCK,
    CUSTOM_THEMES,
}

class FeatureGateManagerStub : FeatureGateManager {
    override fun isPremium(): Boolean = true
    override fun isFeatureEnabled(feature: PremiumFeature): Boolean = true
    override fun maxServerProfiles(): Int = Int.MAX_VALUE
    override fun maxTerminalTabs(): Int = Int.MAX_VALUE
    override fun maxParallelTransfers(): Int = Int.MAX_VALUE
}
```

Bind the stub in a new Hilt module in `core-common`. Phase 9 will replace the binding.

- [ ] **Step 2: Commit**

Message: `feat(core-common): add FeatureGateManager stub interface for Phase 9 compat`

---

### Task 6.3: domain -- Claude, Session Recording, Use Cases

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/model/SessionRecording.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/ClaudeRepository.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/SessionRecordingRepository.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/SendToClaudeUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/StartSessionRecordingUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/StopSessionRecordingUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/ExportSessionRecordingUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/SetClaudeApiKeyUseCase.kt`
- Test: `domain/src/test/kotlin/dev/ori/domain/usecase/ClaudeUseCaseTest.kt`
- Test: `domain/src/test/kotlin/dev/ori/domain/usecase/SessionRecordingUseCaseTest.kt`

- [ ] **Step 1: Create SessionRecording model + Repository interfaces**

As described in review response. `ClaudeRepository` has `hasApiKey`, `setApiKey`, `sendPrompt`. `SessionRecordingRepository` has `startRecording`, `appendOutput(ByteArray)`, `stopRecording`, `exportAsMarkdown`, `getRecordingsForServer`.

- [ ] **Step 2: Create 5 use cases** (including SetClaudeApiKeyUseCase for the settings UI in Phase 9)

- [ ] **Step 3: Write tests for all use cases**

Per CLAUDE.md "every public function in domain/ and data/ must have tests". 10 tests total -- 2 per use case (success + failure path).

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :domain:test`
Message: `feat(domain): add Claude, session recording repositories and use cases`

---

### Task 6.4: data -- Repository Implementations

**Files:**
- Modify: `data/build.gradle.kts` (add `:core:core-ai`)
- Create: `data/src/main/kotlin/dev/ori/data/repository/ClaudeRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/repository/SessionRecordingRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/di/AiBindingsModule.kt`
- Test: `data/src/test/kotlin/dev/ori/data/repository/ClaudeRepositoryImplTest.kt`
- Test: `data/src/test/kotlin/dev/ori/data/repository/SessionRecordingRepositoryImplTest.kt`

- [ ] **Step 1: Add core-ai dependency to data**

```kotlin
implementation(project(":core:core-ai"))
```

- [ ] **Step 2: Create ClaudeRepositoryImpl**

@Singleton. Injects `ClaudeApiService`, `CredentialStore` (from domain, backed by KeyStoreManager).

- `hasApiKey()`: `credentialStore.hasCredential(CLAUDE_API_KEY_ALIAS)`
- `setApiKey(apiKey)`: `credentialStore.storePassword(CLAUDE_API_KEY_ALIAS, apiKey.toCharArray())`
- `sendPrompt(userMessage, context)`:
  - Load API key via `credentialStore.getPassword(CLAUDE_API_KEY_ALIAS)` -> String
  - Zero-fill char[] after use
  - Build messages list: [ClaudeMessage(role="user", content=contextPrefix + userMessage)]
  - Call `claudeApiService.sendMessage(...)`
  - On success: extract first text content block, return as String
  - On failure: return failure

```kotlin
companion object {
    private const val CLAUDE_API_KEY_ALIAS = "claude_api_key"
}
```

- [ ] **Step 3: Create SessionRecordingRepositoryImpl with Channel-based writer**

CRITICAL: Use dedicated writer coroutine with Channel to avoid blocking reader (fixes HIGH severity race issues).

```kotlin
@Singleton
class SessionRecordingRepositoryImpl @Inject constructor(
    private val dao: SessionLogDao,
    @ApplicationContext private val context: Context,
) : SessionRecordingRepository {

    private data class ActiveWriter(
        val channel: Channel<ByteArray>,
        val writerJob: Job,
        val file: File,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeWriters = ConcurrentHashMap<Long, ActiveWriter>()

    override suspend fun startRecording(serverProfileId: Long): SessionRecording {
        val sessionsDir = File(context.filesDir, "sessions").apply { mkdirs() }
        val file = File(sessionsDir, "${UUID.randomUUID()}.log")
        file.createNewFile()

        val entity = SessionLogEntity(
            serverProfileId = serverProfileId,
            startedAt = System.currentTimeMillis(),
            logFilePath = file.absolutePath,
        )
        val id = dao.insert(entity)

        // Start dedicated writer coroutine
        val channel = Channel<ByteArray>(capacity = 256, onBufferOverflow = BufferOverflow.SUSPEND)
        val writerJob = scope.launch {
            file.outputStream().buffered().use { out ->
                for (bytes in channel) {
                    out.write(bytes)
                    out.flush()
                }
            }
        }

        activeWriters[id] = ActiveWriter(channel, writerJob, file)
        return SessionRecording(id, serverProfileId, entity.startedAt, null, file.absolutePath)
    }

    override suspend fun appendOutput(recordingId: Long, data: ByteArray) {
        // Non-suspending send (drop-on-full would lose data; SUSPEND is safer even on reader thread)
        activeWriters[recordingId]?.channel?.send(data.copyOf())  // copy since reader buffer is reused
    }

    override suspend fun stopRecording(recordingId: Long) {
        val active = activeWriters.remove(recordingId) ?: return
        active.channel.close()
        active.writerJob.join()  // ensure all queued bytes are flushed

        dao.getById(recordingId)?.let { entity ->
            dao.update(entity.copy(endedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun exportAsMarkdown(recordingId: Long): String {
        val entity = dao.getById(recordingId) ?: return ""
        val file = File(entity.logFilePath)
        if (!file.exists()) return ""

        val timestamp = Instant.ofEpochMilli(entity.startedAt).toString()
        val rawLog = file.readText(Charsets.UTF_8)

        return buildString {
            appendLine("# Terminal Session Recording")
            appendLine()
            appendLine("- **Started:** $timestamp")
            appendLine("- **Server Profile ID:** ${entity.serverProfileId}")
            appendLine()
            appendLine("```")
            append(rawLog)
            appendLine()
            appendLine("```")
        }
    }

    override fun getRecordingsForServer(serverProfileId: Long): Flow<List<SessionRecording>> =
        dao.getForServer(serverProfileId).map { entities ->
            entities.map { e -> SessionRecording(e.id, e.serverProfileId, e.startedAt, e.endedAt, e.logFilePath) }
        }
}
```

Note: `appendOutput()` calls `data.copyOf()` to avoid the reader buffer reuse race.

- [ ] **Step 4: Create AiBindingsModule**

Hilt @Module @Binds for ClaudeRepositoryImpl and SessionRecordingRepositoryImpl.

- [ ] **Step 5: Write tests**

**ClaudeRepositoryImplTest:**
- `sendPrompt_noApiKey_returnsError`
- `sendPrompt_success_returnsFirstContentBlockText`
- `sendPrompt_apiFailure_returnsError`
- `setApiKey_storesInCredentialStore`
- `hasApiKey_returnsTrueAfterSet`

**SessionRecordingRepositoryImplTest:**
- `startRecording_createsFileAndEntity`
- `appendOutput_writesToFile`
- `stopRecording_updatesEndedAtAndFlushesWriter`
- `exportAsMarkdown_wrapsLogInMarkdown`
- `appendAfterStop_doesNotCrash`
- `multipleAppends_preservesOrder`
- `concurrentAppends_noDataLoss` -- launches 10 coroutines writing in parallel

Uses a temp directory for test `filesDir`.

- [ ] **Step 6: Run tests and commit**

Message: `feat(data): add Claude and session recording repository implementations with channel writer`

---

### Task 6.4b: Sora-Editor + TextMate Grammar Loading

**Files:**
- Modify: `gradle/libs.versions.toml` (add sora-editor-textmate and themes)
- Modify: `feature-editor/build.gradle.kts`
- Create: `feature-editor/src/main/assets/textmate/grammars/*.json` (10+ grammar files from tm4e)
- Create: `feature-editor/src/main/assets/textmate/themes/*.json` (light theme)
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/SoraEditorView.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/TextMateLoader.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/LanguageDetector.kt`

- [ ] **Step 1: Add dependencies**

```toml
# In libs.versions.toml [libraries]
sora-editor = { module = "io.github.Rosemoe.sora-editor:editor", version.ref = "sora-editor" }
sora-editor-textmate = { module = "io.github.Rosemoe.sora-editor:language-textmate", version.ref = "sora-editor" }
```

In `feature-editor/build.gradle.kts`:
```kotlin
implementation(project(":core:core-common"))
implementation(project(":core:core-ui"))
implementation(project(":domain"))
implementation(libs.sora.editor)
implementation(libs.sora.editor.textmate)
implementation(libs.compose.material.icons.extended)

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Download TextMate grammar files**

For each language, download the `*.tmLanguage.json` file from tm4e or microsoft/vscode repo and place in `feature-editor/src/main/assets/textmate/grammars/`:

- kotlin.tmLanguage.json
- java.tmLanguage.json
- python.tmLanguage.json
- javascript.tmLanguage.json
- typescript.tmLanguage.json
- php.tmLanguage.json
- shell-unix-bash.tmLanguage.json
- yaml.tmLanguage.json
- json.tmLanguage.json
- xml.tmLanguage.json
- markdown.tmLanguage.json

Also copy at least one theme: `light-plus.json` (VS Code Light+).

NOTE: If network download is not available in the build environment, commit placeholder grammar files and document that real grammars must be added before release. For v1 implementation, use `EmptyLanguage` as fallback and document as known issue.

- [ ] **Step 3: Create TextMateLoader**

```kotlin
object TextMateLoader {
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        // Register grammar files from assets
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(context.assets)
        )
        // Load theme
        val themeSource = IThemeSource.fromInputStream(
            context.assets.open("textmate/themes/light-plus.json"),
            "light-plus.json",
            null,
        )
        ThemeRegistry.getInstance().loadTheme(ThemeModel(themeSource, "light-plus"))
        initialized = true
    }

    fun loadLanguageForFile(filename: String): Language {
        val scope = LanguageDetector.scopeForFile(filename)
        return if (scope != null) {
            TextMateLanguage.create(scope, true)
        } else {
            EmptyLanguage()
        }
    }
}
```

- [ ] **Step 4: Create LanguageDetector**

Map file extensions to TextMate scope names (e.g., `.kt` -> `source.kotlin`, `.py` -> `source.python`).

- [ ] **Step 5: Create SoraEditorView composable**

AndroidView wrapping `CodeEditor`. On factory: create editor, set color scheme to `TextMateColorScheme.create(ThemeRegistry.getInstance())`. On update: set text, set language via TextMateLoader.

Parameters:
- content: String
- language: String (file extension or scope)
- readOnly: Boolean
- onContentChange: (String) -> Unit

- [ ] **Step 6: Commit**

Message: `feat(editor): add Sora-Editor with TextMate grammars and light theme`

---

### Task 6.5: feature-editor -- CodeEditorScreen with Tabs, Search/Replace, SFTP

**Files:**
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/CodeEditorUiState.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/CodeEditorViewModel.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/CodeEditorScreen.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/EditorTabBar.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/ui/SearchReplaceBar.kt`
- Create: `feature-editor/src/main/kotlin/dev/ori/feature/editor/navigation/EditorNavigation.kt`
- Modify: `app/src/main/kotlin/dev/ori/app/navigation/OriDevNavHost.kt`

- [ ] **Step 1: Create UiState with tabs**

```kotlin
data class EditorTab(
    val id: String,
    val filePath: String,
    val filename: String,
    val content: String,
    val originalContent: String,
    val language: String,
    val isRemote: Boolean,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class CodeEditorUiState(
    val tabs: List<EditorTab> = emptyList(),
    val activeTabIndex: Int = 0,
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val isReadOnly: Boolean = false, // controlled by FeatureGateManager
    val savedMessage: String? = null,
)

val EditorTab.isDirty get() = content != originalContent

sealed class CodeEditorEvent {
    data class OpenFile(val path: String, val isRemote: Boolean) : CodeEditorEvent()
    data class CloseTab(val tabId: String) : CodeEditorEvent()
    data class SwitchTab(val index: Int) : CodeEditorEvent()
    data class ContentChanged(val content: String) : CodeEditorEvent()
    data object Save : CodeEditorEvent()
    data object ToggleSearch : CodeEditorEvent()
    data class SetSearchQuery(val query: String) : CodeEditorEvent()
    data class SetReplaceQuery(val query: String) : CodeEditorEvent()
    data object ReplaceAll : CodeEditorEvent()
    data object FindNext : CodeEditorEvent()
    data object ClearSavedMessage : CodeEditorEvent()
}
```

- [ ] **Step 2: Create CodeEditorViewModel**

Injects BOTH `@LocalFileSystem FileSystemRepository` AND `@RemoteFileSystem FileSystemRepository` + `FeatureGateManager`.

Key logic:
- OpenFile: determines which repo based on isRemote flag, loads content, creates EditorTab
- ContentChanged: updates active tab content
- Save: writes via correct repo, updates originalContent, shows "Saved"
- ToggleSearch/FindNext/ReplaceAll: operates on active tab content via regex
- isReadOnly from FeatureGateManager stub (always false in Phase 6a since stub returns Premium=true)

- [ ] **Step 3: Create SearchReplaceBar composable**

Row with: search TextField, match count, find prev/next buttons, replace TextField, replace/replace-all buttons.

- [ ] **Step 4: Create EditorTabBar**

Similar to TerminalTabBar: horizontal scroll, filename with modified dot, close X, indigo active indicator.

- [ ] **Step 5: Create CodeEditorScreen**

Scaffold with OriDevTopBar. Below: EditorTabBar. Below: SearchReplaceBar (when visible). Main: SoraEditorView. Actions in top bar: Save, Search toggle.

- [ ] **Step 6: Create EditorNavigation**

```kotlin
const val EDITOR_ROUTE_BASE = "editor"
const val EDITOR_LOCAL_ROUTE = "$EDITOR_ROUTE_BASE/local/{filePath}"
const val EDITOR_REMOTE_ROUTE = "$EDITOR_ROUTE_BASE/remote/{filePath}"

fun NavController.navigateToEditor(filePath: String, isRemote: Boolean) {
    val encoded = java.net.URLEncoder.encode(filePath, "UTF-8")
    val route = if (isRemote) "$EDITOR_ROUTE_BASE/remote/$encoded" else "$EDITOR_ROUTE_BASE/local/$encoded"
    navigate(route)
}
```

In the screen, decode the path: `URLDecoder.decode(savedStateHandle["filePath"], "UTF-8")`.

- [ ] **Step 7: Update OriDevNavHost**

Register `editorScreen()`. Import from editor navigation.

- [ ] **Step 8: Remove .gitkeep, commit**

Message: `feat(editor): add CodeEditorScreen with tabs, search/replace, and local+remote support`

---

### Task 6.6: feature-terminal -- Session Recording + Send to Claude

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/SendToClaudeSheet.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt`

- [ ] **Step 1: Verify TerminalEvent is a sealed class (not sealed interface)**

Read TerminalUiState.kt. If it's `sealed class TerminalEvent`, the plan's `data object X : TerminalEvent()` syntax is correct. If it's `sealed interface TerminalEvent`, remove the `()` from all new event declarations.

- [ ] **Step 2: Update TerminalUiState**

Add fields: `isRecording`, `activeRecordingId`, `showSendToClaude`, `sendToClaudeInput` (selected text), `claudeResponse`, `claudeLoading`, `claudeError`.

Add events: `StartRecording`, `StopRecording`, `ExportRecording`, `ShowSendToClaude(selectedText)`, `HideSendToClaude`, `SendToClaude(prompt)`, `CopyClaudeResponse`.

- [ ] **Step 3: Update TerminalViewModel**

Inject 4 new use cases: StartSessionRecordingUseCase, StopSessionRecordingUseCase, ExportSessionRecordingUseCase, SendToClaudeUseCase.

In the reader coroutine (the one that feeds bytes to termlib emulator), after processing bytes via `emulator.writeInput()`, ALSO call `sessionRecordingRepository.appendOutput(recordingId, bytes.copyOf(bytesRead))` if recording is active. The `.copyOf(bytesRead)` is CRITICAL to avoid buffer reuse race.

Call `appendOutput` DIRECTLY from the reader coroutine (which already runs on Dispatchers.IO). Do NOT wrap in an additional `launch` -- that would allow interleaved writes from concurrent coroutines and break ordering. The Channel's `send` suspends correctly if the writer is backpressured, which is the desired behavior (terminal backs off when disk is slow).

Ordering guarantee: Channel preserves FIFO order per-sender. Since only one reader coroutine per tab sends, ordering is preserved.

- [ ] **Step 4: Create SendToClaudeSheet**

ModalBottomSheet:
- Header "Ask Claude"
- Context preview (selected terminal text, collapsed)
- Prompt TextField
- "Send" button -> triggers SendToClaude event
- Loading indicator while claudeLoading
- Response area (monospace, scrollable, selectable)
- Copy response button
- "Not set up? Paste API key" link (Phase 9 will have proper settings)

- [ ] **Step 5: Update TerminalScreen**

- Add floating "Send to Claude" pill button (bottom-right, indigo)
- Long-press terminal output for selection (termlib handles this)
- On selection + FAB tap: show SendToClaudeSheet with selected text
- Add recording indicator to TerminalTabBar (red dot)
- Add menu action "Record Session" / "Stop Recording" / "Export Recording"

- [ ] **Step 6: Commit**

Message: `feat(terminal): add session recording and Send to Claude integration`

---

### Task 6.7: feature-filemanager -- File Preview Sheet

**Files:**
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FilePreviewSheet.kt`
- Modify: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileManagerViewModel.kt`
- Modify: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileManagerUiState.kt`
- Modify: `feature-filemanager/build.gradle.kts` (add feature-editor dep for SoraEditorView)

- [ ] **Step 1: FileManagerViewModel loads preview content**

Add `loadPreview(file)` which uses the active pane's FileSystemRepository to read file content (cap at 100KB for perf), set `previewContent` and `previewFile` in uiState.

Add event: `ShowFilePreview(file)`, `ClosePreview`.

When user taps a FILE (not directory), trigger ShowFilePreview instead of current behavior.

- [ ] **Step 2: Create FilePreviewSheet**

ModalBottomSheet with SoraEditorView (read-only) showing content. Button "Open in Editor" navigates to CodeEditorScreen.

Requires dependency: `implementation(project(":feature-editor"))` OR move SoraEditorView to core-ui. Since core-ui is the shared UI module, moving SoraEditorView there is cleaner. Alternative: file-manager can depend on feature-editor since it's not a circular dep.

Simpler: add `implementation(project(":feature-editor"))` to feature-filemanager/build.gradle.kts.

- [ ] **Step 3: Commit**

Message: `feat(filemanager): add file preview sheet with SoraEditorView`

---

### Task 6.8: Tests + Verify + Push + CI Green

**Files:**
- Test: `feature-editor/src/test/kotlin/dev/ori/feature/editor/ui/CodeEditorViewModelTest.kt`
- Test: `feature-editor/src/test/kotlin/dev/ori/feature/editor/ui/LanguageDetectorTest.kt`
- Test: Updates to TerminalViewModelTest for new recording + Claude events
- Test: FileManagerViewModelTest for preview

- [ ] **Step 1: Write all test files**

- CodeEditorViewModelTest: 8 tests (open local, open remote, content changed, save success, save failure, search match, replace all, tab switch)
- LanguageDetectorTest: 15+ extension mappings
- TerminalViewModelTest: add tests for recording lifecycle, send to Claude flow, error handling
- FileManagerViewModelTest: add tests for preview loading

- [ ] **Step 2: Run all checks**

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew detekt
./gradlew test
./gradlew assembleDebug
```

All must pass. Fix any detekt violations (Compose functions, parameter lists, complexity).

- [ ] **Step 3: Commit and push**

Message: `test(editor,terminal,filemanager): add comprehensive tests for Phase 6a`
Then: `git push origin master`

- [ ] **Step 4: Monitor CI until green**

`gh run list --branch master --limit 5` -- wait for Build & Test: success. If failure, read logs, fix, commit, push, repeat.

**Do NOT consider done until CI is green.**

---

## Phase 6a Completion Checklist

- [ ] `core-security`: KeyStoreManager PERSISTS credentials via DataStore + AndroidKeyStore AES/GCM (was in-memory bug)
- [ ] `core-ai`: ClaudeApiService with retry-after, error body parsing, 429/529 backoff, size guard, tests
- [ ] `core-common`: FeatureGateManager stub (Phase 9 compat)
- [ ] `domain`: SessionRecording model, Claude + SessionRecording repositories, 5 use cases, tests
- [ ] `data`: ClaudeRepositoryImpl, SessionRecordingRepositoryImpl (Channel-based writer), tests
- [ ] `feature-editor`: Sora-Editor + TextMate grammars (10+ languages), SoraEditorView, CodeEditorViewModel with tabs, search/replace, local+remote file support, tests
- [ ] `feature-terminal`: session recording wired to reader coroutine (non-blocking), SendToClaudeSheet, recording indicator, tests updated
- [ ] `feature-filemanager`: FilePreviewSheet with SoraEditorView
- [ ] All tests pass, detekt clean, build succeeds, CI GREEN

## Deferred to Phase 6b
- Snippet Manager (spec task 6.8) -- UI + storage
- Auto-Detect Code Blocks in terminal output (spec task 6.9) -- regex + highlight
- Inline Diff Viewer (spec task 6.10) -- side-by-side diff for Claude code suggestions
- Git Status Overlay in file list (spec task 6.12) -- visual layer exists, backend logic needed
- Git Diff Gutter in editor -- Sora-Editor gutter annotations
- Claude streaming responses
