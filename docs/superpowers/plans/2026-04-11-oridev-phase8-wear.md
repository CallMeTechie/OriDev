# Ori:Dev Phase 8: Wear OS Companion App (v2 -- post-review)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Wear OS Companion App with connection monitoring, quick commands, transfer view, server health dashboard, panic button (disconnect-all), Tiles/Complications, and bidirectional phone-watch Data Layer sync. Spec-listed "Claude notifications" on watch and "standalone WiFi SSH" are handled as documented limitations for v1.

**Architecture:** The `wear` module is an independent APK (same Gradle project) that shares `domain` + `core-common` + `core-security` with the phone module. Communication with the phone uses Google Play Services Wearable Data Layer API (Data Client for state sync, Message Client for commands). The watch app does NOT directly run SSH -- instead it sends commands to the phone which executes them and returns output.

**Tech Stack:** Wear Compose Material3 1.5.0 (already in catalog), Play Services Wearable 19.x, Horologist 0.6.22, Hilt. Watch-side persistence: minimal (transient state in StateFlows; phone is source of truth).

**Depends on:** Phase 2 (ConnectionRepository), Phase 5 (TransferRepository), Phase 6b (CommandSnippet with isWatchQuickCommand flag already exists).

---

## Design Decisions

1. **Phone is source of truth.** The watch is a thin client. All shell execution, file operations, and state management happen on the phone. The watch displays phone-synced state and sends command messages.

2. **Data Layer API split:**
   - `DataClient.putDataItem("/connections/status")` -- phone publishes active connections every time state changes (debounced 1/sec)
   - `DataClient.putDataItem("/transfers/active")` -- phone publishes active transfers
   - `DataClient.putDataItem("/snippets/watch")` -- phone publishes watch-quick-command snippets
   - `MessageClient` for commands: `/command/execute`, `/panic/disconnect-all`, `/connect`, `/disconnect`
   - `ChannelClient` for command output >100KB (deferred to post-v1)

3. **Standalone SSH on watch** -- deferred. The watch has no libvterm, no SSHJ. Shipping SSH on watch would double binary size. v1 requires phone connection.

4. **Claude notifications on watch** -- deferred. Requires a NotificationListenerService and complex cross-device messaging. v1 focuses on the core connection/transfer/commands loop.

5. **Tiles & Complications** -- v1 ships ONE Tile (main summary). Complications deferred (they need watchface integration tests on real hardware).

6. **Panic Button = MessageClient send + confirmation UI** -- two-tap confirmation, destructive red button.

---

## File Structure

```
app/src/main/kotlin/dev/ori/app/wear/
├── WearDataSyncService.kt          # Phone-side Wearable service
└── WearDataSyncPublisher.kt        # Phone publishes state changes to DataClient

wear/src/main/kotlin/dev/ori/wear/
├── WearApplication.kt              # @HiltAndroidApp
├── MainActivity.kt                 # setContent { WearApp() }
├── di/
│   └── WearModule.kt
├── sync/
│   ├── WearDataSyncClient.kt       # Watch-side Wearable listener
│   └── WearState.kt                # In-memory state holder
├── ui/
│   ├── theme/
│   │   └── OriDevWearTheme.kt
│   ├── WearApp.kt                  # NavHost
│   ├── WearNavigation.kt
│   ├── screens/
│   │   ├── MainTileScreen.kt
│   │   ├── ConnectionListScreen.kt
│   │   ├── TransferMonitorScreen.kt
│   │   ├── QuickCommandsScreen.kt
│   │   ├── ServerHealthScreen.kt
│   │   ├── PanicButtonScreen.kt
│   │   └── CommandOutputScreen.kt
│   └── component/
│       ├── StatusIndicator.kt
│       ├── ProgressRing.kt
│       └── HealthGauge.kt
├── tile/
│   ├── MainTileService.kt          # Wear Tiles v2 API
│   └── MainTileProvider.kt
└── AndroidManifest.xml             # updated

domain/src/main/kotlin/dev/ori/domain/
├── model/
│   └── WearPayloads.kt             # shared serializable payloads for Data Layer
```

---

### Task 8.1: Shared Wear Payloads in Domain

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/model/WearPayloads.kt`

These are the serializable state objects sent over the Data Layer. They live in `domain` so both app and wear modules can use them without duplication.

- [ ] **Step 1: Create WearPayloads**

```kotlin
package dev.ori.domain.model

/**
 * Payloads exchanged between phone and watch via Data Layer API.
 * Must remain serializable with a stable binary format -- avoid renaming fields.
 *
 * Serialization is done manually via android.os.Bundle / PutDataMapRequest rather than
 * Kotlin serialization to avoid adding a new dependency. Each payload has a toMap() /
 * fromMap() pair in the data sync layer.
 */

data class WearConnectionPayload(
    val profileId: Long,
    val serverName: String,
    val host: String,
    val status: String, // ConnectionStatus enum name
    val connectedSinceMillis: Long?,
)

data class WearTransferPayload(
    val transferId: Long,
    val sourcePath: String,
    val destinationPath: String,
    val direction: String, // TransferDirection enum name
    val status: String,    // TransferStatus enum name
    val totalBytes: Long,
    val transferredBytes: Long,
    val filesTransferred: Int,
    val fileCount: Int,
)

data class WearSnippetPayload(
    val id: Long,
    val name: String,
    val command: String,
    val category: String,
    val serverProfileId: Long?,
)

data class WearCommandRequest(
    val requestId: String,
    val profileId: Long,
    val command: String,
)

data class WearCommandResponse(
    val requestId: String,
    val exitCode: Int,
    val stdout: String, // truncated to first 4KB
    val stderr: String, // truncated to first 1KB
    val truncated: Boolean,
)

