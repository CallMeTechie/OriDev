# Ori:Dev -- Design Specification

## 1. Projektbeschreibung

**Ori:Dev -- 折り Dev** (Falten + Entwicklung) ist ein SCP/FTP/SSH File Manager & Terminal fuer Android Foldables. Die App kombiniert einen Dual-Pane File Manager, einen vollwertigen SSH Terminal-Emulator, Proxmox-Integration und eine Wear OS Companion App. Primaere Zielplattform ist das Pixel Fold.

**Zielgruppe:** Entwickler, SysAdmins und DevOps-Engineers die ihre Server mobil verwalten -- insbesondere Nutzer von Claude Code ueber SSH.

---

## 2. Architektur & Modulstruktur

### 2.1 Multi-Module Gradle Projekt

```
OriDev/
├── app/                        # App Shell, Navigation Graph, DI Root, MainActivity
├── core/
│   ├── core-ui/               # Design System, Theme, Shared Compose Components
│   ├── core-network/          # SSH/FTP Client Abstraktionen (SSHJ, Commons Net)
│   ├── core-security/         # Android Keystore, EncryptedPrefs, BiometricManager
│   └── core-common/           # Kotlin Extensions, Result<T> Wrapper, Constants, Enums
├── domain/                     # Use Cases, Repository Interfaces, Domain Models
├── data/                       # Repository Implementations, Room DB, DAOs, Mappers
├── feature-filemanager/        # Dual-Pane File Manager (UI + ViewModel)
├── feature-terminal/           # SSH Terminal (UI + ViewModel + Custom Keyboard)
├── feature-connections/        # Connection Manager (UI + ViewModel)
├── feature-transfers/          # Transfer Queue (UI + ViewModel + WorkManager Workers)
├── feature-proxmox/            # Proxmox VM Manager (UI + ViewModel + REST Client)
├── feature-editor/             # Code Editor (UI + ViewModel, Sora-Editor Integration)
├── feature-settings/           # Settings + Premium/Paywall (UI + ViewModel)
└── wear/                       # Wear OS Companion App (eigenstaendiges APK)
```

### 2.2 Dependency Graph

```
app ──────► feature-* ──────► domain ──────► core-common
                │                 │
                │                 ├──────► core-security
                │                 │
                ▼                 ▼
            core-ui          core-network
                │
                ▼
           core-common

data ──────► domain + core-network + core-security + core-common

wear ──────► domain + core-common + core-security (abgespeckt)
```

**Regeln:**
- Feature-Module kennen sich NICHT gegenseitig (kein feature-A -> feature-B Import)
- Feature-Module kommunizieren ausschliesslich ueber Navigation mit Type-Safe Args
- `domain` hat KEINE Android-Dependencies (pure Kotlin)
- `data` implementiert Repository-Interfaces aus `domain`

### 2.3 Navigation

- **Framework:** Jetpack Navigation Compose mit Type-Safe Args (Kotlin Serialization)
- **Bottom Navigation Bar:** Connections | File Manager | Terminal | Transfers | Settings
- **Deep Links:** `oridev://connect/{profileId}`, `oridev://terminal/{profileId}`, `oridev://transfer/{transferId}`
- **Nested Navigation Graphs:** Jedes Feature-Modul definiert seinen eigenen NavGraph

### 2.4 MVVM + Clean Architecture Layers

```
Presentation (Compose UI + ViewModel)
    │ StateFlow<UiState> / events
    ▼
Domain (Use Cases + Repository Interfaces)
    │ suspend fun / Flow<T>
    ▼
Data (Repository Impl + Room + Network Clients)
    │ SSHJ / Commons Net / Proxmox REST
    ▼
External Systems (SSH Servers, FTP Servers, Proxmox API)
```

- **ViewModels** exponieren `StateFlow<UiState>` (kein LiveData)
- **Use Cases** sind single-purpose Klassen mit `operator fun invoke()`
- **Repositories** geben `Flow<T>` fuer Streams und `Result<T>` fuer One-Shot Operationen zurueck
- **Fehler** werden als sealed classes modelliert: `sealed class AppError`

---

## 3. Tech-Stack Matrix

| Library | Version | Zweck | Module | Gradle Dependency |
|---------|---------|-------|--------|-------------------|
| Kotlin | 2.1.x | Sprache | alle | `org.jetbrains.kotlin:kotlin-stdlib` |
| Compose BOM | 2025.04.x | UI Framework | core-ui, feature-* | `androidx.compose:compose-bom` |
| Material 3 | 1.4.x | Design System | core-ui | `androidx.compose.material3:material3` |
| Navigation Compose | 2.9.x | Navigation | app, feature-* | `androidx.navigation:navigation-compose` |
| Hilt | 2.54.x | Dependency Injection | alle | `com.google.dagger:hilt-android` |
| Room | 2.7.x | Lokale DB | data | `androidx.room:room-runtime` / `room-ktx` |
| SSHJ | 0.40.x | SSH/SCP/SFTP | core-network | `com.hierynomus:sshj` |
| Apache Commons Net | 3.11.x | FTP/FTPS | core-network | `commons-net:commons-net` |
| Termux Terminal | 0.118.x | Terminal Emulation | feature-terminal | `com.termux:terminal-emulator` / `terminal-view` |
| Sora Editor | 0.24.x | Code Editor | feature-editor | `io.github.Rosemoe.sora-editor:editor` |
| WindowManager | 1.4.x | Foldable Support | app, core-ui | `androidx.window:window` |
| WorkManager | 2.10.x | Background Transfers | feature-transfers | `androidx.work:work-runtime-ktx` |
| DataStore | 1.1.x | Preferences | core-security | `androidx.datastore:datastore-preferences` |
| Biometric | 1.4.x | Biometrie-Auth | core-security | `androidx.biometric:biometric` |
| Security Crypto | 1.1.0-alpha07 | EncryptedSharedPrefs | core-security | `androidx.security:security-crypto` |
| OkHttp | 4.12.x | Proxmox REST API | feature-proxmox | `com.squareup.okhttp3:okhttp` |
| Moshi | 1.16.x | JSON Parsing | feature-proxmox, data | `com.squareup.moshi:moshi-kotlin` |
| Coil | 3.1.x | Image Loading | core-ui | `io.coil-kt.coil3:coil-compose` |
| Play Billing | 7.1.x | In-App Purchases | feature-settings | `com.android.billingclient:billing-ktx` |
| AdMob | 23.6.x | Werbung | app | `com.google.android.gms:play-services-ads` |
| Wear Compose | 1.5.x | Wear OS UI | wear | `androidx.wear.compose:compose-material3` |
| Wear Data Layer | 19.x | Watch-Phone Sync | app, wear | `com.google.android.gms:play-services-wearable` |
| JUnit 5 | 5.11.x | Unit Tests | alle | `org.junit.jupiter:junit-jupiter` |
| MockK | 1.13.x | Mocking | alle | `io.mockk:mockk` |
| Turbine | 1.2.x | Flow Testing | alle | `app.cash.turbine:turbine` |
| Compose UI Test | (BOM) | UI Tests | feature-* | `androidx.compose.ui:ui-test-junit4` |
| detekt | 1.23.x | Static Analysis | alle (Plugin) | `io.gitlab.arturbosch.detekt` |
| ktlint | 1.5.x | Code Formatting | alle (via detekt) | `io.gitlab.arturbosch.detekt:detekt-formatting` |

