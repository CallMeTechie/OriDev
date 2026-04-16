# TransferEngine Blueprint — 2026-04-14 (Phase 12)

> Architect-designed blueprint for replacing the WorkManager-based transfer stub
> with a real `ForegroundService`-driven engine. Authored by the feature-dev
> code-architect subagent after a read-only pass across the codebase.

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  feature-transfers (UI Layer)                                                 │
│                                                                               │
│  TransferQueueScreen ──► TransferQueueViewModel                              │
│                               │                                               │
│                               ├── StateFlow<TransferQueueUiState>            │
│                               └── SharedFlow<ConflictRequest>  (NEW)        │
│                                         │                                    │
│                    GetTransfersUseCase, PauseTransferUseCase, etc.           │
│                    PauseAllTransfersUseCase (NEW)                            │
│                    CancelAllTransfersUseCase (NEW)                           │
│                    ResolveConflictUseCase (NEW)                              │
└──────────────────────────────────────────────────────────────────────────────┘
                     │ Room Flow (reactive, unchanged)
                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  domain (pure Kotlin — no Android imports)                                   │
│                                                                               │
│  TransferRepository (add updateProgress, updateStatus, getQueuedTransfers)   │
│  TransferEngineController (NEW interface)                                    │
│  TransferConflictRepository (NEW interface)                                  │
│  ConflictRequest + ConflictResolution (NEW models)                           │
└──────────────────────────────────────────────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  :app (service package) + :data (impls)                                      │
│                                                                               │
│  TransferEngineService (NEW ForegroundService in :app)                       │
│    SupervisorJob scope                                                        │
│    ─ TransferDispatcher (NEW) ── Semaphore(maxParallelTransfers)             │
│         └─ TransferWorkerCoroutine (NEW) per active row                     │
│              ├── SshTransferExecutor (NEW) — SSHJ resumable I/O             │
│              └── FtpTransferExecutor (NEW) — Commons Net resumable I/O      │
│    ─ RetryScheduler (NEW, pure Kotlin)                                       │
│    ─ TransferNotificationManager (NEW)                                       │
│                                                                               │
│  TransferEngineServiceControllerImpl (NEW in :data) — sends Intent to svc   │
│  TransferConflictRepositoryImpl (NEW in :data) — MutableSharedFlow          │
│  TransferRepositoryImpl (REWRITTEN — remove WorkManager, DAO-only)          │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 2. File Plan (full list)

See the agent's original report. Key new files:
- `:app/service/TransferEngineService.kt`, `TransferDispatcher.kt`, `TransferWorkerCoroutine.kt`, `SshTransferExecutor.kt`, `FtpTransferExecutor.kt`, `RetryScheduler.kt`, `TransferNotificationManager.kt`
- `:domain/repository/TransferEngineController.kt`, `TransferConflictRepository.kt`
- `:domain/model/ConflictRequest.kt`
- `:data/service/TransferEngineServiceControllerImpl.kt`, `:data/conflict/TransferConflictRepositoryImpl.kt`
- 3 new drawable vectors for notifications
- Instrumentation test `TransferEngineServiceTest.kt`

Files to modify:
- `TransferRecordEntity.kt` (+2 columns), `TransferRecordDao.kt` (+5 methods), `Migrations.kt` (new `MIGRATION_2_3`), `OriDevDatabase.kt` (version 3)
- `TransferRepositoryImpl.kt` **full rewrite — remove WorkManager**
- `TransferRepository.kt` interface (+4 methods)
- `SshClient*.kt`, `FtpClient*.kt` resumable overloads
- `AndroidManifest.xml` (`<service>` tag)
- `TransferQueueViewModel/UiState/Screen.kt` (ConflictDialog + PauseAll/CancelAll)
- **Delete** `data/worker/TransferWorker.kt`

## 3. Package Layout

- Service component + logic lives in **`:app/service/`** (only `:app` has a manifest)
- Interfaces in **`:domain/`** (no Android imports)
- Impls in **`:data/`** (sends Intents to `:app/service/TransferEngineService`)

Feature modules never import `:app`, so layering is preserved.

## 4. Domain Types

- **`ConflictRequest(id, transferId, conflictedPath, existingSize, existingLastModified)`**
- **`enum ConflictResolution { OVERWRITE, SKIP, RENAME, CANCEL }`**
- **`interface TransferEngineController { ensureRunning(); pauseAll(); cancelAll(); pauseTransfer(id); resumeTransfer(id); cancelTransfer(id) }`**
- **`interface TransferConflictRepository { val conflictRequests: SharedFlow<ConflictRequest>; emitConflict(req); suspend awaitResolution(id): ConflictResolution; resolve(id, resolution) }`**
- **`TransferRepository`** (+4 methods): `updateProgress`, `updateStatus`, `setNextRetryAt`, `getTransferById`
- **`TransferRecordEntity`** (+2 columns): `queuedAt: Long`, `nextRetryAt: Long?` — data-layer only, NOT surfaced on domain `TransferRequest` (confirmed Q1)
- New DAO methods: `getReadyQueued(now, limit)`, `updateProgress`, `updateStatus`, `scheduleRetry`, `observeNonTerminalCount`
- New use-cases: `PauseAllTransfersUseCase`, `CancelAllTransfersUseCase`, `ResolveConflictUseCase`