/** 2FA: phone -> watch when a connection needs approval */
data class WearTwoFactorRequest(
    val requestId: String,
    val profileId: Long,
    val serverName: String,
    val host: String,
    val expiresAtMillis: Long, // 30s from now
)

/** 2FA: watch -> phone response */
data class WearTwoFactorResponse(
    val requestId: String,
    val approved: Boolean,
)

/** Data Layer paths and Message paths */
object WearPaths {
    const val CONNECTIONS_STATUS = "/oridev/connections/status"
    const val TRANSFERS_ACTIVE = "/oridev/transfers/active"
    const val SNIPPETS_WATCH = "/oridev/snippets/watch"
    const val COMMAND_EXECUTE = "/oridev/command/execute"
    const val COMMAND_RESPONSE = "/oridev/command/response"
    const val PANIC_DISCONNECT_ALL = "/oridev/panic/disconnect-all"
    const val CONNECT_REQUEST = "/oridev/connect"
    const val DISCONNECT_REQUEST = "/oridev/disconnect"
    const val TWO_FA_REQUEST = "/oridev/2fa/request"
    const val TWO_FA_RESPONSE = "/oridev/2fa/response"
}
```

- [ ] **Step 2: Commit**

Message: `feat(domain): add Wear Data Layer payload types and paths`

---

### Task 8.2: Phone-Side Wear Data Sync

The phone publishes state changes to the Data Layer so the watch can display them. It also receives MessageClient commands from the watch.

**Files:**
- Modify: `app/build.gradle.kts` (add play-services-wearable dep)
- Create: `app/src/main/kotlin/dev/ori/app/wear/WearDataSyncPublisher.kt`
- Create: `app/src/main/kotlin/dev/ori/app/wear/WearMessageListenerService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (register WearMessageListenerService)

- [ ] **Step 1: Add play-services-wearable to app**

In `app/build.gradle.kts`:
```kotlin
implementation(libs.play.services.wearable)
```

- [ ] **Step 2: Create WearDataSyncPublisher**

@Singleton. Injects ConnectionRepository, TransferRepository, SnippetRepository, @ApplicationContext Context.

Starts a coroutine scope (SupervisorJob + Dispatchers.IO) on first use. Collects:
- `connectionRepository.getActiveConnections()` -> map to List<WearConnectionPayload> -> putDataItem(CONNECTIONS_STATUS)
- `transferRepository.getActiveTransfers()` -> map to List<WearTransferPayload> -> putDataItem(TRANSFERS_ACTIVE)
- `snippetRepository.getSnippetsForServer(null)` -> filter isWatchQuickCommand -> map -> putDataItem(SNIPPETS_WATCH)

Debounce: use `.conflate()` + a 1-second throttle via `.sample(1000)` to avoid flooding the Data Layer API.

Serialization: build a `PutDataMapRequest.create(path)`. For list payloads, use `DataMap.putDataMapArrayList()`. Each payload object becomes a DataMap with individual key-value entries.

Start method: `fun startPublishing(scope: CoroutineScope)` called from `OriDevApplication.onCreate()`.

```kotlin
@Singleton
@OptIn(kotlinx.coroutines.FlowPreview::class) // sample() is experimental
class WearDataSyncPublisher @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val transferRepository: TransferRepository,
    private val snippetRepository: SnippetRepository,
    @ApplicationContext private val context: Context,
) {
    private val dataClient: DataClient get() = Wearable.getDataClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            connectionRepository.getActiveConnections()
                .sample(1000)
                .collect { connections -> publishConnections(connections) }
        }
        scope.launch {
            transferRepository.getActiveTransfers()
                .sample(1000)
                .collect { transfers -> publishTransfers(transfers) }
        }
        scope.launch {
            snippetRepository.getSnippetsForServer(null)
                .map { list -> list.filter { it.isWatchQuickCommand } }
                .distinctUntilChanged()
                .collect { snippets -> publishSnippets(snippets) }
        }
    }

    private suspend fun publishConnections(connections: List<Connection>) {
        runCatching {
            val request = PutDataMapRequest.create(WearPaths.CONNECTIONS_STATUS).apply {
                dataMap.putLong("updated_at", System.currentTimeMillis())
                val items = ArrayList<DataMap>()
                for (c in connections) {
                    items.add(DataMap().apply {
                        putLong("profileId", c.profileId)
                        putString("serverName", c.serverName)
                        putString("host", c.host)
                        putString("status", c.status.name)
                        putLong("connectedSinceMillis", c.connectedSince ?: 0L)
                    })
                }
                dataMap.putDataMapArrayList("items", items)
            }
            dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
        }
    }

    // publishTransfers and publishSnippets follow same pattern
}
```

NOTE: `OriDevApplication` currently has NO `onCreate()` override. This task must ADD the override:
```kotlin
@HiltAndroidApp
class OriDevApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var wearDataSyncPublisher: WearDataSyncPublisher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        wearDataSyncPublisher.start()
    }
}
```

- [ ] **Step 3: Create WearMessageListenerService**

Android Service extending `WearableListenerService`. Overrides `onMessageReceived(messageEvent: MessageEvent)` to handle incoming messages from the watch.

