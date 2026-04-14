# Ori:Dev Phase 4: SSH Terminal (v3 -- post-review fixes)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fully functional SSH Terminal with interactive PTY shell sessions, multi-tab support, custom soft keyboard, landscape/portrait layout, copy & paste with multiline confirmation, clipboard history, snippet manager, terminal preferences, foldable awareness, and ConnectionService integration for background persistence.

**Architecture:** Terminal rendering and VT100/xterm emulation handled by **ConnectBot termlib** (Apache 2.0, libvterm backend, native Compose component). core-network provides interactive shell sessions via SSHJ. Shell sessions are managed by ConnectionService (foreground service) so they persist when the app goes to background. feature-terminal contains the UI layer only.

**Tech Stack:** ConnectBot termlib (org.connectbot:termlib, Apache 2.0, libvterm), SSHJ (PTY shell channel), Compose, Hilt, Room (CommandSnippet, SessionLog), ConnectionService (foreground service).

**Depends on:** Phase 2 completed (ConnectionRepository, SshClient, core modules).

**Previous plan rejected because:** Custom Canvas-based terminal emulator was too simplistic (no alternate screen, no scroll regions, no UTF-8 multi-byte, not thread-safe). Reviews found 8 critical issues. ConnectBot termlib solves all of them via libvterm.

---

## File Structure

```
core/core-network/src/main/kotlin/dev/ori/core/network/ssh/
├── SshShellSession.kt
├── SshShellManager.kt
├── ShellHandle.kt

domain/src/main/kotlin/dev/ori/domain/
├── model/
│   ├── TerminalTab.kt
│   └── CommandSnippet.kt
├── repository/
│   └── SnippetRepository.kt
├── usecase/
│   └── GetSnippetsUseCase.kt

data/src/main/kotlin/dev/ori/data/
├── repository/
│   └── SnippetRepositoryImpl.kt
├── di/
│   └── TerminalModule.kt

app/src/main/kotlin/dev/ori/app/
├── service/
│   └── ConnectionService.kt

feature-terminal/src/main/kotlin/dev/ori/feature/terminal/
├── ui/
│   ├── TerminalScreen.kt
│   ├── TerminalViewModel.kt
│   ├── TerminalUiState.kt
│   ├── CustomKeyboard.kt
│   ├── TerminalTabBar.kt
│   ├── ClipboardHistory.kt
│   ├── SnippetSheet.kt
│   └── TerminalPreferencesSheet.kt
├── navigation/
│   └── TerminalNavigation.kt
```

---

### Task 4.1: core-network -- Interactive Shell Session with PTY

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/ShellHandle.kt`
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshShellSession.kt`
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshShellManager.kt`
- Modify: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClient.kt` (add openShell method)
- Modify: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClientImpl.kt` (implement openShell)
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/ShellHandleTest.kt`

- [ ] **Step 1: Create ShellHandle**

```kotlin
package dev.ori.core.network.ssh

import java.io.InputStream
import java.io.OutputStream

data class ShellHandle(
    val shellId: String,
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val onResize: (cols: Int, rows: Int) -> Unit,
    val onClose: () -> Unit,
)
```

- [ ] **Step 2: Create SshShellSession**

Wraps SSHJ Session.Shell with PTY. Key points:
- Constructor takes SSHJ `Session` and `Session.Shell`
- `write(data: ByteArray)` sends to shell's outputStream
- `resize(cols, rows)` calls `shell.changeWindowDimensions(cols, rows, 0, 0)` -- THIS IS THE CORRECT SSHJ API (review found previous plan used a no-op)
- `close()` is idempotent via `AtomicBoolean`
- `isOpen` tracks state, also checks `shell.isOpen`

```kotlin
package dev.ori.core.network.ssh

import net.schmizz.sshj.connection.channel.direct.Session
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class SshShellSession(
    private val session: Session,
    private val shell: Session.Shell,
) {
    val inputStream: InputStream get() = shell.inputStream
    val outputStream: OutputStream get() = shell.outputStream

    private val closed = AtomicBoolean(false)

    val isOpen: Boolean get() = !closed.get() && shell.isOpen

    fun write(data: ByteArray) {
        if (isOpen) {
            runCatching {
                outputStream.write(data)
                outputStream.flush()
            }.onFailure { close() } // Connection dropped
        }
    }

    fun resize(cols: Int, rows: Int) {
        if (isOpen) {
            runCatching {
                shell.changeWindowDimensions(cols, rows, 0, 0)
            } // Silently ignore resize failures (non-fatal)
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { shell.close() }
            runCatching { session.close() }
        }
    }
}
```

