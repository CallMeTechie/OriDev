# Ori:Dev Phase 2: Connection Manager

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Connection Manager feature: server profile CRUD, SSH/SFTP/FTP connect/disconnect, host key verification (TOFU), and the foundation navigation shell. This is the first user-facing feature.

**Architecture:** feature-connections depends on domain (use cases, repository interfaces) and core modules. data module implements ConnectionRepositoryImpl. App module holds the navigation shell.

**Tech Stack:** Jetpack Compose, Hilt, SSHJ, Room, Jetpack Navigation.

**Depends on:** Phase 1 completed (core modules, domain models, Room DB, Theme).

---

### Task 2.1: core-security -- KeyStoreManager

**Files:**
- Create: `core/core-security/src/main/kotlin/dev/ori/core/security/KeyStoreManager.kt`
- Create: `core/core-security/src/main/kotlin/dev/ori/core/security/di/SecurityModule.kt`

- [ ] **Step 1: Create KeyStoreManager**

Implements `CredentialStore` interface from domain. Uses Android Keystore with AES-256-GCM to encrypt credentials. Stores encrypted bytes in an in-memory cache (will be migrated to EncryptedSharedPreferences in a later task). Passwords are handled as `char[]` and zero-filled after use.

Key points:
- `getOrCreateKey(alias)` generates AES key in AndroidKeyStore if not exists
- `encrypt()` prepends 12-byte GCM IV to ciphertext
- `decrypt()` extracts IV then decrypts
- All methods are `suspend` for Hilt injection compatibility

- [ ] **Step 2: Create SecurityModule (Hilt)**

Binds `KeyStoreManager` to `CredentialStore` interface as Singleton.

- [ ] **Step 3: Commit**

Message: `feat(core-security): add KeyStoreManager with AES-256-GCM encryption`

---

### Task 2.2: core-network -- SSH Client

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClient.kt` (interface)
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClientImpl.kt`
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSession.kt`
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/HostKeyVerifier.kt`
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/model/RemoteFile.kt`
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/di/NetworkModule.kt`
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshClientImplTest.kt`

- [ ] **Step 1: Create SshSession data class**

Fields: sessionId (UUID String), profileId (Long), host, port, connectedAt.

- [ ] **Step 2: Create SshClient interface**

Methods: connect, disconnect, isConnected, listFiles, executeCommand, uploadFile, downloadFile, deleteFile, rename, mkdir, chmod. All suspend functions. connect takes host, port, username, password (CharArray?), privateKey (ByteArray?).

- [ ] **Step 3: Create RemoteFile data class**

Fields: name, path, isDirectory, size, lastModified, permissions, owner.

- [ ] **Step 4: Create HostKeyStore interface in core-network**

IMPORTANT: HostKeyVerifier lives in core-network, but KnownHostDao is in data. core-network CANNOT depend on data (circular dependency). Solution: Define a `HostKeyStore` interface in core-network that abstracts host key persistence. The data module provides the implementation backed by KnownHostDao.

Create `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/HostKeyStore.kt`:
```kotlin
interface HostKeyStore {
    suspend fun findHost(host: String, port: Int): StoredHostKey?
    suspend fun updateLastSeen(host: String, port: Int)
}
data class StoredHostKey(val fingerprint: String, val keyType: String)
```

- [ ] **Step 5: Create OriDevHostKeyVerifier**

Implements SSHJ's `HostKeyVerifier`. Injects `HostKeyStore` (NOT KnownHostDao directly):
- Unknown host (findHost returns null): throws `AppError.HostKeyUnknown` (UI shows TOFU dialog)
- Known + matching fingerprint: calls updateLastSeen, returns true
- Known + mismatching fingerprint: throws `AppError.HostKeyMismatch` (possible MITM)

Fingerprint: SHA-256 of public key, Base64 encoded.

- [ ] **Step 6: Create SshClientImpl**

Wraps SSHJ `SSHClient`. Manages sessions in `ConcurrentHashMap<String, SSHClient>`. Connect creates client, adds host key verifier, authenticates (password or public key), configures keepalive (15s interval). SFTP operations use `client.newSFTPClient()` in try/finally.

- [ ] **Step 7: Create NetworkModule (Hilt)**

Binds `SshClientImpl` to `SshClient` as Singleton. Note: `HostKeyStore` binding is provided by data module's RepositoryModule (see Task 2.4).

- [ ] **Step 7: Write unit tests for SshSession and CommandResult**

- [ ] **Step 8: Run tests and commit**

Message: `feat(core-network): add SshClient with SSHJ, TOFU host key verification`

---

### Task 2.3: domain -- Connection Use Cases

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/GetConnectionsUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/GetFavoriteConnectionsUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/SaveProfileUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/DeleteProfileUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/ConnectUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/DisconnectUseCase.kt`
- Test: `domain/src/test/kotlin/dev/ori/domain/usecase/ConnectUseCaseTest.kt`
- Test: `domain/src/test/kotlin/dev/ori/domain/usecase/SaveProfileUseCaseTest.kt`