```kotlin
@AndroidEntryPoint
class WearMessageListenerService : WearableListenerService() {

    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var sshClient: SshClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * THREAT MODEL: MessageClient events are received from paired Wear OS devices.
     * Google Play Services ensures the wire protocol is authenticated per-device-pair.
     * However, a malicious companion app on the same paired watch could call
     * MessageClient with our path prefix. To mitigate:
     * 1. Validate sourceNodeId is in the currently paired-and-connected nodes list
     *    (not strictly sufficient, but rejects unpaired nodes)
     * 2. Command execution is limited to already-established SSH sessions;
     *    no new shell channels are opened from watch-originated messages
     * 3. Future: add an HMAC token provisioned at pairing time (deferred)
     */
    override fun onMessageReceived(event: MessageEvent) {
        scope.launch {
            if (!isTrustedNode(event.sourceNodeId)) return@launch
            when (event.path) {
                WearPaths.PANIC_DISCONNECT_ALL -> handlePanicDisconnect()
                WearPaths.COMMAND_EXECUTE -> handleCommandExecute(event)
                WearPaths.CONNECT_REQUEST -> handleConnect(event)
                WearPaths.DISCONNECT_REQUEST -> handleDisconnect(event)
                WearPaths.TWO_FA_RESPONSE -> handleTwoFactorResponse(event)
            }
        }
    }

    private suspend fun isTrustedNode(nodeId: String): Boolean = runCatching {
        val nodes = Wearable.getNodeClient(this).connectedNodes.await()
        nodes.any { it.id == nodeId }
    }.getOrDefault(false)

    private fun handlePanicDisconnect() {
        scope.launch {
            connectionRepository.getActiveConnections().first().forEach { conn ->
                runCatching { connectionRepository.disconnect(conn.profileId) }
            }
        }
    }

    private fun handleCommandExecute(event: MessageEvent) {
        scope.launch {
            val requestData = DataMap.fromByteArray(event.data)
            val profileId = requestData.getLong("profileId")
            val command = requestData.getString("command") ?: return@launch
            val requestId = requestData.getString("requestId") ?: return@launch

            // getActiveSessionId is already on the ConnectionRepository interface (Phase 7)
            val sessionId = connectionRepository.getActiveSessionId(profileId)
            if (sessionId == null) {
                sendResponse(event.sourceNodeId, requestId, -1, "", "Not connected", false)
                return@launch
            }

            runCatching {
                val result = sshClient.executeCommand(sessionId, command)
                val stdout = result.stdout.take(4096)
                val stderr = result.stderr.take(1024)
                val truncated = result.stdout.length > 4096 || result.stderr.length > 1024
                sendResponse(event.sourceNodeId, requestId, result.exitCode, stdout, stderr, truncated)
            }.onFailure {
                sendResponse(event.sourceNodeId, requestId, -1, "", it.message ?: "Error", false)
            }
        }
    }

    private fun handleTwoFactorResponse(event: MessageEvent) {
        scope.launch {
            val map = DataMap.fromByteArray(event.data)
            val requestId = map.getString("requestId") ?: return@launch
            val approved = map.getBoolean("approved")
            // Notify any pending 2FA waiters via a shared sink (see Task 8.2 Step 5 -- TwoFactorCoordinator)
            dev.ori.app.wear.TwoFactorCoordinator.completeRequest(requestId, approved)
        }
    }

    private suspend fun sendResponse(
        nodeId: String,
        requestId: String,
        exitCode: Int,
        stdout: String,
        stderr: String,
        truncated: Boolean,
    ) {
        val messageClient = Wearable.getMessageClient(this)
        val data = DataMap().apply {
            putString("requestId", requestId)
            putInt("exitCode", exitCode)
            putString("stdout", stdout)
            putString("stderr", stderr)
            putBoolean("truncated", truncated)
        }
        runCatching {
            messageClient.sendMessage(nodeId, WearPaths.COMMAND_RESPONSE, data.toByteArray()).await()
        }
    }

    private fun handleConnect(event: MessageEvent) {
        scope.launch {
            val profileId = ByteBuffer.wrap(event.data).long
            runCatching { connectionRepository.connect(profileId) }
        }
    }

    private fun handleDisconnect(event: MessageEvent) {
        scope.launch {
            val profileId = ByteBuffer.wrap(event.data).long
            runCatching { connectionRepository.disconnect(profileId) }
        }
    }
}
```

- [ ] **Step 3b: Create TwoFactorCoordinator**

`app/src/main/kotlin/dev/ori/app/wear/TwoFactorCoordinator.kt` -- singleton object used to correlate 2FA requests sent to the watch with the response received asynchronously via MessageClient.

```kotlin
package dev.ori.app.wear

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates 2FA request/response correlation between the phone connection flow
 * and the watch's approve/deny response. The Connection flow publishes a request
 * and awaits a CompletableDeferred<Boolean>; the MessageListenerService completes
 * it when the watch responds.
 */
object TwoFactorCoordinator {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun registerRequest(requestId: String): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        pending[requestId] = deferred
        return deferred
    }

    fun completeRequest(requestId: String, approved: Boolean) {
        pending.remove(requestId)?.complete(approved)
    }

    fun cancelRequest(requestId: String) {
        pending.remove(requestId)?.complete(false)
    }
}
```

Also add a helper `WearTwoFactorSender` (or a method on `WearDataSyncPublisher`) that:
- Takes a profileId, serverName, host
- Generates requestId
- Calls TwoFactorCoordinator.registerRequest
- Publishes WearTwoFactorRequest via `MessageClient.sendMessage(nodeId, WearPaths.TWO_FA_REQUEST, data)` to all connected nodes
- Awaits the deferred with 30s timeout via `withTimeoutOrNull(30_000)`
- On timeout -> cancelRequest, return false
- Returns the boolean approval

This helper is called from `ConnectionRepositoryImpl.connect()` BEFORE the SSH handshake when `ServerProfile.require2fa == true`. Adding `require2fa` to ServerProfile is in scope for this phase; add it as a new nullable/default-false field.