**Min SDK:** 34 (Android 14)
**Target SDK:** 36 (Android 16)
**Compile SDK:** 36

---

## 4. Datenmodell

### 4.1 Room Entities

#### ServerProfile

```kotlin
@Entity(tableName = "server_profiles")
data class ServerProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: Protocol,
    val username: String,
    val authMethod: AuthMethod,
    val credentialRef: String,       // Android Keystore alias
    val sshKeyType: SshKeyType?,     // ED25519, RSA
    val startupCommand: String?,
    val projectDirectory: String?,
    val claudeCodeModel: String?,
    val claudeMdPath: String?,
    val isFavorite: Boolean = false,
    val lastConnected: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

enum class Protocol { SSH, SFTP, SCP, FTP, FTPS, PROXMOX }
enum class AuthMethod { PASSWORD, SSH_KEY, KEY_AGENT }
enum class SshKeyType { ED25519, RSA }
```

#### TransferRecord

```kotlin
@Entity(
    tableName = "transfer_records",
    foreignKeys = [ForeignKey(
        entity = ServerProfile::class,
        parentColumns = ["id"],
        childColumns = ["serverProfileId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TransferRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long,
    val sourcePath: String,
    val destinationPath: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val totalBytes: Long,
    val transferredBytes: Long = 0,
    val fileCount: Int = 1,
    val filesTransferred: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)

enum class TransferDirection { UPLOAD, DOWNLOAD }
enum class TransferStatus { QUEUED, ACTIVE, PAUSED, COMPLETED, FAILED }
```

#### Bookmark

```kotlin
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long?,  // null = lokales Dateisystem
    val path: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

#### CommandSnippet

```kotlin
@Entity(tableName = "command_snippets")
data class CommandSnippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long?,  // null = global
    val name: String,
    val command: String,
    val category: String,
    val isWatchQuickCommand: Boolean = false,
    val sortOrder: Int = 0
)
```

#### SessionLog

```kotlin
@Entity(
    tableName = "session_logs",
    foreignKeys = [ForeignKey(
        entity = ServerProfile::class,
        parentColumns = ["id"],
        childColumns = ["serverProfileId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SessionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val logFilePath: String  // Pfad zur .md Datei im internen Speicher
)
```

#### ProxmoxNode

```kotlin
@Entity(tableName = "proxmox_nodes")
data class ProxmoxNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 8006,
    val tokenId: String,          // user@realm!tokenname
    val tokenSecretRef: String,   // Keystore alias
    val certFingerprint: String?, // SHA-256 fuer Self-Signed Cert Pinning
    val lastSyncAt: Long? = null
)
```

#### KnownHost (SSH Host Key Verification)

```kotlin
@Entity(
    tableName = "known_hosts",
    indices = [Index(value = ["host", "port"], unique = true)]
)
data class KnownHost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val keyType: String,      // ssh-ed25519, ssh-rsa, ecdsa-sha2-nistp256
    val fingerprint: String,  // SHA-256 Base64
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)
```

### 4.2 DAOs

```kotlin
@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY sortOrder, name")
    fun getAll(): Flow<List<ServerProfile>>

    @Query("SELECT * FROM server_profiles WHERE isFavorite = 1")
    fun getFavorites(): Flow<List<ServerProfile>>

    @Query("SELECT * FROM server_profiles WHERE id = :id")
    suspend fun getById(id: Long): ServerProfile?

    @Query("SELECT COUNT(*) FROM server_profiles")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(profile: ServerProfile): Long

    @Update
    suspend fun update(profile: ServerProfile)

    @Delete
    suspend fun delete(profile: ServerProfile)

    @Query("UPDATE server_profiles SET lastConnected = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface TransferRecordDao {
    @Query("SELECT * FROM transfer_records ORDER BY startedAt DESC")
    fun getAll(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfer_records WHERE status IN ('QUEUED', 'ACTIVE', 'PAUSED')")
    fun getActive(): Flow<List<TransferRecord>>

    @Insert
    suspend fun insert(record: TransferRecord): Long

    @Update
    suspend fun update(record: TransferRecord)

    @Query("DELETE FROM transfer_records WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY label")
    fun getAll(): Flow<List<Bookmark>>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)
}

@Dao
interface CommandSnippetDao {
    @Query("SELECT * FROM command_snippets WHERE serverProfileId = :serverId OR serverProfileId IS NULL ORDER BY sortOrder")
    fun getForServer(serverId: Long?): Flow<List<CommandSnippet>>

    @Query("SELECT * FROM command_snippets WHERE isWatchQuickCommand = 1")
    fun getWatchCommands(): Flow<List<CommandSnippet>>

    @Insert
    suspend fun insert(snippet: CommandSnippet): Long

    @Update
    suspend fun update(snippet: CommandSnippet)

    @Delete
    suspend fun delete(snippet: CommandSnippet)
}

@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs WHERE serverProfileId = :serverId ORDER BY startedAt DESC")
    fun getForServer(serverId: Long): Flow<List<SessionLog>>

    @Insert
    suspend fun insert(log: SessionLog): Long

    @Update
    suspend fun update(log: SessionLog)
}

@Dao
interface ProxmoxNodeDao {
    @Query("SELECT * FROM proxmox_nodes ORDER BY name")
    fun getAll(): Flow<List<ProxmoxNode>>

    @Query("SELECT * FROM proxmox_nodes WHERE id = :id")
    suspend fun getById(id: Long): ProxmoxNode?

    @Insert
    suspend fun insert(node: ProxmoxNode): Long

    @Update
    suspend fun update(node: ProxmoxNode)

    @Delete
    suspend fun delete(node: ProxmoxNode)
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun find(host: String, port: Int): KnownHost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(knownHost: KnownHost)

    @Delete
    suspend fun delete(knownHost: KnownHost)