- [ ] **Step 3: Create SshShellManager**

```kotlin
package dev.ori.core.network.ssh

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshShellManager @Inject constructor() {

    private val shells = ConcurrentHashMap<String, SshShellSession>()

    fun openShell(
        client: net.schmizz.sshj.SSHClient,
        cols: Int = 80,
        rows: Int = 24,
        term: String = "xterm-256color",
    ): ShellHandle {
        val session = client.startSession()
        session.allocatePTY(term, cols, rows, 0, 0, emptyMap())
        val shell = session.startShell()
        val shellSession = SshShellSession(session, shell)
        val shellId = UUID.randomUUID().toString()
        shells[shellId] = shellSession

        return ShellHandle(
            shellId = shellId,
            inputStream = shellSession.inputStream,
            outputStream = shellSession.outputStream,
            onResize = { c, r -> shellSession.resize(c, r) },
            onClose = { closeShell(shellId) },
        )
    }

    fun getSession(shellId: String): SshShellSession? = shells[shellId]

    fun closeShell(shellId: String) {
        shells.remove(shellId)?.close()
    }

    fun closeAllShells() {
        shells.keys.toList().forEach { closeShell(it) }
    }

    fun isShellOpen(shellId: String): Boolean =
        shells[shellId]?.isOpen == true
}
```

- [ ] **Step 4: Add openShell to SshClient interface**

Add to `SshClient.kt`:
```kotlin
suspend fun openShell(
    sessionId: String,
    cols: Int = 80,
    rows: Int = 24,
): ShellHandle
```

Implement in `SshClientImpl.kt`:
```kotlin
override suspend fun openShell(
    sessionId: String,
    cols: Int,
    rows: Int,
): ShellHandle = withContext(Dispatchers.IO) {
    val client = getClient(sessionId)
    shellManager.openShell(client, cols, rows)
}
```

Add `private val shellManager: SshShellManager` to `SshClientImpl` constructor (injected via Hilt).

NOTE: `getClient()` is currently `private` in `SshClientImpl`. For `openShell` to work, either:
- Make `getClient()` internal/public, or  
- Better: keep it private and have `openShell` call it internally (since `openShell` is implemented inside `SshClientImpl`)

The implementation inside `SshClientImpl` can call its own private `getClient()` directly -- no visibility change needed.

ALSO: Add `implementation(project(":core:core-network"))` to `app/build.gradle.kts` because `ConnectionService` (Task 4.4) needs `SshShellManager`. This is the only feature-adjacent module that needs direct core-network access.

- [ ] **Step 5: Write ShellHandle test**

```kotlin
package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ShellHandleTest {

    @Test
    fun shellHandle_holdsStreams() {
        val input = ByteArrayInputStream(byteArrayOf())
        val output = ByteArrayOutputStream()
        val handle = ShellHandle(
            shellId = "test-id",
            inputStream = input,
            outputStream = output,
            onResize = { _, _ -> },
            onClose = {},
        )
        assertThat(handle.shellId).isEqualTo("test-id")
        assertThat(handle.inputStream).isSameInstanceAs(input)
        assertThat(handle.outputStream).isSameInstanceAs(output)
    }

    @Test
    fun shellHandle_onClose_callsCallback() {
        var closed = false
        val handle = ShellHandle(
            shellId = "id",
            inputStream = ByteArrayInputStream(byteArrayOf()),
            outputStream = ByteArrayOutputStream(),
            onResize = { _, _ -> },
            onClose = { closed = true },
        )
        handle.onClose()
        assertThat(closed).isTrue()
    }
}
```

- [ ] **Step 6: Run tests and commit**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew :core:core-network:test`
Message: `feat(core-network): add interactive SSH shell session with PTY and resize support`

---

### Task 4.2: domain -- Terminal Models, SnippetRepository, UseCase

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/model/TerminalTab.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/model/CommandSnippet.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/SnippetRepository.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/GetSnippetsUseCase.kt`

- [ ] **Step 1: Create domain models**