## 5. Parallelism Design

`TransferDispatcher` uses a `kotlinx.coroutines.sync.Semaphore`, reactively rebuilt when `maxParallelTransfers` preference changes:

```kotlin
class TransferDispatcher(
    private val dao: TransferRecordDao,
    private val prefs: AppPreferences,
    private val scope: CoroutineScope,
    private val workerFactory: TransferWorkerCoroutineFactory,
) {
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    @Volatile private var semaphore = Semaphore(DEFAULT_CAP)

    fun start() {
        scope.launch {
            prefs.maxParallelTransfers.distinctUntilChanged().collect { cap ->
                semaphore = Semaphore(cap)
                tryDispatch()
            }
        }
        scope.launch {
            dao.observeNonTerminalCount().distinctUntilChanged().collect {
                tryDispatch()
            }
        }
    }

    private suspend fun tryDispatch() {
        val slots = semaphore.availablePermits
        if (slots <= 0) return
        val candidates = dao.getReadyQueued(System.currentTimeMillis(), slots)
        for (record in candidates) {
            if (activeJobs.containsKey(record.id) || !semaphore.tryAcquire()) continue
            val job = scope.launch {
                try { workerFactory.create(record.id).execute() }
                finally {
                    activeJobs.remove(record.id)
                    semaphore.release()
                    tryDispatch()
                }
            }
            activeJobs[record.id] = job
        }
    }

    fun cancelWorker(id: Long) { activeJobs[id]?.cancel() }
}
```

**Cap increase:** new semaphore instantly grants more slots; tryDispatch fills.
**Cap decrease:** existing workers complete naturally; new semaphore gates future acquisitions.
**FIFO:** `getReadyQueued` sorts by `queuedAt ASC`.

## 6. Retry Math

```
baseMs   = baseSeconds * 2^retryCount * 1000
jitterMs = baseMs * 0.30
jitter   = Random.nextLong(-jitterMs, jitterMs + 1)
nextRetryAt = now + baseMs + jitter
```

With `baseSeconds = 10`, `maxRetryAttempts = 3`:

| Attempt | Base | Jitter | Example range |
|---|---|---|---|
| 0 → 1 | 10 s | ±3 s | 7–13 s |
| 1 → 2 | 20 s | ±6 s | 14–26 s |
| 2 → 3 | 40 s | ±12 s | 28–52 s |
| 3 | — | — | Mark FAILED |

`autoResume = false` skips retries entirely. `RetryScheduler` is pure Kotlin, unit-testable in isolation.

**Q6 decision (worker-side wakeup):** After scheduling a retry, the failing `TransferWorkerCoroutine` calls `delay(max(0, nextRetryAt - now))` **before** releasing the semaphore. This keeps structured concurrency intact and avoids a separate `ScheduledExecutorService`.

## 7. Persistence Schema

### Current v2 columns
`id, serverProfileId, sourcePath, destinationPath, direction, status, totalBytes, transferredBytes, fileCount, filesTransferred, startedAt, completedAt, errorMessage, retryCount`

### MIGRATION_2_3
```sql
ALTER TABLE transfer_records ADD COLUMN queuedAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE transfer_records ADD COLUMN nextRetryAt INTEGER;
CREATE INDEX IF NOT EXISTS idx_transfer_records_status_queuedAt ON transfer_records(status, queuedAt);
```

`TransferMapper.toEntity()` sets `queuedAt = System.currentTimeMillis()` on every new insert.

## 8. Notification Design

### Channel
`ID: "oridev_transfers"` (already created by existing TransferWorker — call is idempotent), importance `LOW`, no badge.

### Notification IDs
```
NOTIFICATION_ID_SERVICE   = 2001   // persistent ongoing
NOTIFICATION_ID_DONE_BASE = 3000   // + transferId.toInt()
NOTIFICATION_ID_FAIL_BASE = 4000   // + transferId.toInt()
```

### Aggregate (persistent, ongoing)
- Small icon: `R.drawable.ic_transfer_active`
- Title: `"3 active transfers"` (or direction split)
- Body: `"4.2 MB / 18.7 MB · 23%"`
- ProgressBar: `setProgress(100, pct, false)` or indeterminate when total unknown
- Actions: `Pause all` → `ACTION_PAUSE_ALL`, `Cancel all` → `ACTION_CANCEL_ALL`
- Tap: `MainActivity` LAUNCH (bring Transfers tab to focus)

### Per-file completion (`notifyTransferDone == true`)
- Small icon: `ic_transfer_upload` or `ic_transfer_download`
- Title: `"Transfer complete"`, Body: `"<name> — <size> to <path>"`
- `autoCancel = true`

### Per-file failure
- Small icon: `android.R.drawable.stat_notify_error`
- Action: `Retry` → `ACTION_RETRY(transferId)`