Actually: ServerProfile modification is invasive. Alternative: store `require2fa` in a separate config or ignore for v1 and make 2FA always-on for profiles with a specific flag. For v1 simplicity: add `require2fa: Boolean = false` to ServerProfile domain model + entity + Room migration. Mark this clearly.

- [ ] **Step 4: Register service in app AndroidManifest.xml**

```xml
<service
    android:name=".wear.WearMessageListenerService"
    android:exported="true"
    android:permission="com.google.android.gms.permission.BIND_LISTENER">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <data android:scheme="wear" android:host="*" android:pathPrefix="/oridev/" />
    </intent-filter>
</service>
```

The `android:exported="true"` is REQUIRED for Wearable services (they receive events from the system) but they are protected by the `BIND_LISTENER` permission.

- [ ] **Step 5: Commit**

Message: `feat(app): add Wear Data Layer publisher and message listener service`

---

### Task 8.3: Wear Module Setup + Application/MainActivity

**Files:**
- Modify: `wear/build.gradle.kts` (verify deps, add missing)
- Create: `wear/src/main/kotlin/dev/ori/wear/WearApplication.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/MainActivity.kt`
- Modify: `wear/src/main/AndroidManifest.xml` (application name + activity)

- [ ] **Step 1: Verify wear/build.gradle.kts**

Read current file. Check that it has:
- `alias(libs.plugins.hilt)` + `alias(libs.plugins.ksp)`
- `implementation(project(":core:core-common"))`, `implementation(project(":core:core-ui"))` -- no, core-ui is for phone Compose
- Actually: `implementation(project(":core:core-common"))`, `implementation(project(":domain"))`
- `implementation(libs.wear.compose.material3)`, `implementation(libs.wear.compose.foundation)`, `implementation(libs.wear.compose.navigation)`
- `implementation(libs.horologist.compose.layout)`
- `implementation(libs.play.services.wearable)`
- `implementation(libs.hilt.android)`, `ksp(libs.hilt.android.compiler)`
- `implementation(libs.activity.compose)`
- `implementation(libs.compose.material.icons.extended)` (add if missing)
- `implementation(libs.hilt.navigation.compose)` -- REQUIRED for `hiltViewModel<T>()` calls in Compose
- Test: junit5, mockk, turbine
- `tasks.withType<Test> { useJUnitPlatform() }`

Add any missing deps.

**Wear Compose import map (CRITICAL):**
- `ScalingLazyColumn` is in `androidx.wear.compose.foundation.lazy.*` (NOT material3)
- `Button`, `FilledTonalButton`, `Card`, `Text`, `MaterialTheme` are in `androidx.wear.compose.material3.*`
- `AppScaffold`, `ScreenScaffold` are in `androidx.wear.compose.material3.*` (promoted from Horologist in 1.5.0)
- `SwipeDismissableNavHost`, `rememberSwipeDismissableNavController` are in `androidx.wear.compose.navigation.*`
- Wear Compose Material3 1.5 does NOT have a `Chip` composable -- use `Button` / `FilledTonalButton` with `label` slot

- [ ] **Step 2: Create WearApplication**

```kotlin
package dev.ori.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WearApplication : Application()
```

- [ ] **Step 3: Create MainActivity**

```kotlin
package dev.ori.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import dev.ori.wear.ui.WearApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}
```

- [ ] **Step 4: Update wear/src/main/AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".WearApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.DeviceDefault">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.DeviceDefault">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Remove any existing `.gitkeep` from wear source dir.

- [ ] **Step 5: Verify build**

Run: `./gradlew :wear:assembleDebug`

Fix any issues. If the existing wear/build.gradle.kts or manifest has different content, adapt.

- [ ] **Step 6: Commit**

Message: `feat(wear): add WearApplication, MainActivity, and updated manifest`

---

### Task 8.4: Wear Data Sync Client + State

The watch listens to DataClient events from the phone and updates an in-memory state.

**Files:**
- Create: `wear/src/main/kotlin/dev/ori/wear/sync/WearState.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/sync/WearDataSyncClient.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/di/WearModule.kt`

- [ ] **Step 1: Create WearState**

```kotlin
package dev.ori.wear.sync

import dev.ori.domain.model.WearConnectionPayload
import dev.ori.domain.model.WearSnippetPayload
import dev.ori.domain.model.WearTransferPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearState @Inject constructor() {
    private val _connections = MutableStateFlow<List<WearConnectionPayload>>(emptyList())
    val connections: StateFlow<List<WearConnectionPayload>> = _connections.asStateFlow()

    private val _transfers = MutableStateFlow<List<WearTransferPayload>>(emptyList())
    val transfers: StateFlow<List<WearTransferPayload>> = _transfers.asStateFlow()

    private val _snippets = MutableStateFlow<List<WearSnippetPayload>>(emptyList())
    val snippets: StateFlow<List<WearSnippetPayload>> = _snippets.asStateFlow()

    private val _isPhoneReachable = MutableStateFlow(false)
    val isPhoneReachable: StateFlow<Boolean> = _isPhoneReachable.asStateFlow()

    private val _lastCommandOutput = MutableStateFlow<String?>(null)
    val lastCommandOutput: StateFlow<String?> = _lastCommandOutput.asStateFlow()

    fun updateConnections(list: List<WearConnectionPayload>) { _connections.value = list }
    fun updateTransfers(list: List<WearTransferPayload>) { _transfers.value = list }
    fun updateSnippets(list: List<WearSnippetPayload>) { _snippets.value = list }
    fun updatePhoneReachable(reachable: Boolean) { _isPhoneReachable.value = reachable }
    fun setCommandOutput(output: String?) { _lastCommandOutput.value = output }
}
```

- [ ] **Step 2: Create WearDataSyncClient**

Listens to DataClient events. Injected with @ApplicationContext Context and WearState.