```kotlin
// TerminalTab.kt
package dev.ori.domain.model

data class TerminalTab(
    val id: String,
    val profileId: Long,
    val serverName: String,
    val shellId: String? = null,
    val isConnected: Boolean = false,
)
```

```kotlin
// CommandSnippet.kt -- includes ALL fields from entity (review found missing fields)
package dev.ori.domain.model

data class CommandSnippet(
    val id: Long = 0,
    val serverProfileId: Long?,
    val name: String,
    val command: String,
    val category: String,
    val isWatchQuickCommand: Boolean = false,
    val sortOrder: Int = 0,
)
```

- [ ] **Step 2: Create SnippetRepository**

```kotlin
package dev.ori.domain.repository

import dev.ori.domain.model.CommandSnippet
import kotlinx.coroutines.flow.Flow

interface SnippetRepository {
    fun getSnippetsForServer(serverId: Long?): Flow<List<CommandSnippet>>
    suspend fun addSnippet(snippet: CommandSnippet): Long
    suspend fun updateSnippet(snippet: CommandSnippet)
    suspend fun deleteSnippet(snippet: CommandSnippet)
}
```

- [ ] **Step 3: Create GetSnippetsUseCase**

```kotlin
package dev.ori.domain.usecase

import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.repository.SnippetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSnippetsUseCase @Inject constructor(
    private val repository: SnippetRepository,
) {
    operator fun invoke(serverId: Long?): Flow<List<CommandSnippet>> =
        repository.getSnippetsForServer(serverId)
}
```

- [ ] **Step 4: Commit**

Message: `feat(domain): add TerminalTab, CommandSnippet models and SnippetRepository`

---

### Task 4.3: data -- SnippetRepository Implementation + Hilt Module

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/repository/SnippetRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/di/TerminalModule.kt`

- [ ] **Step 1: Create SnippetRepositoryImpl**

@Singleton. Wraps CommandSnippetDao. Maps ALL fields including `isWatchQuickCommand` and `sortOrder` (review found these were dropped in v1).

```kotlin
package dev.ori.data.repository

import dev.ori.data.dao.CommandSnippetDao
import dev.ori.data.entity.CommandSnippetEntity
import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.repository.SnippetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepositoryImpl @Inject constructor(
    private val dao: CommandSnippetDao,
) : SnippetRepository {

    override fun getSnippetsForServer(serverId: Long?): Flow<List<CommandSnippet>> =
        dao.getForServer(serverId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun addSnippet(snippet: CommandSnippet): Long =
        dao.insert(snippet.toEntity())

    override suspend fun updateSnippet(snippet: CommandSnippet) =
        dao.update(snippet.toEntity())

    override suspend fun deleteSnippet(snippet: CommandSnippet) =
        dao.delete(snippet.toEntity())

    private fun CommandSnippetEntity.toDomain() = CommandSnippet(
        id = id,
        serverProfileId = serverProfileId,
        name = name,
        command = command,
        category = category,
        isWatchQuickCommand = isWatchQuickCommand,
        sortOrder = sortOrder,
    )

    private fun CommandSnippet.toEntity() = CommandSnippetEntity(
        id = id,
        serverProfileId = serverProfileId,
        name = name,
        command = command,
        category = category,
        isWatchQuickCommand = isWatchQuickCommand,
        sortOrder = sortOrder,
    )
}
```

- [ ] **Step 2: Create TerminalModule**

```kotlin
package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.repository.SnippetRepositoryImpl
import dev.ori.domain.repository.SnippetRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TerminalModule {

    @Binds
    @Singleton
    abstract fun bindSnippetRepository(impl: SnippetRepositoryImpl): SnippetRepository
}
```

- [ ] **Step 3: Commit**

Message: `feat(data): add SnippetRepository implementation and TerminalModule`

---

### Task 4.4: app -- ConnectionService (Foreground Service)

**Files:**
- Create: `app/src/main/kotlin/dev/ori/app/service/ConnectionService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add service declaration)

This is CRITICAL for terminal usability -- without it, Android kills shell sessions on app background.

- [ ] **Step 1: Create ConnectionService**

A foreground service that:
- Holds references to active `SshShellManager` (injected via Hilt)
- Shows persistent notification: "Ori:Dev - N active sessions"
- Updates notification when sessions open/close
- Foreground service type: `connectedDevice|dataSync`
- Started when first terminal tab opens, stopped when last closes
- Binder pattern so TerminalViewModel can communicate with the service

