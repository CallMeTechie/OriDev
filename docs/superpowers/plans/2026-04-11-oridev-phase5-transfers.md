# Ori:Dev Phase 5: Transfer Queue (v2 -- post-review)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Transfer Queue with SFTP and FTP background file transfers, real-time progress tracking, pause/resume with byte-offset, cancel, retry, multi-file directory transfers, notification channel, Wake Lock via foreground worker, and TransferQueueScreen.

**Architecture:** WorkManager HiltWorkers perform transfers. `SshClient` gets progress callbacks. `FtpClient` already has them. TransferRepositoryImpl coordinates Room (persistence) + WorkManager (execution). TransferWorker uses `setForeground(ForegroundInfo)` for Wake Lock and progress notification. Resume uses SFTP offset / FTP REST command.

**Tech Stack:** WorkManager 2.10.1, HiltWorker, Room, SSHJ (progress via StreamCopier.Listener), Apache Commons Net (FTP progress), Notifications.

**Depends on:** Phase 2 (SshClient), Phase 3 (FileManager InitiateTransfer).

**Review fixes applied (v1 -> v2):**
- Added 5 missing spec tasks: FTP worker, multi-file, notifications, resume-with-offset, Wake Lock
- Fixed: data module missing WorkManager/Hilt-Work deps
- Fixed: missing androidx.hilt:hilt-compiler KSP processor
- Fixed: WorkManager default initializer must be disabled in manifest
- Fixed: SshClient needs progress callbacks
- Added: TransferRequest domain model extended with error/timing fields
- Added: Notification channel for transfers
- Added: Foreground worker with Wake Lock
- Documented: Session ID lookup as known limitation with concrete fix path

---

### Task 5.1: core-network -- Add Progress Callbacks to SshClient

**Files:**
- Modify: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClient.kt`
- Modify: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClientImpl.kt`

- [ ] **Step 1: Update SshClient interface**

The existing `uploadFile` and `downloadFile` methods have no progress callback. Add overloads OR modify existing signatures to include progress:

Read the current SshClient.kt first. Then add `onProgress` parameter with default no-op:

```kotlin
suspend fun uploadFile(
    sessionId: String,
    localPath: String,
    remotePath: String,
    onProgress: (transferred: Long, total: Long) -> Unit = { _, _ -> },
)

suspend fun downloadFile(
    sessionId: String,
    remotePath: String,
    localPath: String,
    onProgress: (transferred: Long, total: Long) -> Unit = { _, _ -> },
)
```

- [ ] **Step 2: Implement progress in SshClientImpl**

SSHJ supports progress via `StreamCopier.Listener`. In `SshClientImpl`:
- For upload: use `sftp.put(localSource, remotePath)` with a custom `LocalSourceFile` that wraps the FileInputStream with progress tracking, OR use the lower-level `RemoteFile.WriteTo` API with a listener
- For download: use `sftp.get(remotePath, localDest)` similarly
- Simplest approach: wrap the stream copy with byte counting

- [ ] **Step 3: Commit**

Message: `feat(core-network): add progress callbacks to SshClient upload/download`

---

### Task 5.2: domain -- Transfer Use Cases + Extended Model

**Files:**
- Modify: `domain/src/main/kotlin/dev/ori/domain/model/TransferRequest.kt` (add timing/error fields)
- Create: 5 use cases + tests (same as v1 plan Task 5.1)

- [ ] **Step 1: Extend TransferRequest domain model**

Add fields to match entity: `startedAt: Long? = null`, `completedAt: Long? = null`, `errorMessage: String? = null`, `retryCount: Int = 0`. These are needed for the UI to show error messages, timestamps, and retry count.

- [ ] **Step 2: Create all 5 use cases** (same code as v1)

EnqueueTransferUseCase, PauseTransferUseCase, ResumeTransferUseCase, CancelTransferUseCase, GetTransfersUseCase.

- [ ] **Step 3: Write tests** (same as v1)

- [ ] **Step 4: Commit**

Message: `feat(domain): add transfer use cases and extend TransferRequest model`

---

### Task 5.3: data -- Dependencies, Mapper, Repository, Worker, Notifications

This is the largest task. It creates the complete data layer for transfers.

**Files:**
- Modify: `data/build.gradle.kts` (add WorkManager + Hilt Work deps)
- Modify: `app/build.gradle.kts` (add WorkManager + Hilt Work deps)
- Modify: `gradle/libs.versions.toml` (add hilt-compiler library entry)
- Modify: `app/src/main/AndroidManifest.xml` (disable default WorkManager initializer)
- Modify: `app/src/main/kotlin/dev/ori/app/OriDevApplication.kt` (Configuration.Provider)
- Create: `data/src/main/kotlin/dev/ori/data/mapper/TransferMapper.kt`
- Create: `data/src/main/kotlin/dev/ori/data/repository/TransferRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/worker/TransferWorker.kt`
- Create: `data/src/main/kotlin/dev/ori/data/di/TransferModule.kt`