```kotlin
@Singleton
class WearDataSyncClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearState: WearState,
) : DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)

    // Scope is lazily created on each start() so that stop()+start() cycles work correctly
    private var scope: CoroutineScope? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        dataClient.addListener(this)
        messageClient.addListener(this)
        s.launch { pollPhoneReachability() }
        s.launch { loadInitialState() }
    }

    fun stop() {
        if (!started) return
        started = false
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        scope?.cancel()
        scope = null
    }

    override fun onDataChanged(buffer: DataEventBuffer) {
        for (event in buffer) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            handleDataUpdate(path, DataMapItem.fromDataItem(event.dataItem).dataMap)
        }
        buffer.release()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.COMMAND_RESPONSE -> handleCommandResponse(event.data)
        }
    }

    private fun handleDataUpdate(path: String, dataMap: DataMap) {
        when (path) {
            WearPaths.CONNECTIONS_STATUS -> {
                val items = dataMap.getDataMapArrayList("items").orEmpty()
                val list = items.map { m ->
                    WearConnectionPayload(
                        profileId = m.getLong("profileId"),
                        serverName = m.getString("serverName") ?: "",
                        host = m.getString("host") ?: "",
                        status = m.getString("status") ?: "DISCONNECTED",
                        connectedSinceMillis = m.getLong("connectedSinceMillis").takeIf { it > 0 },
                    )
                }
                wearState.updateConnections(list)
            }
            WearPaths.TRANSFERS_ACTIVE -> {
                // Similar mapping
            }
            WearPaths.SNIPPETS_WATCH -> {
                // Similar mapping
            }
        }
    }

    private fun handleCommandResponse(data: ByteArray) {
        val map = DataMap.fromByteArray(data)
        val stdout = map.getString("stdout") ?: ""
        val stderr = map.getString("stderr") ?: ""
        val exitCode = map.getInt("exitCode")
        val truncated = map.getBoolean("truncated")
        val output = buildString {
            append(stdout)
            if (stderr.isNotEmpty()) append("\n[stderr]\n").append(stderr)
            if (truncated) append("\n[... output truncated ...]")
            if (exitCode != 0) append("\n[exit: $exitCode]")
        }
        wearState.setCommandOutput(output)
    }

    private suspend fun pollPhoneReachability() {
        while (true) {
            val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
            wearState.updatePhoneReachable(nodes.any { it.isNearby })
            delay(5000)
        }
    }

    private suspend fun loadInitialState() {
        // Fetch current DataItems for each path on startup
        listOf(
            WearPaths.CONNECTIONS_STATUS,
            WearPaths.TRANSFERS_ACTIVE,
            WearPaths.SNIPPETS_WATCH,
        ).forEach { path ->
            runCatching {
                val items = dataClient.getDataItems(
                    android.net.Uri.parse("wear://*$path")
                ).await()
                items.forEach { item ->
                    handleDataUpdate(path, DataMapItem.fromDataItem(item).dataMap)
                }
                items.release()
            }
        }
    }

    /** Watch -> Phone: send an execute-command request */
    suspend fun sendCommand(profileId: Long, command: String): String {
        val requestId = java.util.UUID.randomUUID().toString()
        val data = DataMap().apply {
            putString("requestId", requestId)
            putLong("profileId", profileId)
            putString("command", command)
        }
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        nodes.firstOrNull { it.isNearby }?.let { node ->
            runCatching {
                messageClient.sendMessage(node.id, WearPaths.COMMAND_EXECUTE, data.toByteArray()).await()
            }
        }
        return requestId
    }

    suspend fun sendPanicDisconnect() {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        nodes.firstOrNull { it.isNearby }?.let { node ->
            runCatching {
                messageClient.sendMessage(node.id, WearPaths.PANIC_DISCONNECT_ALL, ByteArray(0)).await()
            }
        }
    }
}
```

- [ ] **Step 3: Create WearModule (Hilt)**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object WearModule {
    // All injections are @Singleton via constructor; no explicit provides needed
    // unless we need to provide Wearable clients (they come from Wearable.getXClient(context))
}
```

Start the WearDataSyncClient from MainActivity.onCreate() OR from the Compose layer via DisposableEffect.

- [ ] **Step 4: Commit**

Message: `feat(wear): add Data Layer sync client with state holder`

---

### Task 8.5: Wear UI -- Theme, Navigation, Main Tile Screen

**Files:**
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/theme/OriDevWearTheme.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/WearApp.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/WearNavigation.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/screens/MainTileScreen.kt`

- [ ] **Step 1: Create OriDevWearTheme**

Wear OS has its own theming: `androidx.wear.compose.material3.MaterialTheme`. Use the default dark theme (watch OLEDs save power with dark).

```kotlin
package dev.ori.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun OriDevWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
```

- [ ] **Step 2: Create WearNavigation**

```kotlin
package dev.ori.wear.ui

const val ROUTE_MAIN = "main"
const val ROUTE_CONNECTIONS = "connections"
const val ROUTE_TRANSFERS = "transfers"
const val ROUTE_QUICK_COMMANDS = "quick_commands"
const val ROUTE_SERVER_HEALTH = "server_health"
const val ROUTE_PANIC = "panic"
const val ROUTE_COMMAND_OUTPUT = "command_output"
```

- [ ] **Step 3: Create WearApp**