    @Query("SELECT * FROM known_hosts ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<KnownHost>>
}
```

### 4.3 Room Database

```kotlin
@Database(
    entities = [
        ServerProfile::class,
        TransferRecord::class,
        Bookmark::class,
        CommandSnippet::class,
        SessionLog::class,
        ProxmoxNode::class,
        KnownHost::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OriDevDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun transferRecordDao(): TransferRecordDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun commandSnippetDao(): CommandSnippetDao
    abstract fun sessionLogDao(): SessionLogDao
    abstract fun proxmoxNodeDao(): ProxmoxNodeDao
    abstract fun knownHostDao(): KnownHostDao
}
```

### 4.4 Migrations-Strategie

- Schema-Export aktiviert (`exportSchema = true`) fuer automatisierte Migration-Tests
- `AutoMigration` fuer additive Aenderungen (neue Spalten mit Default)
- Manuelle `Migration(N, N+1)` fuer destruktive Aenderungen
- Fallback: `fallbackToDestructiveMigration()` NUR in Debug-Builds
- Jede Migration hat einen eigenen Unit-Test mit `MigrationTestHelper`

---

## 5. Security-Konzept

### 5.1 Credentials-Flow

1. User gibt Passwort oder importiert SSH-Key bei Server-Erstellung
2. `KeyStoreManager` generiert einen AES-256-GCM Schluessel im Android Keystore (hardware-backed wenn verfuegbar)
3. Credentials werden mit diesem Schluessel verschluesselt und als `EncryptedSharedPreferences` Entry gespeichert
4. `ServerProfile.credentialRef` speichert NUR den Keystore-Alias, nie das Passwort
5. Bei Verbindungsaufbau: Keystore Entry entschluesseln, als `char[]` an SSHJ uebergeben, nach Auth `Arrays.fill(charArray, '\u0000')` ausfuehren
6. Passwoerter werden NIE als `String` gehalten (GC-Timing auf JVM macht String-Cleanup unzuverlaessig)

### 5.2 SSH Key Storage

- Private Keys werden als verschluesselte Byte-Arrays im Keystore gespeichert (NICHT als Dateien im Filesystem)
- Import-Flow: User waehlt `.pem`/`.pub` File via SAF (Storage Access Framework) -> Key parsen -> in Keystore schreiben -> Originaldatei wird NICHT kopiert oder cached
- Unterstuetzte Key-Typen: Ed25519 (bevorzugt, default bei Generierung), RSA (2048+ Bit)
- In-App Key-Generierung: Ed25519 Keys koennen direkt generiert werden, Public Key wird zum Export angeboten

### 5.3 SSH Host Key Verification (TOFU)

1. Beim ersten Connect zu einem unbekannten Host: Dialog zeigt Host Key Fingerprint (SHA-256, Base64)
2. User muss explizit "Trust this host" bestaetigen
3. Host Key wird als `KnownHost` Entity in Room gespeichert
4. Bei jedem weiteren Connect: SSHJ `HostKeyVerifier` prueft gegen gespeicherten Fingerprint
5. Bei Key-Mismatch: Verbindung wird ABGELEHNT, Warndialog "HOST KEY HAS CHANGED - possible MITM attack"
6. User kann manuell den neuen Key akzeptieren (mit expliziter Bestaetigung)

### 5.4 Proxmox API Token Handling

- Token ID (`user@realm!tokenname`) wird im Klartext in `ProxmoxNode.tokenId` gespeichert
- Token Secret wird verschluesselt im Keystore gespeichert, `ProxmoxNode.tokenSecretRef` haelt den Alias
- API-Calls: HTTPS mit Header `Authorization: PVEAPIToken=<tokenId>=<secret>`
- Self-Signed Certificate Handling:
  1. Beim ersten Connect: Certificate-Details und SHA-256 Fingerprint dem User anzeigen
  2. User bestaetigt -> Fingerprint wird in `ProxmoxNode.certFingerprint` gespeichert
  3. Custom `X509TrustManager` in OkHttp der gegen gespeicherten Fingerprint prueft
  4. Bei Fingerprint-Mismatch: Verbindung ablehnen, Warndialog

### 5.5 Biometrie-Unlock (Premium)

- `BiometricPrompt` API mit `CryptoObject` (AES Key aus Keystore)
- Keystore Master-Key: `setUserAuthenticationRequired(true)`, `setUserAuthenticationValidityDurationSeconds(0)` (jede Nutzung erfordert Auth)
- Fallback-Chain: Biometrie -> Device PIN/Pattern -> App-internes Passwort
- Auto-Lock nach konfigurierbarem Timeout (Default: 5 Min, Optionen: 1/5/15/30 Min, Nie)
- Beim App-Start pruefen ob Device Lockscreen aktiv ist. Ohne Lockscreen: Warnung "Biometric unlock requires a device lock screen" und Feature deaktivieren

### 5.6 Clipboard-Sicherheit

- Terminal Copy setzt `ClipDescription.EXTRA_IS_SENSITIVE = true` (maskiert Android 13+ Clipboard Preview)
- Auto-Clear nach konfigurierbarem Timeout (Default: 30s, Optionen: 15s/30s/60s/Nie)
- Paste-Bestaetigung bei mehrzeiligen Inhalten: Dialog "You are about to paste N lines. This may execute multiple commands. Continue?"
- Kein Clipboard-Logging in Session Recorder (sensible Daten schuetzen)

### 5.7 Watch 2FA-Flow

1. Feature ist optional und pro Server-Profil konfigurierbar (`require2fa: Boolean`)
2. User startet Verbindung auf Foldable
3. Foldable sendet Auth-Request via `MessageClient` an Watch: `{action: "2fa_request", server: "name", host: "..."}`
4. Watch zeigt Dialog: "Verbindung zu server01 erlauben?" mit Approve/Deny
5. Watch sendet Response via `MessageClient`: `{action: "2fa_response", approved: true/false}`
6. Timeout: 30 Sekunden. Bei Timeout oder Watch nicht erreichbar -> Fallback auf Device-Biometrie
7. Falls Biometrie nicht verfuegbar -> Verbindung wird abgelehnt mit Hinweis

### 5.8 Netzwerk-Sicherheit

- Alle Verbindungen laufen ueber verschluesselte Protokolle (SSH, SFTP, FTPS)
- Unverschluesseltes FTP: Warndialog "This connection is not encrypted. Credentials will be sent in plaintext. Continue?"
- NetworkSecurityConfig: `cleartextTrafficPermitted="false"` (Default), Ausnahme nur fuer explizites FTP
- Certificate Transparency Logs werden NICHT erzwungen (Self-Signed Certs bei Homelab-Servern ueblich)

---

## 6. Hintergrund-Betrieb & Foreground Service

### 6.1 ConnectionService

```
Foreground Service
Type: connectedDevice | dataSync
Lifecycle: Gestartet bei erster Verbindung, gestoppt bei letzter Trennung
```

- Haelt alle aktiven SSH/SFTP/FTP Sessions in einer `ConcurrentHashMap<Long, SshSession>`
- Persistent Notification (nicht abwischbar):
  - Title: "Ori:Dev"
  - Content: "3 Verbindungen aktiv | 2 Transfers laufen"
  - Actions: "Trennen" (alle), "Oeffnen"
  - Wird aktualisiert bei Statusaenderungen
- Bound Service: Activities binden sich fuer direkte Session-Interaktion
- Stoppt sich selbst wenn: alle Verbindungen getrennt UND kein Transfer aktiv UND kein aktives Terminal

### 6.2 TransferWorker (WorkManager)

- Jeder Transfer = ein `CoroutineWorker` mit eindeutiger Work-ID
- Fortschritt via `setProgress(workDataOf("transferred" to bytes, "total" to total))`
- UI beobachtet via `WorkManager.getWorkInfoByIdLiveData()` / `.asFlow()`
- Constraints: `NetworkType.CONNECTED`
- Retry-Policy: `BackoffPolicy.EXPONENTIAL`, initialDelay 2s, maxRetries 3
- Nach 3 Fehlversuchen: Transfer als FAILED markieren, Notification mit Retry-Action
- Pause: `WorkManager.cancelWorkById()` + TransferRecord.status = PAUSED + transferredBytes speichern
- Resume: Neuen Worker enqueuen mit Offset = transferredBytes (SFTP `readFrom(offset)`)

### 6.3 Wake Lock Strategie

- `PARTIAL_WAKE_LOCK` nur waehrend:
  - Aktivem Dateitransfer
  - Terminal-Session mit laufendem Befehl (Output wird empfangen)
- Automatisches Release nach 30s Idle (kein Terminal-Input, kein aktiver Transfer)
- KEIN Wake Lock fuer reine Idle-Verbindungen (SSH Keepalive laeuft ueber TCP Stack)
- Wake Lock Tag: `"oridev:transfer"`, `"oridev:terminal"`

### 6.4 Reconnect-Logik

- `ConnectivityManager.NetworkCallback` ueberwacht Netzwerk-Changes
- Bei Disconnect-Event:
  1. Sofort: Retry #1 (0s Delay)
  2. Nach 2s: Retry #2
  3. Nach 5s: Retry #3
  4. Nach 15s: Retry #4 (letzter Versuch)
  5. Danach: Notification "Verbindung zu server01 verloren" mit "Reconnect" Action
- SSH Keepalive: `ServerAliveInterval = 15s`, `ServerAliveCountMax = 3`
- Bei Netzwerkwechsel (WiFi -> Mobile): Alle Sessions reconnecten mit 1s Delay

### 6.5 Android 14+ Compliance

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />

<service
    android:name=".service.ConnectionService"
    android:foregroundServiceType="connectedDevice|dataSync"
    android:exported="false" />
```

### 6.6 Battery Optimization

- Beim ersten App-Start (Onboarding): Dialog erklaert warum Akku-Ausnahme noetig ist
- "Hintergrund-Verbindungen aktiv halten" -> `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Falls User ablehnt: Hinweis in Settings dass Verbindungen bei Inaktivitaet getrennt werden koennten
- Kein erneutes Nachfragen nach Ablehnung (Android Policy)

---

## 7. Wear OS Kommunikation

### 7.1 Data Layer (State-Sync)

```
DataClient.putDataItem("/connections/status") -> {
    connections: [{id, name, status, host}],
    activeTransfers: [{id, name, progress, speed}],
    lastCommand: {command, output, timestamp}
}
```

- Sync bei jeder Statusaenderung (Debounced, max 1x pro Sekunde)
- Watch liest via `DataClient.getDataItems()` und `onDataChanged()`

### 7.2 MessageClient (Commands)

```
Watch -> Phone: "/command/execute" -> {serverId, command}
Phone -> Watch: "/command/result" -> {output, exitCode}
Watch -> Phone: "/panic/disconnect-all"
Watch -> Phone: "/2fa/response" -> {requestId, approved}
Phone -> Watch: "/2fa/request" -> {requestId, serverName, host}
```

### 7.3 ChannelClient (Grosse Daten)

- Fuer Command-Output > 100KB
- Streaming statt einmaliger Transfer

### 7.4 Standalone-Modus

- Watch prueft via `NodeClient.getConnectedNodes()` ob Foldable erreichbar
- Falls nicht erreichbar UND Watch ist im WiFi:
  - Direkter SSH-Connect via SSHJ (abgespeckt, kein File Manager)
  - Nur Quick-Commands und Command-Output
  - Gespeicherte Credentials werden lokal auf der Watch gehalten (verschluesselt)

---

## 8. Phasen-Roadmap

### Phase 0: Projekt-Setup & CLAUDE.md (8 Tasks)

**Abhaengigkeiten:** Keine
**Ergebnis:** Kompilierbares Multi-Module Projekt mit CI/CD

- Task 0.1: Git Repository initialisieren, `.gitignore` fuer Android/Gradle erstellen
- Task 0.2: Root `build.gradle.kts` mit Version Catalog (`libs.versions.toml`) erstellen
- Task 0.3: Alle Module als leere Gradle-Module anlegen (app, core/*, domain, data, feature-*, wear)
- Task 0.4: `CLAUDE.md` im Projekt-Root erstellen (Konventionen, Architektur, Standards)
- Task 0.5: Hilt Setup (Root + alle Module), leere `@HiltAndroidApp` Application-Klasse
- Task 0.6: `detekt.yml` Konfiguration, ktlint via detekt-formatting Plugin
- Task 0.7: GitHub Actions Workflows erstellen (pr-check, build, release, dependabot)
- Task 0.8: Signing-Konfiguration (Debug Keystore, Release Keystore Platzhalter)
- **Verifikation:** `./gradlew assembleDebug` kompiliert, `./gradlew detekt` laeuft ohne Fehler

### Phase 1: Core-Module & Datenmodell (12 Tasks)

**Abhaengigkeiten:** Phase 0
**Ergebnis:** Room DB, Keystore-Abstraktion, SSHJ-Wrapper, Design System

- Task 1.1: `core-common` - Kotlin Extensions, Result<T> Sealed Class, Constants
- Task 1.2: `core-common` - Enum-Definitionen (Protocol, AuthMethod, TransferStatus, etc.)
- Task 1.3: `core-security` - `KeyStoreManager` Interface + Implementation (AES-256-GCM)
- Task 1.4: `core-security` - `EncryptedPrefsManager` fuer App-Settings
- Task 1.5: `core-security` - `BiometricManager` Wrapper (Premium-gated)
- Task 1.6: `data` - Room Entities (alle 7 Entities)
- Task 1.7: `data` - Room DAOs (alle 7 DAOs)
- Task 1.8: `data` - `OriDevDatabase` + TypeConverters + Hilt DatabaseModule
- Task 1.9: `core-network` - SSHJ Wrapper: `SshClient` Interface + Implementation
- Task 1.10: `core-network` - FTP/FTPS Wrapper: `FtpClient` Interface + Implementation
- Task 1.11: `core-ui` - Material 3 Theme (Colors, Typography, Shapes), Dark/Light
- Task 1.12: `core-ui` - Shared Components (LoadingIndicator, ErrorView, StatusBadge, OriDevTopBar)
- **Verifikation:** `./gradlew test` (Unit Tests fuer Keystore, Room DAOs mit in-memory DB), `./gradlew assembleDebug`

### Phase 2: Connection Manager (10 Tasks)

**Abhaengigkeiten:** Phase 1
**Ergebnis:** Server-Profile CRUD, Connect/Disconnect, Host Key Verification

- Task 2.1: `domain` - `ServerProfile` Domain Model, `ConnectionRepository` Interface
- Task 2.2: `domain` - Use Cases: `GetConnectionsUseCase`, `ConnectUseCase`, `DisconnectUseCase`, `SaveProfileUseCase`
- Task 2.3: `data` - `ConnectionRepositoryImpl` (Room + Keystore + SSHJ)
- Task 2.4: `data` - `KnownHostRepository` + TOFU Host Key Verification Logic
- Task 2.5: `feature-connections` - `ConnectionListScreen` Compose UI (Server-Karten, Status-Badges)
- Task 2.6: `feature-connections` - `ConnectionListViewModel` (StateFlow<UiState>, Event-Handling)
- Task 2.7: `feature-connections` - `AddEditConnectionScreen` (Formular, Validierung)
- Task 2.8: `feature-connections` - `ConnectionDetailBottomSheet` (Details, Actions)
- Task 2.9: `feature-connections` - Host Key Verification Dialog
- Task 2.10: `app` - Navigation Setup, Bottom Navigation Bar, ConnectionService Grundgeruest
- **Verifikation:** `./gradlew test` (Use Case Tests, ViewModel Tests), `./gradlew assembleDebug`, App startet und zeigt Connection Manager

### Phase 3: Dual-Pane File Manager (14 Tasks)

**Abhaengigkeiten:** Phase 2
**Ergebnis:** Funktionaler File Manager mit Local + Remote Browsing

- Task 3.1: `domain` - `FileItem` Domain Model, `FileSystemRepository` Interface
- Task 3.2: `domain` - Use Cases: `ListFilesUseCase`, `DeleteFileUseCase`, `RenameFileUseCase`, `CreateDirectoryUseCase`, `ChmodUseCase`
- Task 3.3: `data` - `LocalFileSystemRepository` (Android Storage Access)
- Task 3.4: `data` - `RemoteFileSystemRepository` (SFTP via SSHJ, FTP via Commons Net)
- Task 3.5: `feature-filemanager` - `DualPaneLayout` Composable mit draggable Divider
- Task 3.6: `feature-filemanager` - `FileListPane` Composable (List/Grid View, Sort, Select)
- Task 3.7: `feature-filemanager` - `FileListViewModel` (Navigation-Stack, Selection-State)
- Task 3.8: `feature-filemanager` - Git-Status Badges (`.git` Verzeichnis parsen, modified/staged/untracked)
- Task 3.9: `feature-filemanager` - File-Info Bottom Sheet (Properties, Preview)
- Task 3.10: `feature-filemanager` - Context Menu (Long-Press: Copy, Cut, Paste, Rename, Delete, Chmod)
- Task 3.11: `feature-filemanager` - Breadcrumb Navigation mit Tap-to-Navigate
- Task 3.12: `feature-filemanager` - Foldable Awareness: WindowSizeClass Detection, Folded -> Single Pane + Tab Bar
- Task 3.13: `feature-filemanager` - Bookmark-Integration (Favoriten speichern/laden)
- Task 3.14: `feature-filemanager` - Drag & Drop zwischen Panels (Initiiert Transfer)
- **Verifikation:** `./gradlew test`, `./gradlew assembleDebug`, Dual-Pane navigiert lokal + remote, Fold-Toggle funktioniert

### Phase 4: SSH Terminal (12 Tasks)

**Abhaengigkeiten:** Phase 2
**Ergebnis:** Funktionaler SSH Terminal mit Custom Keyboard

- Task 4.1: `feature-terminal` - Termux `TerminalView` als Compose `AndroidView` wrappen
- Task 4.2: `feature-terminal` - `TerminalViewModel` (Session-Management, Multi-Tab State)
- Task 4.3: `feature-terminal` - SSH Channel Bridge: SSHJ Shell Channel -> Termux TerminalEmulator InputStream/OutputStream
- Task 4.4: `feature-terminal` - Multi-Tab UI (Tab Bar, Add/Close/Switch Tabs)
- Task 4.5: `feature-terminal` - Custom Soft Keyboard Composable (Esc, F-Keys, Ctrl, Alt, Arrows, Symbols)
- Task 4.6: `feature-terminal` - Landscape Layout: Draggable Split (Terminal oben 60%, Keyboard unten 40%)
- Task 4.7: `feature-terminal` - Portrait Layout: Terminal Fullscreen, Keyboard als Bottom Sheet
- Task 4.8: `feature-terminal` - Copy & Paste: Long-Press Selection, Floating Toolbar, Paste-Bestaetigung bei Multiline
- Task 4.9: `feature-terminal` - Clipboard-Historie (letzte 10 kopierte Eintraege, `EXTRA_IS_SENSITIVE`)
- Task 4.10: `feature-terminal` - Foldable Awareness: Auto-Rotation, Layout-Switch bei Fold/Unfold
- Task 4.11: `feature-terminal` - ConnectionService Integration: Terminal-Session via Bound Service
- Task 4.12: `feature-terminal` - Terminal Preferences: Font, Font-Size, Color Scheme, Bell-Modus
- **Verifikation:** `./gradlew test`, `./gradlew assembleDebug`, SSH-Verbindung + Terminal-Eingabe + Custom Keyboard funktioniert

### Phase 5: Transfer Queue (10 Tasks)

**Abhaengigkeiten:** Phase 3
**Ergebnis:** Hintergrund-Transfers mit Fortschritt, Pause/Resume, Retry

- Task 5.1: `domain` - `TransferRequest` Domain Model, `TransferRepository` Interface
- Task 5.2: `domain` - Use Cases: `EnqueueTransferUseCase`, `PauseTransferUseCase`, `ResumeTransferUseCase`, `CancelTransferUseCase`
- Task 5.3: `feature-transfers` - `TransferWorker` (CoroutineWorker, SFTP Upload/Download mit Progress-Reporting)
- Task 5.4: `feature-transfers` - FTP TransferWorker Variante (Commons Net)
- Task 5.5: `feature-transfers` - Multi-File Transfer Logik (Directory-Traversal, File-Count Progress)
- Task 5.6: `feature-transfers` - `TransferQueueScreen` Compose UI (Active/Completed/Failed Sections)
- Task 5.7: `feature-transfers` - `TransferQueueViewModel` (WorkManager Observation, Filter-State)
- Task 5.8: `feature-transfers` - Notification-Channel fuer Transfer-Fortschritt + Completion
- Task 5.9: `feature-transfers` - Resume-Logik: SFTP `readFrom(offset)` / FTP `REST` Command
- Task 5.10: `feature-transfers` - Wake Lock Integration + ConnectionService Binding
- **Verifikation:** `./gradlew test`, `./gradlew assembleDebug`, Transfer starten/pausieren/fortsetzen funktioniert

### Phase 6: Code Editor & Claude Code Integration (12 Tasks)

**Abhaengigkeiten:** Phase 3, Phase 4
**Ergebnis:** Integrierter Code Editor, Claude Code Features im Terminal + File Manager

- Task 6.1: `feature-editor` - Sora-Editor als Compose `AndroidView` wrappen
- Task 6.2: `feature-editor` - TextMate Grammar Loading fuer alle Zielsprachen (Kotlin, PHP, Python, JS/TS, Bash, YAML, JSON, XML, Markdown)
- Task 6.3: `feature-editor` - `CodeEditorScreen` UI (Tab Bar, Toolbar, Search & Replace)
- Task 6.4: `feature-editor` - `CodeEditorViewModel` (File Loading via SFTP, Dirty-State, Save)
- Task 6.5: `feature-editor` - Git Diff Gutter (Added/Deleted/Modified Lines farbig markieren)
- Task 6.6: `feature-terminal` - Session Recorder: Terminal-Output als Markdown/Log exportieren (Premium)
- Task 6.7: `feature-terminal` - "Send to Claude" Output-Selektor: Markierten Text an Claude API senden (Premium)
- Task 6.8: `feature-terminal` - Snippet Manager: Quick-Actions fuer gespeicherte Befehle
- Task 6.9: `feature-terminal` - Auto-Detect Code Blocks: Regex-basierte Erkennung von Code-Fences in Terminal-Output
- Task 6.10: `feature-terminal` - Inline Diff-Viewer: Side-by-Side Diff fuer erkannte Dateiänderungen
- Task 6.11: `feature-filemanager` - Code Preview mit Syntax Highlighting (Read-Only Sora-Editor)
- Task 6.12: `feature-filemanager` - Git Status Overlay: `.git/status` parsen, Badges in Dateiliste
- **Verifikation:** `./gradlew test`, `./gradlew assembleDebug`, Code Editor oeffnet/bearbeitet/speichert Dateien

### Phase 7: Proxmox VM Manager (10 Tasks)

**Abhaengigkeiten:** Phase 2
**Ergebnis:** VM-Liste, VM-Erstellung aus Templates, Lifecycle-Management

- Task 7.1: `feature-proxmox` - Proxmox REST API Client (OkHttp + Moshi, Token-Auth)
- Task 7.2: `feature-proxmox` - API Models: Node, VM, Template, Task als Data Classes
- Task 7.3: `domain` - Proxmox Use Cases: `GetNodesUseCase`, `GetVmsUseCase`, `CloneVmUseCase`, `ControlVmUseCase`
- Task 7.4: `feature-proxmox` - `ProxmoxDashboardScreen` UI (Node-Karten, VM-Liste)
- Task 7.5: `feature-proxmox` - `ProxmoxDashboardViewModel`
- Task 7.6: `feature-proxmox` - VM-Card Composable (Status, Ressourcen-Bars, Actions)
- Task 7.7: `feature-proxmox` - Create VM Wizard (4-Step: Template -> Config -> Network -> Review)
- Task 7.8: `feature-proxmox` - Auto-Connect Flow: Nach Clone+Start SSH-Port pollen -> Terminal oeffnen
- Task 7.9: `feature-proxmox` - Self-Signed Certificate Pinning Dialog + TrustManager
- Task 7.10: `feature-proxmox` - VM Lifecycle Actions (Start/Stop/Restart/Delete mit Confirmation)
- **Verifikation:** `./gradlew test`, `./gradlew assembleDebug`, API-Calls gegen Mock-Server, VM-Liste rendert

### Phase 8: Wear OS Companion (14 Tasks)

**Abhaengigkeiten:** Phase 2, Phase 5
**Ergebnis:** Wear OS App mit Monitoring, Quick-Commands, 2FA

- Task 8.1: `wear` - Gradle Module Setup (Wear OS Dependencies, Compose Material 3 Wear)
- Task 8.2: `wear` - Main Activity + Wear Navigation (SwipeDismiss, Horologist)
- Task 8.3: `wear` - Data Layer Setup: `DataClient` Listener fuer Phone-Sync
- Task 8.4: `wear` - Connection Status Screen (Server-Liste, Connect/Disconnect)
- Task 8.5: `wear` - Transfer Monitor Screen (Circular Progress, Pause/Cancel)
- Task 8.6: `wear` - Quick Commands Screen (Command-Buttons, Server-Selector)
- Task 8.7: `wear` - Command Output Screen (Monospace Text, Scrollable)
- Task 8.8: `wear` - Server Health Dashboard (SVG Circular Gauges fuer CPU/RAM/Disk)
- Task 8.9: `wear` - Panic Button Screen (2-Tap Confirmation, Disconnect All)
- Task 8.10: `wear` - Claude Code Notification (Approve/Reject Buttons)
- Task 8.11: `wear` - 2FA Dialog + MessageClient Response
- Task 8.12: `wear` - Tiles: Main Tile (Connections, Transfers, Last Command)
- Task 8.13: `wear` - Complications: Connection Count, Transfer Status, Server Health
- Task 8.14: `wear` - Standalone WiFi SSH Mode (Fallback wenn Phone nicht erreichbar)
- **Verifikation:** `./gradlew :wear:assembleDebug`, Wear Emulator zeigt Screens, Data Layer Sync funktioniert

### Phase 9: Monetarisierung (8 Tasks)

**Abhaengigkeiten:** Phase 6
**Ergebnis:** Free/Premium Tiers, AdMob, Play Billing, Feature-Gates

- Task 9.1: `feature-settings` - `PremiumManager` (BillingClient Setup, Purchase Flow)
- Task 9.2: `feature-settings` - Subscription + One-Time Purchase Handling (verify server-side optional)
- Task 9.3: `feature-settings` - 7-Tage Trial Logik (Trial-Start in EncryptedPrefs, Expiry-Check)
- Task 9.4: `feature-settings` - Premium/Paywall Screen UI (Feature-Vergleich, Pricing Cards)
- Task 9.5: `feature-settings` - `FeatureGateManager`: Prueft Premium-Status fuer gated Features
- Task 9.6: `app` - AdMob Integration: Banner-Ads NUR in Idle-Screens (Connection Manager, File List, Settings)
- Task 9.7: `app` - Ad-Regeln: KEINE Ads im Terminal, KEINE Ads waehrend Transfers, KEINE interstitials
- Task 9.8: `feature-settings` - Settings Screen UI (alle Sections, Toggles, Premium-Locks)
- **Verifikation:** `./gradlew test`, `./gradlew assembleDebug`, Feature-Gates blockieren korrekt, Ads erscheinen nur in erlaubten Screens

### Phase 10: CI/CD & Polish (8 Tasks)

**Abhaengigkeiten:** Phase 9
**Ergebnis:** Vollstaendige CI/CD Pipeline, Release-Ready App

- Task 10.1: GitHub Actions PR-Check verfeinern (Lint, Unit Tests, UI Tests auf Emulator)
- Task 10.2: GitHub Actions Release Workflow (Tag-basiert, signierter AAB, GitHub Release)
- Task 10.3: Proguard/R8 Regeln fuer alle Libraries (SSHJ, Moshi, Termux, Sora-Editor)
- Task 10.4: App-Onboarding Flow (Welcome, Permission-Requests, Battery-Optimization, optional Biometrie)
- Task 10.5: Accessibility: Content Descriptions, TalkBack Support, Minimum Touch Targets 48dp
- Task 10.6: Performance: Baseline Profile generieren, Startup Tracing, Memory Leak Check (LeakCanary Debug)
- Task 10.7: Crash Reporting Setup (Firebase Crashlytics oder alternative)
- Task 10.8: Store Listing Assets: Screenshots (Phone + Foldable + Watch), Feature Graphic, Beschreibung
- **Verifikation:** `./gradlew assembleRelease` produziert signierten AAB, CI Pipeline gruen, alle Tests bestehen

---

## 9. CI/CD Pipeline

### 9.1 `.github/workflows/pr-check.yml`

```yaml
name: PR Check

on:
  pull_request:
    branches: [main, develop]

concurrency:
  group: pr-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew detekt
      - run: ./gradlew lint

  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew test
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: test-results
          path: '**/build/reports/tests/'

  ui-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          profile: pixel_fold
          script: ./gradlew connectedCheck
```

### 9.2 `.github/workflows/build.yml`

```yaml
name: Build

on:
  push:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        variant: [debug, release]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode Keystore
        if: matrix.variant == 'release'
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > app/release.keystore
      - run: ./gradlew assemble${{ matrix.variant == 'release' && 'Release' || 'Debug' }}
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
      - uses: actions/upload-artifact@v4
        with:
          name: apk-${{ matrix.variant }}
          path: app/build/outputs/apk/${{ matrix.variant }}/*.apk
```

### 9.3 `.github/workflows/release.yml`

```yaml
name: Release

on:
  push:
    tags: ['v*']

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode Keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > app/release.keystore
      - run: ./gradlew bundleRelease assembleRelease
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
      - name: Generate Changelog
        id: changelog
        run: |
          PREV_TAG=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")
          if [ -n "$PREV_TAG" ]; then
            echo "changelog<<EOF" >> $GITHUB_OUTPUT
            git log --pretty=format:"- %s" $PREV_TAG..HEAD >> $GITHUB_OUTPUT
            echo "EOF" >> $GITHUB_OUTPUT
          else
            echo "changelog=Initial release" >> $GITHUB_OUTPUT
          fi
      - uses: softprops/action-gh-release@v2
        with:
          body: ${{ steps.changelog.outputs.changelog }}
          files: |
            app/build/outputs/bundle/release/*.aab
            app/build/outputs/apk/release/*.apk
```

### 9.4 `.github/dependabot.yml`

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
      day: monday
    open-pull-requests-limit: 10
    labels:
      - dependencies
    groups:
      compose:
        patterns:
          - "androidx.compose*"
      kotlin:
        patterns:
          - "org.jetbrains.kotlin*"

  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    labels:
      - ci
```

### 9.5 Benoetigte GitHub Secrets

| Secret | Beschreibung |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded Release Keystore |
| `KEYSTORE_PASSWORD` | Keystore Passwort |
| `KEY_ALIAS` | Signing Key Alias |
| `KEY_PASSWORD` | Signing Key Passwort |

---

## 10. Testplan

### 10.1 Pro Modul

**core-common:**
- `ResultTest` - Erfolg/Fehler Mapping, fold(), getOrNull()
- `ExtensionsTest` - String/Long/File Extensions

**core-security:**
- `KeyStoreManagerTest` - Encrypt/Decrypt Roundtrip, verschiedene Key-Typen
- `BiometricManagerTest` - Availability Check, Fallback-Chain (Instrumented Test)

**core-network:**
- `SshClientTest` - Connect, Execute, Disconnect (MockK-basierter SSHJ Mock)
- `FtpClientTest` - Connect, List, Upload, Download (MockK)
- `HostKeyVerifierTest` - TOFU Accept, Known Host Match, Known Host Mismatch Reject

**data:**
- `ServerProfileDaoTest` - CRUD, getCount(), Favorites Query (Room In-Memory DB)
- `TransferRecordDaoTest` - Insert, Update Status, getActive Filter, clearCompleted
- `ConnectionRepositoryImplTest` - Connect-Flow mit gemocktem SshClient + KeyStore

**domain:**
- `ConnectUseCaseTest` - Success Path, Auth Failure, Network Error, Host Key Mismatch
- `ListFilesUseCaseTest` - Local Files, Remote Files, Error Handling
- `EnqueueTransferUseCaseTest` - Single File, Directory, Queue Limit (Free Tier)

**feature-connections:**
- `ConnectionListViewModelTest` - Load Profiles, Connect/Disconnect State Transitions (Turbine)
- `AddEditConnectionScreenTest` - Form Validation, Save Flow (Compose UI Test)

**feature-filemanager:**
- `FileListViewModelTest` - Navigation Stack, Selection State, Sort/Filter (Turbine)
- `DualPaneLayoutTest` - Fold State Changes, Divider Drag (Compose UI Test)

**feature-terminal:**
- `TerminalViewModelTest` - Session Create/Close, Multi-Tab State (Turbine)
- `CustomKeyboardTest` - Key Press Events, Modifier Keys (Compose UI Test)

**feature-transfers:**
- `TransferWorkerTest` - Progress Reporting, Retry on Failure, Resume with Offset (WorkManager Testing)
- `TransferQueueViewModelTest` - Filter State, WorkInfo Observation (Turbine)

**feature-proxmox:**
- `ProxmoxApiClientTest` - API Response Parsing, Token Auth Header, Error Handling (MockWebServer)
- `CloneVmUseCaseTest` - Template Selection, Clone Request, Poll SSH Port

**feature-editor:**
- `CodeEditorViewModelTest` - File Load, Dirty State, Save Flow (Turbine)

**feature-settings:**
- `PremiumManagerTest` - Purchase Flow, Trial Expiry, Feature Gate Check
- `FeatureGateManagerTest` - Free Tier Limits, Premium Unlock

**wear:**
- `DataLayerSyncTest` - State Sync, Command Messaging (MockK)
- `PanicButtonTest` - 2-Tap Confirmation, Disconnect All Message

### 10.2 Test-Konventionen

- Naming: `methodUnderTest_condition_expectedResult` (z.B. `connect_withInvalidHost_returnsNetworkError`)
- AAA Pattern: Arrange / Act / Assert
- Ein Assert pro Test (Ausnahme: State-Objekte mit mehreren Properties)
- MockK fuer alle externen Dependencies
- Turbine fuer Flow-Testing mit expliziten `awaitItem()` Assertions
- Room DAOs testen mit `Room.inMemoryDatabaseBuilder()`
- WorkManager testen mit `TestListenableWorkerBuilder`

---

## 11. Monetarisierungs-Implementierung

### 11.1 Feature-Gate Matrix

| Feature | Free | Premium |
|---------|------|---------|
| Server Profiles | Max 2 | Unbegrenzt |
| Terminal Tabs | Max 3 | Unbegrenzt |
| Parallel Transfers | Max 5 | Unbegrenzt |
| Banner Ads | Ja (Idle-Screens) | Nein |
| Session Recorder | Nein | Ja |
| Send to Claude | Nein | Ja |
| Diff-Viewer | Nein | Ja |
| Context-Bridge | Nein | Ja |
| File Watcher | Nein | Ja |
| Code Editor Write | Nein (Read-Only) | Ja |
| Syntax Highlighting | 6 Sprachen | Alle |
| Biometrie-Unlock | Nein | Ja |
| Custom Themes | Nein | Ja |

### 11.2 FeatureGateManager

```kotlin
class FeatureGateManager @Inject constructor(
    private val premiumManager: PremiumManager
) {
    fun maxServerProfiles(): Int = if (premiumManager.isPremium()) Int.MAX_VALUE else 2
    fun maxTerminalTabs(): Int = if (premiumManager.isPremium()) Int.MAX_VALUE else 3
    fun maxParallelTransfers(): Int = if (premiumManager.isPremium()) Int.MAX_VALUE else 5
    fun isFeatureEnabled(feature: PremiumFeature): Boolean = premiumManager.isPremium()
    fun shouldShowAds(): Boolean = !premiumManager.isPremium()
}
```

### 11.3 Pricing

| Option | Preis | Play Billing Product Type |
|--------|-------|--------------------------|
| Monatlich | 6,99 EUR/Monat | SUBS (monthly) |
| Jaehrlich | 69,99 EUR/Jahr | SUBS (yearly) |
| Einmalkauf | 139,98 EUR | INAPP (lifetime) |
| Free Trial | 7 Tage | SUBS Trial Period |

### 11.4 Ad-Platzierung Regeln

- Banner Ads (320x50) am unteren Bildschirmrand
- NUR in: Connection Manager (Idle), File List (Idle), Settings
- NIEMALS in: Terminal (aktiv), Transfer-Screen (aktiv), Code Editor, Proxmox Wizard, Dialoge
- Keine Interstitials, keine Rewarded Ads, keine Video Ads
- Ad wird ausgeblendet sobald User eine Aktion startet (Verbindung, Transfer, Navigation in Unterscreen)

---

## 12. Risiken & Mitigations

| # | Risiko | Wahrscheinlichkeit | Impact | Mitigation |
|---|--------|-------------------|--------|------------|
| 1 | SSHJ hat Kompatibilitaetsprobleme mit bestimmten SSH-Servern (alte Dropbear, exotische Ciphers) | Mittel | Hoch | Cipher-Liste konfigurierbar machen. Fallback auf weniger sichere Ciphers mit User-Warnung. SSHJ `Config` erlaubt explizite Cipher-Reihenfolge. |
| 2 | Termux terminal-emulator Library Lizenz (GPLv3) ist inkompatibel mit proprietaerer App | Hoch | Kritisch | Loesung: `terminal-view` (Apache 2.0) als Rendering-Basis verwenden. Fuer den Emulator-Layer: connectbot/sshlib (Apache 2.0) als Alternative pruefen. Falls noetig: Terminal-Emulator als separates Open-Source GPLv3 Modul veroeffentlichen und dynamisch laden. VOR Phase 4 muss die Lizenzfrage geklaert sein -- blockiert Terminal-Implementierung. |
| 3 | Android Foreground Service Restrictions werden in Android 17+ verschaerft | Mittel | Hoch | Foreground Service Types korrekt deklarieren. Regelmaessig Android Developer Preview testen. WorkManager als Fallback fuer nicht-interaktive Tasks. |
| 4 | Sora-Editor Performance bei grossen Dateien (>10MB) auf Foldables | Niedrig | Mittel | File-Size Limit von 5MB fuer den Editor. Groessere Dateien: Read-Only mit Lazy-Loading (nur sichtbare Zeilen rendern). User-Warnung bei grossen Dateien. |
| 5 | Wear OS Standalone SSH ueber WiFi: Watch hat limitierte CPU/RAM | Mittel | Mittel | Nur Quick-Commands (kein interaktives Terminal). Command-Output auf 10KB truncaten. Connection-Timeout auf 10s. Session auto-close nach 60s Inaktivitaet. |
| 6 | Google Play Billing Race Conditions bei Subscription + Lifetime Kauf | Niedrig | Hoch | Server-side Verification (optional). Lokal: `PurchasesUpdatedListener` prueft alle aktiven Purchases. Lifetime ueberschreibt Subscription. Grace Period von 3 Tagen bei Verification-Fehlern. |
| 7 | Room Schema Migration bei groesseren Updates | Mittel | Mittel | Schema-Export aktiviert, AutoMigration fuer additive Changes. Manuelle Migration-Tests in CI. Fallback: `fallbackToDestructiveMigration()` nur in Debug. |
| 8 | SFTP Resume nach Pause: Server unterstuetzt kein `readFrom(offset)` | Niedrig | Niedrig | Vor Resume: SFTP `stat()` auf Remote-Datei. Falls Groesse != transferredBytes: Transfer von vorne starten mit User-Hinweis. |

---

## 13. Design Mockups

Interaktive HTML-Mockups befinden sich im Verzeichnis `Mockups/`:

| Screen | Datei | Pixel Fold Optimiert |
|--------|-------|---------------------|
| Navigation Hub | `Mockups/index.html` | Folded + Unfolded Toggle |
| Dual-Pane File Manager | `Mockups/file-manager.html` | Folded (Single Pane + Tabs) / Unfolded (Dual Pane) |
| SSH Terminal | `Mockups/terminal.html` | Portrait (Fullscreen + Keyboard Sheet) / Landscape (Split) |
| Connection Manager | `Mockups/connection-manager.html` | Standard Mobile Layout |
| Transfer Queue | `Mockups/transfer-queue.html` | Standard Mobile Layout |
| Code Editor | `Mockups/code-editor.html` | Folded (Fullscreen) / Unfolded (Editor + File Tree) |
| Proxmox VM Manager | `Mockups/proxmox.html` | Standard Mobile Layout + Create Wizard |
| Settings & Premium | `Mockups/settings.html` | Settings + Paywall Overlay |
| Wear OS Companion | `Mockups/watch.html` | 8 Watch Screens + Complications |

Alle Mockups sind self-contained HTML-Dateien mit inline CSS/JS, Material 3 Dark Theme, und funktionalen Interaktionen (klickbar, navigierbar, animiert).