```kotlin
package dev.ori.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.ori.core.network.ssh.SshShellManager
import javax.inject.Inject

@AndroidEntryPoint
class ConnectionService : Service() {

    @Inject lateinit var shellManager: SshShellManager

    private val binder = LocalBinder()
    private var sessionCount = 0

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
        fun getShellManager(): SshShellManager = shellManager
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        return START_STICKY
    }

    fun updateSessionCount(count: Int) {
        sessionCount = count
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
        if (count == 0) {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Connections",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows active SSH connections"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ori:Dev")
            .setContentText(
                if (sessionCount > 0) "$sessionCount active session${if (sessionCount > 1) "s" else ""}"
                else "Connected",
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: replace with app icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        shellManager.closeAllShells()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "oridev_connections"
        const val NOTIFICATION_ID = 1001
    }
}
```

- [ ] **Step 2: Add service to AndroidManifest.xml**

Add inside `<application>`:
```xml
<service
    android:name=".service.ConnectionService"
    android:foregroundServiceType="connectedDevice|dataSync"
    android:exported="false" />
```

- [ ] **Step 3: Commit**

Also add `implementation(project(":core:core-network"))` to `app/build.gradle.kts` if not already present.

Also update `CLAUDE.md` Key Libraries section: change "Terminal: Termux terminal-view (Apache 2.0)" to "Terminal: ConnectBot termlib (Apache 2.0, libvterm)" -- or whatever library the verification in Task 4.5 confirms.

Message: `feat(app): add ConnectionService foreground service for persistent shell sessions`

---

### Task 4.5: feature-terminal -- Add Dependencies + UiState

**Files:**
- Modify: `feature-terminal/build.gradle.kts`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt`

- [ ] **Step 1: Update feature-terminal/build.gradle.kts**

Add these dependencies:
```kotlin
implementation(project(":core:core-network"))  // for SshClient, ShellHandle
implementation(libs.compose.material.icons.extended)  // for extended icons
implementation(libs.window)  // for foldable detection
// ConnectBot termlib -- add to version catalog first:
// In libs.versions.toml [versions]: termlib = "0.0.23"
// In libs.versions.toml [libraries]: termlib = { module = "org.connectbot:termlib", version.ref = "termlib" }
implementation(libs.termlib)
```

Also add `useJUnitPlatform()` to test task:
```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
}
```

Also update `gradle/libs.versions.toml` to add termlib.

CRITICAL VERIFICATION: Before implementing, the subagent MUST verify that `org.connectbot:termlib:0.0.23` resolves from Maven Central. Run:
```bash
curl -s "https://repo.maven.apache.org/maven2/org/connectbot/termlib/maven-metadata.xml" | head -20
```
If it does NOT resolve, fall back to: use Termux `terminal-emulator` + `terminal-view` (Apache 2.0 modules, import as local modules from GitHub) and wrap the Android View in `AndroidView` for Compose. Update CLAUDE.md accordingly.

- [ ] **Step 2: Create TerminalUiState**

```kotlin
package dev.ori.feature.terminal.ui

import dev.ori.domain.model.CommandSnippet

data class TerminalTabState(
    val id: String,
    val profileId: Long,
    val serverName: String,
    val isConnected: Boolean = false,
    val shellId: String? = null,
    val outputVersion: Long = 0,  // Incremented on each output chunk to trigger recomposition
)

data class TerminalUiState(
    val tabs: List<TerminalTabState> = emptyList(),
    val activeTabIndex: Int = 0,
    val isKeyboardVisible: Boolean = true,
    val splitRatio: Float = 0.6f,
    val clipboardHistory: List<String> = emptyList(),
    val showSnippets: Boolean = false,
    val snippets: List<CommandSnippet> = emptyList(),
    val showPasteConfirmation: String? = null,
    val showPreferences: Boolean = false,
    val terminalFontSize: Float = 14f,
    val error: String? = null,
)