### New drawable assets (`app/src/main/res/drawable/`)
- `ic_transfer_upload.xml` — Lucide `Upload` path, 24dp VectorDrawable
- `ic_transfer_download.xml` — Lucide `Download` path
- `ic_transfer_active.xml` — Lucide `ArrowLeftRight` path

## 9. Wear Sync

**Already implemented.** `WearDataSyncPublisher` at `/root/OriDev/app/src/main/kotlin/dev/ori/app/wear/WearDataSyncPublisher.kt` subscribes to `transferRepository.getActiveTransfers()`, samples at 1000 ms, and publishes `WearPaths.TRANSFERS_ACTIVE` via `DataClient.putDataItem`. Engine just needs to keep the Room `Flow` live via `updateProgress` / `updateStatus`. No new sender needed.

**Latency budget:** 500 ms Room write throttle + 1000 ms Wear sample + BLE ≈ ≤ 1500 ms from byte I/O to watch tile.

## 10. Testing Plan

### Unit Tests
- **`RetrySchedulerTest`** (5): attempt 0/1/2 base range, jitter ≤ 30%, custom base seconds
- **`TransferDispatcherTest`** (6): start with 1 queued, cap 3 concurrency, cap decrease, cap increase, cancel single, skip future nextRetryAt
- **`TransferWorkerCoroutineTest`** (5): success→COMPLETED, cancel→PAUSED, autoResume true→scheduleRetry, autoResume false→FAILED, overwrite skip→COMPLETED(0 bytes)
- **`TransferConflictRepositoryImplTest`** (3): emitConflict, awaitResolution suspends, concurrent conflicts independent
- **`TransferRecordDaoTest`** (4): getReadyQueued skips future nextRetryAt, updateProgress targeting, observeNonTerminalCount, scheduleRetry

### Instrumentation
- **`TransferEngineServiceTest`**: enqueue → ACTIVE → pause → PAUSED → resume → COMPLETED → service stops. Fake `SshTransferExecutor` streams 5 ticks.

## 11. Build Sequence

```
P12.1 — DB schema + DAO + Migration
  Tests: TransferRecordDaoTest (4)
  Blocks: all others

P12.2 — Domain interfaces + RetryScheduler + new use-cases
  Tests: RetrySchedulerTest (5)
  Depends on: P12.1
  Blocks: P12.3, P12.4

──── PARALLEL (2 subagents after P12.2) ────────────

P12.3 — Resumable I/O in core-network
  Touch: SshClient(Impl), FtpClient(Impl)
  Tests: extend SshClientImplTest + FtpClientImplTest
  Depends on: P12.2
  Blocks: P12.5

P12.4 — TransferDispatcher + WorkerCoroutine + Executors + ConflictImpl
  Tests: TransferDispatcherTest (6), TransferWorkerCoroutineTest (5), TransferConflictRepositoryImplTest (3)
  Depends on: P12.2
  Blocks: P12.5

──── SEQUENTIAL ────────────

P12.5 — TransferEngineService + notifications + repository rewrite + manifest
  Delete: TransferWorker.kt
  Tests: TransferEngineServiceTest (instrumentation)
  Depends on: P12.3 + P12.4

P12.6 — UI: ConflictResolutionDialog + ViewModel PauseAll/CancelAll/ResolveConflict
  Depends on: P12.5
```

## 12. Decisions for Open Questions

| # | Question | Decision |
|---|---|---|
| Q1 | `queuedAt`/`nextRetryAt` on domain model? | **No** — data-layer only. `TransferMapper.toEntity()` sets `queuedAt`. |
| Q2 | SSHJ resumable upload reliability | **Try APPEND + seek. Fallback:** truncate-and-restart if remote server behaves oddly. Spike in P12.3. |
| Q3 | FtpClient thread safety | **One `FTPClient` per `FtpTransferExecutor` invocation.** Executor receives `ServerProfileDao` + `CredentialStore` to build its own connection. |
| Q4 | Conflict "ask" timeout | **`withTimeout(60_000)` in worker; default to `SKIP`** when UI isn't listening. |
| Q5 | `HiltWorkerFactory` cleanup after TransferWorker delete | **Keep factory registration** — other `@HiltWorker` classes may exist; conservative removal later. |
| Q6 | Backoff re-dispatch mechanism | **Worker-side `delay(nextRetryAt - now)` before `semaphore.release()`** — structured concurrency preserved. |
| Q7 | `WifiLock` for long transfers | **Out of scope.** May be added in a follow-up. |

---

**Source-confirmed state (2026-04-14):**
- WorkManager `TransferWorker` is the current engine — **being replaced entirely**.
- `WearDataSyncPublisher` already exists and publishes live — **no new Wear sender**.
- `TransferRecordEntity` missing `queuedAt` + `nextRetryAt` — added by `MIGRATION_2_3`.
- DB currently at version 2 — bump to 3.
- `ConnectionService` is the structural template for the new `TransferEngineService`.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions already in manifest.