- [ ] **Step 1: Create GetConnectionsUseCase**

Simple passthrough: `operator fun invoke(): Flow<List<ServerProfile>> = repository.getAllProfiles()`

- [ ] **Step 2: Create GetFavoriteConnectionsUseCase**

Same pattern, calls `repository.getFavoriteProfiles()`.

- [ ] **Step 3: Create SaveProfileUseCase with validation**

Validates: name not blank, host is valid (uses `isValidHost()` extension), port 1-65535. If `profile.id == 0L` calls `saveProfile`, else `updateProfile`. Returns `AppResult<Long>`.

- [ ] **Step 4: Create DeleteProfileUseCase**

Disconnects first (if connected), then deletes profile.

- [ ] **Step 5: Create ConnectUseCase**

Wraps `repository.connect()` in try/catch. Checks if exception wraps an `AppErrorException` and preserves typed errors: `HostKeyUnknown` and `HostKeyMismatch` are re-thrown as-is (UI needs to distinguish TOFU dialog vs MITM warning). Auth errors -> `AuthenticationError`. Other exceptions -> `NetworkError`. Returns `AppResult<Connection>`.

- [ ] **Step 6: Create DisconnectUseCase**

Simple passthrough to `repository.disconnect(profileId)`.

- [ ] **Step 7: Write ConnectUseCaseTest**

Tests: success returns Connection, auth failure returns AuthenticationError, network failure returns NetworkError. Uses MockK for repository.

- [ ] **Step 8: Write SaveProfileUseCaseTest**

Tests: valid profile returns id, blank name returns error, invalid host returns error, invalid port returns error, existing profile (id > 0) calls update.

- [ ] **Step 9: Run tests and commit**

Run: `./gradlew :domain:test`
Message: `feat(domain): add connection use cases with validation and tests`

---