sealed class TerminalEvent {
    data class CreateTab(val profileId: Long, val serverName: String) : TerminalEvent()
    data class CloseTab(val tabId: String) : TerminalEvent()
    data class SwitchTab(val index: Int) : TerminalEvent()
    data class SendInput(val data: ByteArray) : TerminalEvent() {
        override fun equals(other: Any?) = other is SendInput && data.contentEquals(other.data)
        override fun hashCode() = data.contentHashCode()
    }
    data class SendText(val text: String) : TerminalEvent()
    data class Paste(val text: String) : TerminalEvent()
    data object ConfirmPaste : TerminalEvent()
    data object CancelPaste : TerminalEvent()
    data class CopyToClipboard(val text: String) : TerminalEvent()
    data object ToggleKeyboard : TerminalEvent()
    data class UpdateSplitRatio(val ratio: Float) : TerminalEvent()
    data object ToggleSnippets : TerminalEvent()
    data class ExecuteSnippet(val command: String) : TerminalEvent()
    data object TogglePreferences : TerminalEvent()
    data class SetFontSize(val size: Float) : TerminalEvent()
    data class ResizeTerminal(val cols: Int, val rows: Int) : TerminalEvent()
    data object ClearError : TerminalEvent()
}
```

Note: `outputVersion: Long` in `TerminalTabState` solves the StateFlow recomposition issue found in review -- it is incremented every time the reader coroutine receives output, causing StateFlow to emit a new value.

- [ ] **Step 3: Remove .gitkeep from feature-terminal**

- [ ] **Step 4: Commit**

Message: `feat(terminal): add dependencies, termlib, and TerminalUiState`

---

### Task 4.6: feature-terminal -- TerminalViewModel

**Files:**
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`

- [ ] **Step 1: Create TerminalViewModel**

@HiltViewModel. Key design:

**Injections:**
- `SshClient` (for openShell)
- `ConnectionRepository` (for getProfileById to get server name)
- `GetSnippetsUseCase`
- `Application` context (for starting ConnectionService)

**Shell session lifecycle via ConnectionService:**
- On `CreateTab`: start ConnectionService, bind to it, open shell via SshClient.openShell()
- Shell handle's inputStream is read in a coroutine on `Dispatchers.IO`
- Reader coroutine reads 4KB chunks, feeds bytes to termlib's terminal instance, increments `outputVersion`
- On IOException/EOF in reader: set tab as disconnected, notify UI via snackbar "Connection to {server} lost"
- On `CloseTab`: close shell handle, if no tabs left stop ConnectionService
- Disconnected tabs show a "Reconnect" button in the UI. Tapping it calls SshClient.connect() then openShell() to establish a new session.
- On process death + service restart: service detects 0 shells and calls stopSelf() immediately (no zombie service)

**termlib integration:**
- Each tab gets a termlib `Terminal` instance (from org.connectbot:termlib)
- The ViewModel holds `Map<String, Terminal>` mapping tabId to Terminal
- The UI renders the Terminal directly via termlib's Compose component
- Resize events forwarded to both termlib Terminal and shell handle's `onResize`

**Clipboard:**
- `paste()`: if text contains newlines, set `showPasteConfirmation`
- `confirmPaste()`: send text to shell
- `copyToClipboard()`: add to history (max 10), set `ClipDescription.EXTRA_IS_SENSITIVE`

**Snippets:**
- Load on init from GetSnippetsUseCase
- `executeSnippet()`: send command + newline to shell

- [ ] **Step 2: Commit**

Message: `feat(terminal): add TerminalViewModel with shell lifecycle and termlib integration`

---

### Task 4.7: feature-terminal -- Custom Keyboard

**Files:**
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/CustomKeyboard.kt`

- [ ] **Step 1: Create CustomKeyboard**

Light-themed keyboard with 6 rows (compact but complete):

**Row 1 (Function -- toggleable):** Esc | F1-F12 (horizontal scroll) | Home | End | PgUp | PgDn | Ins | Del
**Row 2 (Numbers + Symbols):** 1 2 3 4 5 6 7 8 9 0 - = 
**Row 3 (QWERTY top):** Tab | Q W E R T Y U I O P | [ ] 
**Row 4 (QWERTY mid):** Ctrl | A S D F G H J K L | ; ' | Enter
**Row 5 (QWERTY bottom):** Shift | Z X C V B N M , . | / \ | ↑ | Backspace
**Row 6 (Bottom):** Alt | ~ | ` | | | Space (WIDE -- takes 50% of row) | ← | ↓ | →

CRITICAL: Space bar is a REAL KEY (50% of Row 6 width). Not a gesture.