```kotlin
@Composable
fun WearApp() {
    val viewModel: WearAppViewModel = hiltViewModel()

    OriDevWearTheme {
        val navController = rememberSwipeDismissableNavController()

        DisposableEffect(Unit) {
            viewModel.startSync()
            onDispose { viewModel.stopSync() }
        }

        AppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = ROUTE_MAIN,
            ) {
                composable(ROUTE_MAIN) { MainTileScreen(navController) }
                composable(ROUTE_CONNECTIONS) { ConnectionListScreen(navController) }
                composable(ROUTE_TRANSFERS) { TransferMonitorScreen(navController) }
                composable(ROUTE_QUICK_COMMANDS) { QuickCommandsScreen(navController) }
                composable(ROUTE_SERVER_HEALTH) { ServerHealthScreen(navController) }
                composable(ROUTE_PANIC) { PanicButtonScreen(navController) }
                composable(ROUTE_COMMAND_OUTPUT) { CommandOutputScreen(navController) }
            }
        }
    }
}
```

NOTE: `AppScaffold`, `SwipeDismissableNavHost`, `rememberSwipeDismissableNavController` are from Wear Compose Navigation library. Verify the import paths against the actual library version (likely `androidx.wear.compose.navigation.*`).

Since Hilt + hiltViewModel may not have a straightforward way to inject a non-ViewModel object into a composable, create a `WearAppViewModel` wrapper that exposes `WearDataSyncClient` via @Inject.

- [ ] **Step 4: Create MainTileScreen**

Main tile (first screen). Shows:
- Header "Ori:Dev"
- Connection count (from wearState.connections) with StatusDot
- Active transfers count
- Last command preview (if any)
- Grid of action chips: Connections, Transfers, Commands, Panic

Wear Compose uses `ScalingLazyColumn` for scrollable content.

```kotlin
@Composable
fun MainTileScreen(
    navController: NavHostController,
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val phoneReachable by viewModel.phoneReachable.collectAsStateWithLifecycle()

    ScreenScaffold {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Text("Ori:Dev", style = MaterialTheme.typography.titleMedium) }
            item {
                Row {
                    StatusIndicator(connected = phoneReachable)
                    Text("${connections.size} connected")
                }
            }
            item {
                Text("${transfers.size} transfers", style = MaterialTheme.typography.bodySmall)
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(ROUTE_CONNECTIONS) },
                    label = { Text("Connections") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(ROUTE_TRANSFERS) },
                    label = { Text("Transfers") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(ROUTE_QUICK_COMMANDS) },
                    label = { Text("Commands") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(ROUTE_SERVER_HEALTH) },
                    label = { Text("Health") },
                )
            }
            item {
                Button(
                    onClick = { navController.navigate(ROUTE_PANIC) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    label = { Text("PANIC") },
                )
            }
        }
    }
}
```

- [ ] **Step 5: Create WearAppViewModel**

```kotlin
```kotlin
@HiltViewModel
class WearAppViewModel @Inject constructor(
    private val syncClient: WearDataSyncClient,
    wearState: WearState,
) : ViewModel() {
    val connections = wearState.connections
    val transfers = wearState.transfers
    val snippets = wearState.snippets
    val phoneReachable = wearState.isPhoneReachable
    val lastCommandOutput = wearState.lastCommandOutput
    val pending2Fa = wearState.pending2Fa

    fun startSync() = syncClient.start()
    fun stopSync() = syncClient.stop()

    fun sendCommand(profileId: Long, command: String) {
        viewModelScope.launch { syncClient.sendCommand(profileId, command) }
    }

    fun sendPanicDisconnect() {
        viewModelScope.launch { syncClient.sendPanicDisconnect() }
    }

    fun respondTo2Fa(requestId: String, approved: Boolean) {
        viewModelScope.launch { syncClient.sendTwoFactorResponse(requestId, approved) }
    }
}
```
```

- [ ] **Step 6: Commit**

Message: `feat(wear): add theme, navigation, WearAppViewModel, and main tile screen`

---

### Task 8.6: Wear UI -- Remaining Screens

**Files:**
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/screens/ConnectionListScreen.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/screens/TransferMonitorScreen.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/screens/QuickCommandsScreen.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/screens/ServerHealthScreen.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/screens/PanicButtonScreen.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/screens/CommandOutputScreen.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/component/StatusIndicator.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/component/ProgressRing.kt`
- Create: `wear/src/main/kotlin/dev/ori/wear/ui/component/HealthGauge.kt`

- [ ] **Step 1: Create reusable components**

`StatusIndicator.kt`: small colored dot (green/red/gray).
`ProgressRing.kt`: circular progress for transfers (uses CircularProgressIndicator or custom Canvas).
`HealthGauge.kt`: three concentric arcs for CPU/RAM/Disk health (placeholder for v1 -- hardcoded from phone's local stats or deferred).

- [ ] **Step 2: Create ConnectionListScreen**

ScalingLazyColumn of connections. Each item: StatusIndicator + serverName + host. Tap -> navigate to ServerHealthScreen or directly call disconnect via viewModel. Long-press -> show Disconnect confirmation.

- [ ] **Step 3: Create TransferMonitorScreen**

ScalingLazyColumn of active transfers. Each item: filename, progress bar or ProgressRing, transferred/total bytes.

- [ ] **Step 4: Create QuickCommandsScreen**

ScalingLazyColumn of snippets (from wearState.snippets, which are pre-filtered to isWatchQuickCommand=true). Tap -> send command to phone, navigate to CommandOutputScreen to show result.

Need: a current "selected server" concept. For v1, if there's exactly one active connection, send to that. If multiple, show a chooser. If none, show "Connect to a server first."

- [ ] **Step 5: Create CommandOutputScreen**

Shows lastCommandOutput from wearState. Monospace text, scrollable via ScalingLazyColumn with individual lines. "Back" button.

- [ ] **Step 6: Create ServerHealthScreen**

For v1: shows the list of active servers with last connected time + StatusIndicator. Real CPU/RAM gauges are deferred (require phone to push more data).

Actually, ServerHealthScreen can be simple: a per-server detail view showing host, status, connectedSince duration, and a "Disconnect" button.

- [ ] **Step 6b: Create TwoFactorDialogScreen and wire 2FA flow on watch**

Watch-side 2FA:

1. Add `TwoFactorRequest` state to WearState:
```kotlin
private val _pending2Fa = MutableStateFlow<WearTwoFactorRequest?>(null)
val pending2Fa: StateFlow<WearTwoFactorRequest?> = _pending2Fa.asStateFlow()

