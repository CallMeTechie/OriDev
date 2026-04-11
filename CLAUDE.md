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

## Build

- Debug: `./gradlew assembleDebug`
- Test: `./gradlew test`
- Lint: `./gradlew detekt`
- Wear: `./gradlew :wear:assembleDebug`

## Key Libraries

- SSH/SFTP: SSHJ (com.hierynomus:sshj)
- FTP: Apache Commons Net
- Terminal: Termux terminal-view (Apache 2.0 -- verify license)
- Editor: Sora-Editor (io.github.Rosemoe.sora-editor)
- Proxmox API: OkHttp + Moshi