### Task 2.4: data -- ConnectionRepositoryImpl

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/repository/ConnectionRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/repository/KnownHostRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/di/RepositoryModule.kt`

- [ ] **Step 1: Create ConnectionRepositoryImpl**

Implements `ConnectionRepository`. Uses `ServerProfileDao` for CRUD, `SshClient` for connections, `CredentialStore` for credentials. Tracks active sessions in `ConcurrentHashMap<Long, SshSession>`. Exposes active connections via `MutableStateFlow`. Connect flow: load profile from DB, get credentials from KeyStore, call sshClient.connect(), zero-fill password, update lastConnected timestamp.

- [ ] **Step 2: Create KnownHostRepositoryImpl**

Implements `KnownHostRepository`. Thin wrapper around `KnownHostDao` with entity-to-domain mapping.

- [ ] **Step 3: Create HostKeyStoreImpl in data module**

Implements `HostKeyStore` from core-network, backed by `KnownHostDao`. This bridges the module boundary: core-network defines the interface, data provides the Room-backed implementation.

- [ ] **Step 4: Create RepositoryModule (Hilt)**

Binds: ConnectionRepositoryImpl -> ConnectionRepository, KnownHostRepositoryImpl -> KnownHostRepository, HostKeyStoreImpl -> HostKeyStore. All as Singletons.

- [ ] **Step 5: Commit**

Message: `feat(data): add ConnectionRepositoryImpl, KnownHostRepositoryImpl, and HostKeyStoreImpl`

---

### Task 2.5: feature-connections -- UI

**Files:**
- Create: `feature-connections/src/main/kotlin/dev/ori/feature/connections/ui/ConnectionListScreen.kt`
- Create: `feature-connections/src/main/kotlin/dev/ori/feature/connections/ui/ConnectionListViewModel.kt`
- Create: `feature-connections/src/main/kotlin/dev/ori/feature/connections/ui/ConnectionListUiState.kt`
- Create: `feature-connections/src/main/kotlin/dev/ori/feature/connections/navigation/ConnectionsNavigation.kt`

- [ ] **Step 1: Create ConnectionListUiState**

Data class with: profiles (List), favorites (List), activeConnections (List), isLoading, error (String?), searchQuery. Sealed class `ConnectionListEvent` with: Connect, Disconnect, Delete, ToggleFavorite, Search, ClearError.

- [ ] **Step 2: Create ConnectionListViewModel**

`@HiltViewModel`. Injects all 6 use cases. Init block combines getConnections() and getFavorites() flows. Handles events via `onEvent()` method. Connect/disconnect launch coroutines, errors are caught and set in uiState.

- [ ] **Step 3: Create ConnectionListScreen**

Reference mockup: `Mockups/connection-manager.html`. Scaffold with OriDevTopBar, FAB for add, LazyColumn of ServerProfileCards. Each card shows: StatusDot, name, host:port, ProtocolBadge, favorite star. Cards are clickable (connected -> open terminal, disconnected -> connect). Search filters list by name/host.

- [ ] **Step 4: Create ConnectionsNavigation**

Route constant `CONNECTIONS_ROUTE = "connections"`. NavGraphBuilder extension `connectionsScreen()` with navigation callbacks. NavController extension `navigateToConnections()`.

**Note:** `AddEditConnectionScreen` is deferred to a follow-up task. The FAB's `onNavigateToAdd` callback should be a no-op or show a "Coming soon" toast for now.

**Note:** `ToggleFavorite` event in the ViewModel calls `SaveProfileUseCase` directly with `profile.copy(isFavorite = !profile.isFavorite)`. No separate use case needed.

- [ ] **Step 5: Ensure feature-connections build.gradle.kts includes navigation-compose**

The module needs `implementation(libs.navigation.compose)` for `NavGraphBuilder`, `composable()`, `NavController`. Add this if not already present from Phase 0.

- [ ] **Step 6: Commit**

Message: `feat(connections): add ConnectionListScreen, ViewModel, and navigation`

---

### Task 2.6: app -- Navigation Shell

**Files:**
- Create: `app/src/main/kotlin/dev/ori/app/ui/OriDevApp.kt`
- Create: `app/src/main/kotlin/dev/ori/app/navigation/OriDevNavHost.kt`
- Modify: `app/src/main/kotlin/dev/ori/app/MainActivity.kt`

- [ ] **Step 1: Create OriDevApp**

Composable with OriDevTheme, NavController, Scaffold with NavigationBar. 5 bottom nav items: Connections (Wifi icon), Files (Folder), Terminal (Terminal), Transfers (SwapVert), Settings (Settings). Navigation uses popUpTo with saveState/restoreState pattern.

- [ ] **Step 2: Create OriDevNavHost**

NavHost with startDestination = CONNECTIONS_ROUTE. Registers connectionsScreen(). Adds placeholder composables for filemanager, terminal, transfers, settings routes.

- [ ] **Step 3: Update MainActivity**

Enable edge-to-edge, call OriDevApp() in setContent.

- [ ] **Step 4: Commit**

Message: `feat(app): add navigation shell with bottom bar and ConnectionListScreen`

---

### Task 2.7: ViewModel Tests

**Files:**
- Test: `feature-connections/src/test/kotlin/dev/ori/feature/connections/ui/ConnectionListViewModelTest.kt`

- [ ] **Step 1: Write ViewModel test**

Tests using Turbine:
- `init_loadsProfiles_emitsLoadedState`: Mock getConnections returns flow of profiles, verify uiState transitions from loading to loaded.
- `onConnect_success_updatesActiveConnections`: Mock connect returns success, verify connection appears.
- `onConnect_failure_setsError`: Mock connect throws, verify error message in uiState.
- `onDelete_removesProfile`: Mock delete, verify it's called.
- `onSearch_filtersProfiles`: Set search query, verify filtered list.

- [ ] **Step 2: Run tests**

Run: `./gradlew :feature-connections:test`
Expected: PASS

- [ ] **Step 3: Commit**

Message: `test(connections): add ConnectionListViewModel tests with Turbine`

---

### Task 2.8: Verify Phase 2

- [ ] **Step 1: Run all tests**

Run: `./gradlew test`
Expected: All PASS

- [ ] **Step 2: Compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run detekt**

Run: `./gradlew detekt`
Expected: Clean

- [ ] **Step 4: Fix and commit if needed**

Message: `chore: resolve Phase 2 build and lint issues`

---

## Phase 2 Completion Checklist

- [ ] `core-security`: KeyStoreManager with AES-256-GCM, Hilt binding
- [ ] `core-network`: SshClient + SshClientImpl (SSHJ), HostKeyVerifier (TOFU), Hilt binding
- [ ] `domain`: 6 use cases tested
- [ ] `data`: ConnectionRepositoryImpl, KnownHostRepositoryImpl, Hilt bindings
- [ ] `feature-connections`: ConnectionListScreen, ViewModel (tested), Navigation
- [ ] `app`: OriDevApp with Bottom Navigation, NavHost, updated MainActivity
- [ ] All tests pass, project compiles, detekt clean
