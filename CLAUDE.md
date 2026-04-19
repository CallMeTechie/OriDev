# CLAUDE.md -- Ori:Dev

## Project

Ori:Dev (折り Dev) is an SCP/FTP/SSH File Manager & Terminal for Android Foldables (Pixel Fold).
Multi-module Android project: Kotlin, Jetpack Compose, Material 3, Hilt, Room, MVVM + Clean Architecture.

## Architecture

- **Layers:** Presentation (Compose + ViewModel) -> Domain (Use Cases) -> Data (Room + Network)
- **Modules:** app, core/{common,ui,network,security}, domain, data, feature-{connections,filemanager,terminal,transfers,proxmox,editor,settings}, wear
- **Rule:** Feature modules NEVER depend on each other. Communication via Navigation only.
- **Rule:** domain module has NO Android dependencies (pure Kotlin/coroutines).

## Conventions

### Kotlin
- Language: Kotlin only. No Java.
- Style: ktlint + detekt. Run `./gradlew detekt` before committing.
- Coroutines: Use structured concurrency. ViewModels use `viewModelScope`. No `GlobalScope`.
- Nullability: Prefer non-null types. Use `?.let {}` over `if (x != null)`.

### Compose
- State: ViewModels expose `StateFlow<UiState>`. No LiveData.
- Side effects: `LaunchedEffect`, `rememberCoroutineScope`. No lifecycle observers in Compose.
- Theme: Light-first design with Indigo #6366F1 accent color. Always use `MaterialTheme.colorScheme.*`. No hardcoded colors.
- Preview: Add `@Preview` for all screens and major components.

### Architecture
- ViewModels: One per screen. Use `@HiltViewModel`. Expose sealed `UiState` and `UiEvent`.
- Use Cases: Single-purpose. `operator fun invoke()`. Injectable via Hilt.
- Repositories: Interface in `domain/`, implementation in `data/`. Return `Flow<T>` or `Result<T>`.
- Errors: Sealed class `AppError`. Never throw exceptions across layer boundaries.

### Naming
- Packages: `dev.ori.{module}.{layer}` (e.g., `dev.ori.feature.connections.ui`)
- Screens: `{Name}Screen.kt` (e.g., `ConnectionListScreen.kt`)
- ViewModels: `{Name}ViewModel.kt`
- Use Cases: `{Verb}{Noun}UseCase.kt` (e.g., `ConnectUseCase.kt`)
- Repositories: `{Name}Repository.kt` (interface), `{Name}RepositoryImpl.kt` (implementation)
- Room: Entities in `data/model/`, DAOs in `data/dao/`, Database in `data/db/`

### Testing
- Framework: JUnit 5 + MockK + Turbine + Truth
- Naming: `methodUnderTest_condition_expectedResult`
- Pattern: Arrange / Act / Assert
- Room: In-memory database for DAO tests
- ViewModels: Test via Turbine `test {}` block on StateFlow
- Coverage: Every public function in domain/ and data/ must have tests

### Git
- Conventional Commits: feat:, fix:, chore:, docs:, test:, ci:, refactor:
- One logical change per commit
- Branch naming: feature/, fix/, chore/ prefix

### Security
- Credentials: Android Keystore only. Never store passwords in plaintext.
- Passwords in memory: char[] not String. Zero-fill after use.
- Clipboard: Set EXTRA_IS_SENSITIVE flag. Auto-clear after 30s.
- SSH Host Keys: Trust on First Use (TOFU). Reject on mismatch.
- Local file access: Storage Access Framework `DocumentFile` only; no
  `MANAGE_EXTERNAL_STORAGE` and no broad `READ/WRITE_EXTERNAL_STORAGE`.
  The `LocalFileSystemRepository` routes all reads/writes through
  `ContentResolver` + `DocumentFile`. Granted trees persist via
  `takePersistableUriPermission` (Phase 15 Task 15.6) and are surfaced
  plus removable in Settings → Speicherzugriff. The file-manager's
  local pane shows a "Choose a folder" CTA when no trees are granted.