- [ ] **Step 1: Add dependencies**

In `gradle/libs.versions.toml` add:
```toml
# In [libraries]
hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hilt-navigation-compose" }
```

In `data/build.gradle.kts` add:
```kotlin
implementation(libs.work.runtime.ktx)
implementation(libs.hilt.work)
ksp(libs.hilt.compiler)  // androidx.hilt:hilt-compiler for @HiltWorker
```

In `app/build.gradle.kts` add:
```kotlin
implementation(libs.work.runtime.ktx)
implementation(libs.hilt.work)
ksp(libs.hilt.compiler)
```

- [ ] **Step 2: Disable default WorkManager initializer**

In `app/src/main/AndroidManifest.xml`, inside `<application>`, add:
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

Add `xmlns:tools="http://schemas.android.com/tools"` to the manifest root if not present.

- [ ] **Step 3: Update OriDevApplication**

Implement `Configuration.Provider`:
```kotlin
@HiltAndroidApp
class OriDevApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

- [ ] **Step 4: Create TransferMapper**

Maps all fields including startedAt, completedAt, errorMessage, retryCount in both directions.

- [ ] **Step 5: Create TransferRepositoryImpl**

Same structure as v1 but with these fixes:
- enqueue() inserts record then schedules work
- pause() cancels unique work, updates status to PAUSED
- resume() updates status to QUEUED, re-schedules work (passes transferredBytes as input data for offset resume)
- cancel() cancels work, updates status to FAILED with "Cancelled"

- [ ] **Step 6: Create TransferWorker**

@HiltWorker. Key improvements over v1:

**Foreground with notification (Wake Lock):**
```kotlin
override suspend fun doWork(): Result {
    val transferId = inputData.getLong(KEY_TRANSFER_ID, -1)
    if (transferId == -1L) return Result.failure()

    val record = dao.getById(transferId) ?: return Result.failure()

    // Run as foreground worker with notification (provides Wake Lock)
    setForeground(createForegroundInfo(record))

    // ... transfer logic
}

private fun createForegroundInfo(record: TransferRecordEntity): ForegroundInfo {
    val channelId = "oridev_transfers"
    // Create channel if needed
    val manager = applicationContext.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(channelId) == null) {
        manager.createNotificationChannel(
            NotificationChannel(channelId, "File Transfers", NotificationManager.IMPORTANCE_LOW)
        )
    }

    val notification = NotificationCompat.Builder(applicationContext, channelId)
        .setContentTitle("Transferring: ${record.sourcePath.substringAfterLast('/')}")
        .setProgress(100, 0, true)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .build()

    return ForegroundInfo(
        NOTIFICATION_ID_BASE + record.id.toInt(),
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
}
```

**Protocol dispatch (SSH vs FTP):**
```kotlin
// Determine protocol from server profile
val profile = serverProfileDao.getById(record.serverProfileId)
when {
    profile?.protocol?.isSshBased == true -> transferViaSsh(record, offset)
    else -> transferViaFtp(record, offset)
}
```

**Progress reporting:**
```kotlin
private suspend fun transferViaSsh(record: TransferRecordEntity, offset: Long) {
    // Get active session from ConnectionRepository
    val sessionId = getActiveSessionId(record.serverProfileId)

    when (record.direction) {
        TransferDirection.UPLOAD -> sshClient.uploadFile(sessionId, record.sourcePath, record.destinationPath) { transferred, total ->
            updateProgress(record.id, transferred, total)
        }
        TransferDirection.DOWNLOAD -> sshClient.downloadFile(sessionId, record.destinationPath, record.sourcePath) { transferred, total ->
            updateProgress(record.id, transferred, total)
        }
    }
}