fun set2FaRequest(request: WearTwoFactorRequest?) { _pending2Fa.value = request }
```

2. In WearDataSyncClient, handle MessageEvent with path `TWO_FA_REQUEST`:
```kotlin
WearPaths.TWO_FA_REQUEST -> {
    val map = DataMap.fromByteArray(event.data)
    val request = WearTwoFactorRequest(
        requestId = map.getString("requestId") ?: return,
        profileId = map.getLong("profileId"),
        serverName = map.getString("serverName") ?: "",
        host = map.getString("host") ?: "",
        expiresAtMillis = map.getLong("expiresAtMillis"),
    )
    wearState.set2FaRequest(request)
}
```

3. Add method `sendTwoFactorResponse(requestId, approved)` that sends back via MessageClient with path `TWO_FA_RESPONSE`.

4. Create `TwoFactorDialogScreen.kt` -- a full-screen prompt shown when `wearState.pending2Fa != null`:
   - Text: "Approve connection to {serverName}?"
   - Text: "{host}" (monospace, small)
   - Countdown timer (30s from request.expiresAtMillis - now)
   - Two large buttons: "Approve" (green) / "Deny" (red)
   - On tap: call viewModel.respondTo2Fa(requestId, approved), clear pending, pop
   - On timeout: auto-deny and clear

5. In WearApp.kt, observe `pending2Fa` at the top level. When non-null, present the TwoFactorDialogScreen as an overlay (via a conditional composable or a dedicated "priority route"). This should supersede the current screen.

- [ ] **Step 7: Create PanicButtonScreen (press-and-hold)**

Press-and-hold for 1.5 seconds with haptic feedback on press and fire. More robust than two-tap (prevents accidental triggers, provides tactile confirmation). This replaces the earlier two-tap design per the architecture review.

```kotlin
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PanicButtonScreen(
    navController: NavHostController,
    viewModel: WearAppViewModel = hiltViewModel(),
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var pressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }

    LaunchedEffect(pressing) {
        if (pressing && !triggered) {
            val durationMs = 1500L
            val startTime = System.currentTimeMillis()
            while (pressing) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed.toFloat() / durationMs).coerceAtMost(1f)
                if (progress >= 1f) {
                    triggered = true
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    viewModel.sendPanicDisconnect()
                    navController.popBackStack()
                    break
                }
                kotlinx.coroutines.delay(16)
            }
            if (!triggered) progress = 0f
        }
    }

    ScreenScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressing = true
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            tryAwaitRelease()
                            pressing = false
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // Circular progress ring around content as the hold progresses
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(160.dp),
                color = MaterialTheme.colorScheme.error,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "HOLD TO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "PANIC",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
```

Behavior:
- Press-and-hold fills a ring over 1.5s
- Releasing before 1.5s cancels (progress resets)
- On fire: haptic LongPress, sendPanicDisconnect, pop back
- Initial press haptic confirms contact

- [ ] **Step 8: Commit**

Message: `feat(wear): add all remaining screens (Connections, Transfers, Commands, Health, Panic, Output)`

---

### Task 8.7: Wear Tile Service

**Files:**
- Modify: `wear/build.gradle.kts` (add wear-tiles dependency)
- Modify: `gradle/libs.versions.toml` (add wear-tiles entries)
- Create: `wear/src/main/kotlin/dev/ori/wear/tile/MainTileService.kt`
- Modify: `wear/src/main/AndroidManifest.xml` (register tile service)

- [ ] **Step 1: Add Wear Tiles dependency**

```toml
# libs.versions.toml [versions]
wear-tiles = "1.5.0"

# [libraries]
wear-tiles = { module = "androidx.wear.tiles:tiles", version.ref = "wear-tiles" }
wear-tiles-material = { module = "androidx.wear.tiles:tiles-material", version.ref = "wear-tiles" }
```

In `wear/build.gradle.kts`:
```kotlin
implementation(libs.wear.tiles)
implementation(libs.wear.tiles.material)
```

- [ ] **Step 2: Create MainTileService**

A TileService that returns a Tile resource showing:
- "N connected" (from wearState)
- "M transfers"
- One button: "Open App"

Tiles API in Wear 1.5 requires returning a `TileBuilders.Tile` built from layouts. This is somewhat verbose. For v1:

```kotlin
@AndroidEntryPoint
class MainTileService : TileService() {

    @Inject lateinit var wearState: WearState

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.immediateFuture(createTile())
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
    }

    private fun createTile(): TileBuilders.Tile {
        val connectionCount = wearState.connections.value.size
        val transferCount = wearState.transfers.value.size

        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("$connectionCount connected")
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("$transferCount transfers")
                            .build()
                    )
                    .build()
            )
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(layout)
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(60_000)
            .build()
    }
}
```

- [ ] **Step 3: Register in manifest**

```xml
<service
    android:name=".tile.MainTileService"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
    <intent-filter>
        <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
    </intent-filter>
    <meta-data
        android:name="androidx.wear.tiles.PREVIEW"
        android:resource="@drawable/tile_preview" />