Key behavior:
- Ctrl/Alt/Shift are STICKY toggles (tap to activate, tap again to deactivate, auto-deactivate after next key)
- Ctrl+C sends byte 0x03, Ctrl+D sends 0x04, Ctrl+Z sends 0x1A
- Arrow keys send ESC[A/B/C/D
- F1-F12 send ESC OP through ESC [24~
- Long-press on arrow keys triggers KEY REPEAT (200ms delay, 50ms repeat)
- Backspace sends 0x7F (DEL -- this is what modern terminals expect for Backspace)
- Delete key sends ESC[3~ (xterm Delete sequence)

Design: Light background (#F3F4F6), white key pills with subtle #E5E7EB border, 8dp radius. Active modifier keys get indigo background. Each key minimum 44dp touch target.

- [ ] **Step 2: Commit**

Message: `feat(terminal): add CustomKeyboard with sticky modifiers and key repeat`

---

### Task 4.8: feature-terminal -- UI Components

**Files:**
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalTabBar.kt`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/ClipboardHistory.kt`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/SnippetSheet.kt`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalPreferencesSheet.kt`

- [ ] **Step 1: Create TerminalTabBar**

Horizontal scrollable row. Each tab: status dot (green/red), server name, close X. Active tab has indigo bottom border. Plus (+) button at end. Light chrome (#FFFFFF background, #E5E7EB border).

- [ ] **Step 2: Create ClipboardHistory**

DropdownMenu showing last 10 clipboard entries. Each is a truncated preview, tappable to paste.

- [ ] **Step 3: Create SnippetSheet**

ModalBottomSheet listing command snippets grouped by category. Each shows: name (bold), command (monospace preview). Tappable to execute (sends command + newline).

- [ ] **Step 4: Create TerminalPreferencesSheet**

ModalBottomSheet with terminal settings:
- Font size: slider (10-24sp)
- Terminal type: xterm-256color (fixed, informational)
- Bell mode: Vibrate / None (radio)
- Show keyboard on focus: toggle

These are stored in the ViewModel state (persisted via EncryptedPrefs in later phase).

- [ ] **Step 5: Commit**

Message: `feat(terminal): add TerminalTabBar, ClipboardHistory, SnippetSheet, PreferencesSheet`

---

### Task 4.9: feature-terminal -- TerminalScreen with Foldable Awareness

**Files:**
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/navigation/TerminalNavigation.kt`
- Modify: `app/src/main/kotlin/dev/ori/app/navigation/OriDevNavHost.kt`
- Modify: `app/src/main/kotlin/dev/ori/app/ui/OriDevApp.kt`

- [ ] **Step 1: Create TerminalScreen**

Layout uses `LocalConfiguration.current.screenWidthDp` for fold detection (same pattern as FileManagerScreen):

**Landscape/Unfolded (>=600dp):**
- TerminalTabBar (light, top)
- Terminal content area (60%): termlib Compose component with dark background
- Draggable divider
- CustomKeyboard (40%, light background)

**Portrait/Folded (<600dp):**
- TerminalTabBar (light, top)
- Terminal content area (fullscreen): termlib Compose component
- CustomKeyboard as bottom sheet (slides up when keyboard toggle is on)

Additional overlays:
- Paste confirmation AlertDialog (when multiline paste attempted)
- SnippetSheet
- ClipboardHistory dropdown
- TerminalPreferencesSheet
- Floating "Send to Claude" pill button (bottom-right, deferred functionality -- shows toast "Coming in Phase 6")
- Snackbar for errors

- [ ] **Step 2: Create TerminalNavigation**

```kotlin
const val TERMINAL_ROUTE = "terminal"
const val TERMINAL_WITH_PROFILE_ROUTE = "terminal/{profileId}"

fun NavGraphBuilder.terminalScreen() {
    composable(route = TERMINAL_ROUTE) { TerminalScreen() }
    composable(
        route = TERMINAL_WITH_PROFILE_ROUTE,
        arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val profileId = backStackEntry.arguments?.getLong("profileId") ?: return@composable
        TerminalScreen(initialProfileId = profileId)
    }
}

fun NavController.navigateToTerminal(profileId: Long? = null, navOptions: NavOptions? = null) {
    val route = if (profileId != null) "terminal/$profileId" else TERMINAL_ROUTE
    navigate(route, navOptions)
}
```

- [ ] **Step 3: Update OriDevNavHost**

Replace terminal placeholder with `terminalScreen()`. Import from feature-terminal navigation.

- [ ] **Step 4: Update OriDevApp**

Use `TERMINAL_ROUTE` constant in bottom nav.

- [ ] **Step 5: Verify build**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew assembleDebug`

- [ ] **Step 6: Commit**

Message: `feat(terminal): add TerminalScreen with foldable awareness and navigation`

---

### Task 4.10: feature-terminal -- ViewModel Tests

**Files:**
- Test: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt`

- [ ] **Step 1: Write tests**

Use MockK + Turbine + UnconfinedTestDispatcher. Mock SshClient, ConnectionRepository, GetSnippetsUseCase.

12 tests:
1. `createTab_addsTabToState` -- new tab appears
2. `createTab_opensShellViaClient` -- verify SshClient.openShell called
3. `closeTab_removesTab` -- tab removed
4. `closeTab_closesShellHandle` -- onClose callback invoked
5. `switchTab_updatesActiveIndex` -- index changes
6. `sendInput_writesToOutputStream` -- bytes written to mock output stream
7. `paste_singleLine_sendsDirectly` -- no confirmation for single line
8. `paste_multiLine_showsConfirmation` -- multiline triggers dialog
9. `confirmPaste_sendsTextAndDismisses` -- after confirm, text sent, dialog cleared
10. `cancelPaste_dismissesDialog` -- dialog cleared without sending
11. `copyToClipboard_addsToHistory_maxTen` -- history grows, capped
12. `toggleKeyboard_togglesVisibility` -- keyboard visibility flips

Also add `SnippetRepositoryImplTest` to `data/src/test/`:
- `getSnippetsForServer_mapsAllFields` -- verify isWatchQuickCommand and sortOrder survive roundtrip
- `addSnippet_callsDao` -- verify insert called

And add `SshShellManagerTest` to `core/core-network/src/test/`:
- `openShell_returnsValidHandle` -- test with mock SSHClient (verify allocatePTY + startShell called)
- `closeShell_removesFromMap` -- verify shell removed
- `closeAllShells_closesEverything` -- verify all closed
- `isShellOpen_returnsFalseAfterClose` -- state check

- [ ] **Step 2: Run tests**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew :feature-terminal:test`

- [ ] **Step 3: Commit**

Message: `test(terminal): add TerminalViewModel tests`

---

### Task 4.11: Verify Phase 4 + Push

- [ ] **Step 1: Run all tests**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew test`

- [ ] **Step 2: Compile**

Run: `./gradlew assembleDebug`

- [ ] **Step 3: Fix and commit if needed**

Message: `chore: resolve Phase 4 build issues`

- [ ] **Step 4: Push to GitHub**

Run: `git push origin master`

---

## Phase 4 Completion Checklist

- [ ] `core-network`: ShellHandle, SshShellSession (PTY + resize), SshShellManager, openShell on SshClient
- [ ] `domain`: TerminalTab, CommandSnippet (all fields), SnippetRepository, GetSnippetsUseCase
- [ ] `data`: SnippetRepositoryImpl (all fields mapped), TerminalModule
- [ ] `app`: ConnectionService (foreground service, persistent notification, Hilt)
- [ ] `feature-terminal`: TerminalViewModel (shell lifecycle, reader coroutine on IO, clipboard, snippets), CustomKeyboard (5 rows, sticky modifiers, key repeat, F-key sequences), TerminalTabBar, ClipboardHistory, SnippetSheet, TerminalPreferencesSheet, TerminalScreen (landscape/portrait fold detection), Navigation
- [ ] Version catalog: termlib added
- [ ] All tests pass, project compiles, pushed to GitHub

## Key Design Decisions
- **ConnectBot termlib** (Apache 2.0) handles all terminal emulation (VT100/xterm/256-color/UTF-8 via libvterm)
- **ConnectionService** keeps shell sessions alive in background
- **outputVersion counter** in TerminalTabState triggers StateFlow recomposition
- **Reader coroutine on Dispatchers.IO** for blocking InputStream.read()
- **Paste confirmation** for multiline text (security: prevents accidental multi-command execution)
- **EXTRA_IS_SENSITIVE** on clipboard entries

## Deferred to Phase 6
- Session Recorder (export as Markdown)
- "Send to Claude" (Claude API integration)
- Auto-detect code blocks in output
- Inline diff viewer
- Notification when long-running command completes