#### Semgrep gate

Security rules live in `.semgrep.yml`. The `semgrep` GitHub Actions workflow
runs them on every PR and push to master. To run locally:

    semgrep --config .semgrep.yml --no-git-ignore --error .

ERROR-severity findings block the PR; WARNING-severity findings are advisory
and surfaced in the PR check log but don't fail CI.

ERROR rules (hard CI gate):
- `oridev-no-material-icons-in-features` — Material Icons imports in any
  feature module or `wear/`. Use Lucide vendored icons from
  `dev.ori.core.ui.icons.lucide.*` instead. Complements the per-diff
  shell gate in `.github/ci/check-forbidden-imports.sh`.
- `oridev-clipboard-missing-sensitive-flag` — direct calls to
  `ClipboardManager.setPrimaryClip(ClipData.newPlainText(...))`. All
  clipboard writes must go through
  `dev.ori.core.security.clipboard.OriClipboard.copy()`, which is the
  single authoritative writer and applies `EXTRA_IS_SENSITIVE` plus
  the auto-clear timer. The `OriClipboard` file itself is the only
  excluded path.
- `oridev-ssh-strict-host-key-required` — no `PromiscuousVerifier()` in
  `core-network` or `data/`. TOFU verification via `KnownHostRepository`.

WARNING rules (advisory):
- `oridev-password-must-be-char-array` — `String` passwords in network,
  security, or data layers. Passwords must be `CharArray` so callers can
  zero-fill them.
- `oridev-no-hardcoded-secrets` — literal `password=`/`token=`/`api_key=`
  values 16+ chars long anywhere in Kotlin sources.

## Build

- Debug: `./gradlew assembleDebug`
- Test: `./gradlew test`
- Lint: `./gradlew detekt`
- Wear: `./gradlew :wear:assembleDebug`

## Key Libraries

- SSH/SFTP: SSHJ (com.hierynomus:sshj)
- FTP: Apache Commons Net
- Terminal: ConnectBot termlib (Apache 2.0, org.connectbot:termlib)
- Editor: Sora-Editor (io.github.Rosemoe.sora-editor)
- Proxmox API: OkHttp + Moshi

## Terminal Keyboard Modes (Phase 14)

The terminal can render its on-screen keyboard in one of three modes,
selected via Settings → Terminal → Keyboard style. The choice is stored
in `KeyboardPreferences` (`:domain`, backed by a standalone `ori_keyboard`
DataStore) and read into `TerminalUiState.keyboardMode`.

- **CUSTOM** *(default for existing installs; recommended for password
  entry)* — the in-app `CustomKeyboard` composable is rendered in the
  bottom slot. Nothing is routed through the system IME, so no dictionary
  learning or cloud sync can leak typed text.
- **HYBRID** — the Android system IME (Gboard, SwiftKey, …) is used for
  text input, plus a sticky `TerminalExtraKeys` row pinned above it
  (Esc/Tab/Ctrl/Alt/arrows/Fn/`|`/`/`/`~`/backtick/Home/End/PgUp/PgDn).
  IME dictionary learning is suppressed by `KeyboardType.Password +
  autoCorrect=false` on the invisible `TerminalImeAnchor`. Switching to
  this mode shows an explicit IME-learning warning dialog.
- **SYSTEM_ONLY** — the system IME alone, no extra-keys row. Power-user
  escape hatch.

A single `TerminalImeAnchor` lives at the top of `KeyboardHost` (not
per tab) so IME focus survives `SwitchTab`. `ResizeTerminal` events
are debounced 200 ms and dropped below a 5-row floor so that emoji-sheet
toggles and IME-layout switches don't spam SSH `window-change` packets.

The Ctrl/Alt modifier state is centralised in `TerminalUiState.ModifierState`
and shared by both `CustomKeyboard` and `TerminalExtraKeys`. Long-press on
a Ctrl/Alt key in the extra-keys row toggles `sticky` so the modifier
persists across multiple keystrokes.