</service>
```

For `@drawable/tile_preview`, create a simple placeholder XML (can be a vector with text "Ori:Dev").

- [ ] **Step 4: Commit**

Message: `feat(wear): add main tile service for watchface`

---

### Task 8.8: Tests + CI Green

**Files:**
- Test: `wear/src/test/kotlin/dev/ori/wear/sync/WearStateTest.kt`
- Test: `wear/src/test/kotlin/dev/ori/wear/ui/WearAppViewModelTest.kt`
- Test (app): `app/src/test/kotlin/dev/ori/app/wear/WearDataSyncPublisherTest.kt`

- [ ] **Step 1: Write WearStateTest**

5 tests: updateConnections sets flow value, updateTransfers, updateSnippets, updatePhoneReachable, setCommandOutput.

- [ ] **Step 2: Write WearAppViewModelTest**

4 tests using Turbine:
- exposesConnections -- flow delegates to WearState
- exposesTransfers
- sendCommand_callsSyncClient
- sendPanicDisconnect_callsSyncClient

Use fake WearDataSyncClient (can't easily mock it due to its context dependency; create a test-only subclass or interface).

Actually simpler: create `WearDataSyncClient` as an abstract interface with an implementation. Then the ViewModel can depend on the interface and tests can provide fakes.

If that's too invasive for this task, skip WearAppViewModelTest and only test WearState.

- [ ] **Step 3: Write WearDataSyncPublisherTest (app module)**

Test the serialization paths: given a List<Connection>, verify the published DataMapItem has the correct structure.

Since DataClient requires Wearable runtime, we can't unit-test `.putDataItem()` directly. Test the mapping logic by extracting a pure function `connectionsToDataMap(connections): DataMap` and test that.

- [ ] **Step 4: Run all checks**

```bash
./gradlew detekt
./gradlew test
./gradlew :wear:assembleDebug
./gradlew assembleDebug
```

Fix any violations. Common issues:
- Wear Compose imports differ from phone Compose -- use `androidx.wear.compose.*`
- Wear doesn't have OriDevTheme (uses MaterialTheme from wear.compose.material3)
- ScalingLazyColumn / Chip / Button imports are from wear.compose.material3

- [ ] **Step 5: Commit**

Message: `test(wear): add WearState and Data Sync tests`

- [ ] **Step 6: Push**

`git push origin master`

- [ ] **Step 7: Monitor CI until green**

`gh run list --branch master --limit 5` until success. If failures, `gh run view <id> --log-failed`, fix, push, repeat.

**DO NOT report DONE until CI is GREEN.**

---

## Phase 8 Completion Checklist

- [ ] `domain`: WearPayloads (Connection, Transfer, Snippet, Command, TwoFactorRequest, TwoFactorResponse) + WearPaths constants including TWO_FA paths
- [ ] `domain`: `ServerProfile.require2fa` field added (and corresponding entity/migration in data)
- [ ] `domain`: Verify `ConnectionRepository.getActiveSessionId()` exists (Phase 7 added it)
- [ ] `app`: WearDataSyncPublisher (with @OptIn(FlowPreview::class)), WearMessageListenerService (with isTrustedNode validation), TwoFactorCoordinator
- [ ] `app`: OriDevApplication.onCreate() override added to start publisher
- [ ] `app`: Connection flow integration: ConnectionRepositoryImpl.connect() checks require2fa, sends 2FA request, awaits response before SSH handshake
- [ ] `wear`: WearApplication, MainActivity, Hilt setup with hilt-navigation-compose
- [ ] `wear`: WearState holder (includes pending2Fa) + WearDataSyncClient (handles TWO_FA_REQUEST, idempotency guard)
- [ ] `wear`: OriDevWearTheme, WearNavigation, WearApp with SwipeDismissableNavHost (correct imports: foundation.lazy, material3, navigation)
- [ ] `wear`: MainTileScreen (FilledTonalButton/Button, no Chip), ConnectionListScreen, TransferMonitorScreen, QuickCommandsScreen, ServerHealthScreen (stub), PanicButtonScreen (press-and-hold with haptic), CommandOutputScreen, TwoFactorDialogScreen
- [ ] `wear`: WearAppViewModel, reusable components (StatusIndicator, ProgressRing, HealthGauge as stub)
- [ ] `wear`: MainTileService (Wear Tiles v1.5)
- [ ] Tests: WearState, WearAppViewModel (via interface-extracted WearDataSyncClient), WearDataSyncPublisher serialization, WearPayloads round-trip, TwoFactorCoordinator
- [ ] detekt clean, both phone and wear APKs build, CI GREEN

## Known Limitations (Documented)

1. **No Claude notifications on watch** -- requires NotificationListenerService and cross-device messaging. Deferred.
2. **No standalone SSH from watch** -- v1 requires phone proximity. The watch has no libvterm and no SSHJ. Standalone mode would double the binary and is out of scope.
3. **No complications** -- complications require watchface integration tests on real hardware. v1 ships only the Tile.
4. **HealthGauge is a placeholder** -- CPU/RAM/Disk values require the phone to push additional stats or call Proxmox API. Deferred.
5. **Command output truncated to 4KB stdout / 1KB stderr** -- ChannelClient streaming deferred.
6. **Data Layer authentication via source node validation only** -- Wearable Data Layer traffic is transported over authenticated BT link-layer encryption between paired devices. However, a malicious companion Wear app on the same paired watch could in principle call MessageClient with our paths. The WearMessageListenerService validates `event.sourceNodeId` against currently connected nodes (via `Wearable.getNodeClient().connectedNodes`), which rejects unpaired nodes but does not authenticate per-app. A future enhancement would add an HMAC token provisioned at pairing time, verified on every message. Documented threat model; v1 accepts the residual risk of a malicious co-installed wear companion.