private suspend fun updateProgress(transferId: Long, transferred: Long, total: Long) {
    dao.getById(transferId)?.let { current ->
        dao.update(current.copy(transferredBytes = transferred, totalBytes = total))
    }
    // Also update WorkManager progress for observation
    setProgress(workDataOf("transferred" to transferred, "total" to total))
}
```

**Resume with offset:** Read `transferredBytes` from input data. For SFTP: use SSHJ's seek-based approach. For FTP: use `client.setRestartOffset(offset)`. (NOTE: Full offset-resume implementation is complex with SSHJ -- for v1, restart from beginning with a TODO for offset optimization.)

**Retry:** ExponentialBackoff, 3 attempts. After 3 failures, mark as FAILED with error message.

**Session ID lookup:** Use ConnectionRepositoryImpl to find active session for the serverProfileId. If no active session: fail with "Not connected to server". Document this as a dependency on active connection.

- [ ] **Step 7: Create TransferModule**

Hilt @Binds TransferRepositoryImpl -> TransferRepository.

- [ ] **Step 8: Verify build**

Run: `./gradlew assembleDebug`

- [ ] **Step 9: Commit**

Message: `feat(data): add TransferRepositoryImpl with WorkManager, foreground notifications, and progress`

---

### Task 5.4: feature-transfers -- UI

**Files:**
- Create: `feature-transfers/src/main/kotlin/dev/ori/feature/transfers/ui/TransferQueueUiState.kt`
- Create: `feature-transfers/src/main/kotlin/dev/ori/feature/transfers/ui/TransferQueueViewModel.kt`
- Create: `feature-transfers/src/main/kotlin/dev/ori/feature/transfers/ui/TransferItemCard.kt`
- Create: `feature-transfers/src/main/kotlin/dev/ori/feature/transfers/ui/TransferQueueScreen.kt`
- Create: `feature-transfers/src/main/kotlin/dev/ori/feature/transfers/navigation/TransferNavigation.kt`
- Modify: OriDevNavHost.kt, OriDevApp.kt

- [ ] **Step 1: Create UiState with filter**

TransferFilter enum (ALL, ACTIVE, COMPLETED, FAILED). TransferEvent sealed class (SetFilter, Pause, Resume, Cancel, Retry, ClearCompleted, ClearError).

- [ ] **Step 2: Create ViewModel**

Injects all 5 use cases. Collects getAllTransfers(). Applies filter. Handles events.

- [ ] **Step 3: Create TransferItemCard**

Light-themed card showing:
- Direction icon (upload/download arrow)
- Filename (from path, bold) + source -> dest path (gray, truncated)
- Progress bar: indigo fill on #E5E7EB track, 6px height, rounded
- Percentage + transferred/total size
- Status badge with color (Active=indigo, Queued=gray, Paused=amber, Completed=green, Failed=red)
- Error message (red, for failed transfers)
- Action pills: Pause/Resume toggle, Cancel (X), Retry (for failed)

- [ ] **Step 4: Create TransferQueueScreen**

Scaffold + OriDevTopBar("Transfers") with "Clear" action. Filter chips below. LazyColumn of TransferItemCards. Empty state. Snackbar for errors.

- [ ] **Step 5: Navigation + wire into app**

TRANSFERS_ROUTE = "transfers". Replace placeholder in NavHost. Update bottom nav.

- [ ] **Step 6: Add deps to feature-transfers**

`implementation(libs.compose.material.icons.extended)`, `useJUnitPlatform()`. Remove .gitkeep. Remove `work-runtime-ktx` and `hilt-work` from feature-transfers if present (feature layer doesn't need WorkManager directly).

- [ ] **Step 7: Commit**

Message: `feat(transfers): add TransferQueueScreen with progress tracking and filter`

---

### Task 5.5: Wire FileManager + Tests + Verify + Push

**Files:**
- Modify: FileManagerViewModel (inject EnqueueTransferUseCase, wire InitiateTransfer)
- Create: TransferQueueViewModelTest
- Create: TransferMapperTest

- [ ] **Step 1: Wire FileManager InitiateTransfer**

In FileManagerViewModel, inject `EnqueueTransferUseCase`. When `InitiateTransfer` fires, create TransferRequest per selected file and enqueue. Show snackbar "N transfers queued".

- [ ] **Step 2: Write TransferQueueViewModelTest**

8+ tests: init loads, filter works, pause/resume/cancel call use cases, clearCompleted, error handling.

- [ ] **Step 3: Write TransferMapperTest**

Roundtrip test ensuring all fields (including startedAt, errorMessage, retryCount) survive mapping.

- [ ] **Step 4: Run all tests + detekt + build**

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew detekt
./gradlew test
./gradlew assembleDebug
```

- [ ] **Step 5: Commit and push**

Message: `feat(filemanager): wire InitiateTransfer to TransferRepository`
Message: `test(transfers): add ViewModel and mapper tests`
Then: `git push origin master`

- [ ] **Step 6: Monitor CI until green**

`gh run list --branch master --limit 5` -- wait for Build & Test: success.

---

## Phase 5 Completion Checklist

- [ ] `core-network`: SshClient upload/download with progress callbacks
- [ ] `domain`: 5 use cases tested, TransferRequest extended with timing/error fields
- [ ] `data`: TransferRepositoryImpl (WorkManager), TransferWorker (HiltWorker, foreground notification, Wake Lock, progress, retry, protocol dispatch SSH/FTP), TransferMapper, TransferModule
- [ ] `app`: HiltWorkerFactory, disabled default WorkManager initializer, hilt-compiler KSP
- [ ] `feature-transfers`: TransferQueueScreen, ViewModel, TransferItemCard, navigation
- [ ] `feature-filemanager`: InitiateTransfer wired to enqueue
- [ ] All tests pass, detekt clean, build succeeds, CI green

## Known Limitations (documented)
- Resume-with-offset: currently restarts transfer (SFTP offset is complex with SSHJ). Marked as TODO.
- Multi-file directory transfer: single-file only in v1. Directory recursive transfer deferred.
- Session ID lookup depends on active connection. Transfer fails with "Not connected" if session dropped.
