# SCP-Protocol Implementation — Design

**Status:** Approved (brainstorming session 2026-04-26, devil's-advocate revisions ×2 on 2026-04-26)
**Target release:** v0.34.5
**Author trail:** Brainstormed with user 2026-04-26 — see commit history of `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/`.

## Why

`Protocol.SCP` is a value in the `core-common` enum and is offered as a dropdown
choice in `AddEditConnectionScreen`. It is persisted in `ServerProfileEntity`.
But in the runtime path it is **silently aliased to SFTP**: every call from
`RemoteFileSystemRepository` enters `SshClientImpl.withSftpClient { … }` which
hardcodes the SFTP subsystem.

Consequence on servers that have no SFTP subsystem (or where the user lacks
SFTP privileges, but SCP/SSH-exec works fine) — `oridev-error-listfiles-right-2026-04-26-19-25-26.txt`
is the canonical example: SSH connect succeeds, the UI flips the right pane
to "Remote (Connected)", then `sftp.ls("/")` raises
`net.schmizz.sshj.connection.ConnectionException: Request failed`. The user
has WinSCP working against the same host with **SCP** as the transfer protocol,
and reasonably expects Ori:Dev's own SCP option to work the same way.

This spec closes the gap: the `Protocol.SCP` choice gets a real, distinct
wire-level implementation; the user's choice is honoured strictly with no
silent fallback.

## Decisions

| # | Choice | Reasoning |
|---|---|---|
| 1 | SCP is a **real, separate transfer protocol**, not an SFTP alias. | The dropdown should not lie. |
| 2 | **Strict routing, no auto-fallback** between SFTP and SCP. | Auto-fallback hides which protocol is actually in use, making diagnosis harder and undermining the user's explicit choice. |
| 3 | **Reuse SSHJ's built-in `net.schmizz.sshj.xfer.scp.SCPFileTransfer`.** | The library is already on the classpath, no second SSH library, no custom wire-level code for upload/download. |
| 4 | **Two `SshClient` implementations** keyed by `Protocol`, but **transport state is owned by a third class** (`SshSessionStore`). | Cleanest polymorphism for the file-ops, but the session map and disconnect-listener cleanup must be single-owner — otherwise the v0.34.2 race-condition fix gets split across two classes and stops working (devil's-advocate v1 concern #2). |
| 5 | `delete(directory)` walks the tree client-side, **batching server commands** to a configurable max-args size (default 200 args ≈ ~16 KB). The `SshClient.delete` interface signature changes to `delete(sessionId, paths: List<String>) → DeleteResult`; **both `SshSftpClientImpl` and `SshScpClientImpl` honour this contract** — they continue past per-item permission errors and report `succeeded` / `failed` lists rather than throwing on the first failed item. | Avoids exhausting OpenSSH's `MaxSessions=10` default, and prevents the "snackbar says failed but 349/350 files are actually gone" UX surprise (devil's-advocate v1 concern #5, v2 concern #6). Pinning the contract on the interface (not just the SCP impl) keeps the Repository protocol-agnostic and gives the SFTP path the same partial-success semantics — devil's-advocate v3 concern #2. |
| 6 | **Listing requires GNU `coreutils`**, invoked with `--numeric-uid-gid` and `--time-style='+%Y-%m-%dT%H:%M:%S'`. Numeric UIDs/GIDs are resolved to display names via a **once-per-session** lookup (`getent passwd; getent group`, cached in `SshSessionStore`). | Numeric flag prevents user/group strings with spaces from corrupting the parser silently (devil's-advocate v1 concern #3). The lookup-and-cache step keeps the UI showing `marc` rather than `1000`, matching the SFTP-mode behaviour (devil's-advocate v2 concern #2). |
| 7 | **All non-transfer commands are wrapped in `sh -c`** invoked via `Session.exec`, with `bash --noprofile --norc -c` preferred when bash is available. The bash-probe runs **once during `connect()` itself**, atomically, with the result stored on the `SshSession`. **If the probe's channel-open or exec fails for any transport-level reason (MaxSessions exhausted, transient network glitch), `connect()` silently falls back to `bashAvailable = false` and emits a `NonFatalErrorLogger` entry** — the user-facing connect still succeeds. | The user's login shell may emit MOTD; `~/.bashrc` may print things; `/etc/profile` may run `fortune`. None of that interferes with `sh -c`'s controlled output (devil's-advocate v1 concerns #1 and #6). Probing in `connect` eliminates the parallel-first-op race (devil's-advocate v2 concern #3). The fallback prevents the regression where a user with 9 already-open SSH sessions on the same host could connect on v0.34.4 but not on v0.34.5 (devil's-advocate v3 concern #3) — `sh -c` is POSIX-everywhere, so the worst case is a slightly slower invocation path, never a connect failure. |
| 8 | `uploadFileResumable` / `downloadFileResumable` throw `UnsupportedOperationException("SCP does not support resume")`. | SSHJ's `SCPFileTransfer` has no offset parameter; SCP is fundamentally streaming. Repository must catch and either fall back to a fresh transfer-from-zero or surface the limitation to the user. |
| 9 | `Protocol.SSH` continues to use the SFTP client for file operations. A `@DefaultSshClient` qualifier preserves the bare `@Inject SshClient` injection point for legacy callers. | "SSH" in the dropdown means "I want a terminal and a file browser without thinking about which sub-protocol." Default-qualifier avoids forcing every existing call site to learn the protocol map (devil's-advocate v2 concern #4). |
| 10 | **Local-side I/O for upload/download uses SSHJ's `LocalSourceFile` / `LocalDestFile` interfaces directly**, with custom adapters that wrap SAF `content://` `InputStream`/`OutputStream` and stream byte-for-byte. **No temp-file materialisation.** | Avoids the 2×-disk-usage trap and the OOM-on-large-files trap a temp-file path would create (devil's-advocate v2 concern #1). SSHJ's interfaces are designed exactly for this. |
| 11 | **Every command's exit status is checked**; non-zero exit drains stderr and is wrapped as `IOException("<verb> failed: <stderr-first-line>", cause)`. The probe-failure path detects forced-command authorized_keys and surfaces a specific error. | Empty stdout from a failing `ls` (e.g. `Permission denied`) must NOT silently render an empty directory (devil's-advocate v1 concern #7). A forced-command setup must produce an actionable error, not a generic IOException (devil's-advocate v2 concern #3 sub-point). |

## Architecture

### Class layout

```
core/core-network/src/main/kotlin/dev/ori/core/network/ssh/
├── SshClient.kt                  (interface — unchanged)
├── SshSessionStore.kt            (NEW — @Singleton, owns the sessions map, the disconnect-listener,
│                                  the bash-availability flag per session, and the UID/GID name cache)
├── SshSftpClientImpl.kt          (renamed from SshClientImpl; now delegates connect/disconnect to SshSessionStore)
├── SshScpClientImpl.kt           (NEW — same delegation pattern)
├── ShellInvocation.kt            (NEW — internal helper: builds wrapper command strings,
│                                  reads back stdout, drains stderr on failure, returns ShellResult)
├── ScpListingParser.kt           (NEW — parses `ls -la --numeric-uid-gid --time-style=…` output)
└── LocalFileAdapters.kt          (NEW — SAF-Uri-backed LocalSourceFile and LocalDestFile)

core/core-network/src/test/kotlin/dev/ori/core/network/ssh/
├── SshSessionStoreTest.kt        (NEW)
├── SshSftpClientImplTest.kt      (renamed; transport tests moved to SshSessionStoreTest)
├── SshScpClientImplTest.kt       (NEW)
├── ShellInvocationTest.kt        (NEW)
├── ScpListingParserTest.kt       (NEW)
└── LocalFileAdaptersTest.kt      (NEW — Robolectric, ContentResolver-backed)
```

### State ownership: `SshSessionStore`

The store owns four cross-cutting facts about each live session:

```kotlin
data class LiveSession(
    val client: SSHClient,
    val protocol: Protocol,
    val bashAvailable: Boolean,         // resolved once during connect
    val nameCache: NameCache,           // uid → user, gid → group; populated on first listFiles
)

@Singleton
class SshSessionStore @Inject constructor(
    private val hostKeyVerifier: OriDevHostKeyVerifier,
) {
    private val sessions = ConcurrentHashMap<String, LiveSession>()

    suspend fun connect(host: String, port: Int, username: String,
                        password: CharArray?, privateKey: ByteArray?,
                        protocol: Protocol): SshSession =
        withContext(Dispatchers.IO) {
            val client = openTransport(host, port, username, password, privateKey)
            val bashAvailable = probeBash(client)        // single-flight; result stored, no retry
            val sessionId = UUID.randomUUID().toString()
            sessions[sessionId] = LiveSession(client, protocol, bashAvailable, NameCache.empty())
            registerDisconnectCleanup(sessionId, client)
            SshSession(sessionId, protocol, host, port, …)
        }

    fun getSession(sessionId: String): LiveSession {
        val live = sessions[sessionId]
            ?: throw IOException("No active SSH session: $sessionId")
        if (!live.client.isConnected) {
            sessions.remove(sessionId, live)
            runCatching { live.client.close() }
            throw IOException("SSH session terminated: $sessionId")
        }
        return live
    }

    suspend fun disconnect(sessionId: String) { sessions.remove(sessionId)?.client?.close() }

    /** Cached UID/GID resolution, populated lazily by SshScpClientImpl on first listFiles. */
    suspend fun ensureNameCache(sessionId: String, populate: suspend () -> NameCache): NameCache { … }

    private fun registerDisconnectCleanup(sessionId: String, client: SSHClient) {
        client.transport.disconnectListener = DisconnectListener { _, _ ->
            sessions.remove(sessionId)
        }
    }

    private suspend fun probeBash(client: SSHClient): Boolean {
        // Single channel-open + exec, fail-safe by design.
        //  • exec succeeds AND stdout matches sentinel  →  return true  (bash available)
        //  • exec succeeds, no sentinel                 →  return false, log "scp-bash-probe-fallback"
        //  • exec succeeds, stderr matches forced-cmd   →  throw IOException("Server appears to
        //                                                  use a forced-command authorized_keys
        //                                                  configuration; SCP requires unrestricted
        //                                                  shell access.") — this is the ONE case
        //                                                  where the probe propagates: the user
        //                                                  cannot use SCP at all, no fallback helps.
        //  • channel-open / exec itself raises          →  return false, log "scp-bash-probe-fallback"
        //                                                  with the cause attached. `sh -c` is POSIX
        //                                                  everywhere, so this fallback is correct;
        //                                                  prevents the "9 sessions already open
        //                                                  → connect now fails" regression.
    }
}
```

`SshSftpClientImpl` and `SshScpClientImpl` both inject `SshSessionStore`. Neither holds its own session map. The v0.34.2 disconnect-listener fix and the v0.34.4 `IOException`-typed guard live in **one** place. Keep-alive (`KEEPALIVE_INTERVAL_SECONDS = 15`) is set in `openTransport` and propagates onto the cached `SSHClient`.

### Hilt wiring

`data/src/main/kotlin/dev/ori/data/di/SshModule.kt`:

```kotlin
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class ProtocolKey(val value: Protocol)

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultSshClient

@Module
@InstallIn(SingletonComponent::class)
abstract class SshClientModule {

    @Binds
    @IntoMap
    @ProtocolKey(Protocol.SFTP)
    abstract fun bindSftpClient(impl: SshSftpClientImpl): SshClient

    @Binds
    @IntoMap
    @ProtocolKey(Protocol.SCP)
    abstract fun bindScpClient(impl: SshScpClientImpl): SshClient

    @Binds
    @IntoMap
    @ProtocolKey(Protocol.SSH)
    abstract fun bindSshClientAsSftp(impl: SshSftpClientImpl): SshClient

    /** Default binding for legacy callers that haven't been routed to the protocol map. */
    @Binds
    @DefaultSshClient
    abstract fun bindDefault(impl: SshSftpClientImpl): SshClient
}
```

### Migration plan for `@Inject SshClient` call sites

Spec mandates a `grep -rn '@Inject.*SshClient\b'` audit before the routing diff is finalised. Every site is classified into one of two columns:

| Site | Treatment |
|---|---|
| `RemoteFileSystemRepository` | injects `Map<Protocol, @JvmSuppressWildcards SshClient>` and resolves per-call by the active session's protocol. |
| `SessionRegistry` | injects the same map; `connect(profileId)` picks `clients[profile.protocol]` before calling. |
| `WearMessageListenerService` | injects the same map; resolves by the profile's protocol before calling `executeCommand`. |
| `TerminalViewModel`, `ConnectionDetailViewModel`, `feature-proxmox`-internal callers, etc. | inject `@DefaultSshClient SshClient`. Terminal is protocol-agnostic; everything else either talks to a single protocol per profile (proxmox uses its own client and isn't part of this routing) or is being migrated incrementally. |

Migration PR is gated by the audit table being filled in for every grep-hit and showing zero "ambiguous" rows.

## Wire-level operations (SCP path)

Every server-side action in the SCP client runs through one of two SSHJ
primitives:

- **Bulk transfer** uses `SSHClient.newSCPFileTransfer()` (upload + download), with our `LocalSourceFile`/`LocalDestFile` adapters from `LocalFileAdapters.kt`.
- **Everything else** opens a session channel and runs through `ShellInvocation`. The helper builds either `bash --noprofile --norc -c '<inner>'` (when `LiveSession.bashAvailable == true`) or `sh -c '<inner>'`, opens the channel, drains stdout + stderr, returns `ShellResult(exitCode, stdout, stderr)`. Non-zero exit is converted to `IOException` by the call site with a verb-specific prefix.

| `SshClient` method | SCP behaviour |
|---|---|
| `connect / disconnect / isConnected` | Delegate to `SshSessionStore`. |
| `listFiles(sessionId, path)` | First call per session triggers `SshSessionStore.ensureNameCache(...)`: a single shell invocation runs `getent passwd 2>/dev/null; echo '---'; getent group 2>/dev/null` (with `cat /etc/passwd; echo '---'; cat /etc/group` as fallback if `getent` is unavailable), populating the UID/GID maps. The actual listing then runs `LANG=C ls -la --numeric-uid-gid --time-style='+%Y-%m-%dT%H:%M:%S' <escaped-path>`; stdout streams through `ScpListingParser`; numeric uid/gid are resolved to names via the cache before returning `RemoteFile` instances. Non-zero exit + stderr → `IOException("ls failed: <stderr first line>")`. `.` and `..` filtered out. |
| `uploadFile(sessionId, localPath, remotePath, onProgress)` | `client.newSCPFileTransfer().upload(LocalFileSystemAdapter.fromPath(localPath), remotePath)`. |
| `uploadFile(sessionId, sourceUri, remotePath, contentResolver, onProgress)` | New overload accepting an Android `Uri`. `client.newSCPFileTransfer().upload(SafSourceFile(sourceUri, contentResolver), remotePath)`. Streams `InputStream` byte-for-byte; no temp file. |
| `downloadFile(sessionId, remotePath, destUri, contentResolver, onProgress)` | Mirror overload using `SafDestFile`. Streams to `OutputStream` directly. |
| `uploadFileResumable / downloadFileResumable` | Throw `UnsupportedOperationException("SCP does not support resume")`. |
| `mkdir(sessionId, path)` | Shell invocation: `mkdir -p <escaped-path>`. |
| `rename(sessionId, old, new)` | Shell invocation: `mv -- <escaped-old> <escaped-new>`. |
| `chmod(sessionId, path, octal)` | Shell invocation: `chmod <octal-string> <escaped-path>`. |
| `delete(sessionId, paths) → DeleteResult` | New return type — applies to **both** `SshSftpClientImpl` and `SshScpClientImpl`. SCP path: client-side recursive walk; per directory level, all child files batched into a single `rm -- <escaped-1> … <escaped-N>` capped at 200 args per batch. After each batch, parse stderr line-by-line (`rm: cannot remove 'X': Permission denied`); collect into `succeeded` and `failed`. Empty directories collected bottom-up and batched into `rmdir -- <…>`. SFTP path: same algorithmic shape using `sftp.rm` / `sftp.rmdir` per item, accumulating `succeeded` / `failed` rather than throwing on the first SFTP-level error. Both return when all items processed; throw only on transport-level failures. |
| `executeCommand` | Unchanged — both modes share this. |
| `openShell` | Unchanged — terminal is protocol-agnostic. |
| `fileSize` | Shell invocation: `stat -c %s <escaped-path>`. |

### Shell-escape helper

```kotlin
internal fun shellEscape(path: String): String =
    "'" + path.replace("'", "'\\''") + "'"
```

POSIX single-quote escape. Combined with the always-`sh -c` invocation
strategy, the user's default login shell (csh, fish, zsh) is irrelevant — the
inner command runs under `sh` regardless.

### SAF adapters

```kotlin
internal class SafSourceFile(
    private val uri: Uri,
    private val resolver: ContentResolver,
) : LocalSourceFile {
    override fun getInputStream(): InputStream =
        resolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream for $uri")
    override fun getLength(): Long = … /* DocumentFile.length() */
    override fun getName(): String = … /* DocumentFile.getName() */
    // …other LocalSourceFile contract members
}

internal class SafDestFile(
    private val uri: Uri,
    private val resolver: ContentResolver,
) : LocalDestFile {
    override fun getOutputStream(append: Boolean): OutputStream {
        require(!append) { "SCP does not produce resumable transfers" }
        return resolver.openOutputStream(uri, "wt")
            ?: throw IOException("Cannot open output stream for $uri")
    }
    // …
}
```

These are what the upload/download overloads pass to SSHJ. Bytes flow
`SAF InputStream → SSHJ buffer → socket` without ever materialising on the
app-private cache.

### Listing parser

Expected line format produced by `LANG=C ls -la --numeric-uid-gid --time-style='+%Y-%m-%dT%H:%M:%S'`:

```
drwxr-xr-x 2 1000 1000 4096 2026-04-26T19:25:00 dirname
-rw-r--r-- 1 1000 1000 1234 2026-04-26T19:25:00 file with spaces.txt
lrwxrwxrwx 1 1000 1000   12 2026-04-26T19:25:00 link -> /etc/hosts
```

Parser splits on the first six whitespace runs; everything after is the name.
Symlinks are detected by leading `l` on the permission string and a ` -> `
substring in the name; the target is dropped from `name`. Lines starting with
`total ` are skipped. The `.` and `..` pseudo-entries are filtered out before
returning to callers. Numeric uid/gid are resolved to names via the
`SshSessionStore` name cache; if the cache returns `null` (uid not in passwd),
the numeric value is shown as a string.

If a line cannot be parsed (BSD `ls`, Solaris, exotic locale, MOTD line that
somehow leaked through `sh -c`), `ScpListingParser` returns the line in a
separate "unparseable" bucket; `SshScpClientImpl.listFiles` throws
`IOException("Unsupported server: SCP listing requires GNU coreutils. Set the connection protocol to SFTP for this server.")` with the offending line in the cause-chain.

## Testing

| File | Coverage | ≈ tests |
|---|---|---|
| `SshSessionStoreTest` | Connect adds to map; disconnect removes; listener fires on EOF; `getSession` throws IOException for unknown id and for stale `isConnected==false`; `KEEPALIVE_INTERVAL_SECONDS=15` is propagated; bash-probe result is stored on `LiveSession`; bash-probe failure with forced-command stderr produces a specific IOException; both SshClient impls injected with the same store see the same session; name cache is populated lazily and survives re-list | 11 |
| `ShellInvocationTest` | Wrapper produces correct `bash --noprofile --norc -c …` and `sh -c …` strings based on `LiveSession.bashAvailable`; non-zero exit raises IOException with stderr's first line; MOTD-prefixed stdout passes through unchanged because the wrapper isolates it | 6 |
| `ScpListingParserTest` | Spaces in filenames; symlinks; `->` in filenames; zero-byte files; format mismatch (BSD `ls`) → unparseable bucket; `total NN` line skipped; `.`/`..` filtered; empty input; numeric uid resolved via mock cache; numeric uid not in cache → numeric string preserved | 11 |
| `SshScpClientImplTest` | Each `SshClient` method with a mocked `SSHClient` + session channel + command stream: correct command strings sent, correct argument escaping (incl. paths with single quotes), listing returns `RemoteFile`s with resolved names, batched delete (count `Session.exec` calls vs item count), partial-failure delete returns `DeleteResult(succeeded, failed)` not exception, `uploadFileResumable` throws `UnsupportedOperationException`, exit-code-non-zero → `IOException` with stderr | 14 |
| `LocalFileAdaptersTest` | Robolectric, `ContentResolver`-backed: `SafSourceFile.getInputStream` uses `openInputStream`; throws on `null` resolver result; reports correct `getLength`/`getName` from `DocumentFile`; `SafDestFile` throws `IllegalArgumentException` on `append=true`; concurrent reads/writes don't corrupt the stream | 6 |
| `SshSftpClientImplTest` | Renamed from current `SshClientImplTest`. Transport tests (connect/disconnect/getClient) move to `SshSessionStoreTest`; SFTP file-op tests stay. New: `delete_partialFailure_returnsDeleteResult` mirrors the SCP test — 350 mock paths, mock raises `SFTPException("Permission denied")` on item #257, walker continues, returns `DeleteResult` with 349 succeeded + 1 failed entry. Locks the new interface contract on the SFTP path. | (existing −3 +1) |

`RemoteFileSystemRepositoryTest` adds:
- Upload from SAF `content://` URI: assertion that `SafSourceFile` is the value passed to `client.uploadFile(sessionId, sourceUri, …)`, and that `tmp` files are NOT created.
- Path-edge-case tests on `deleteFile`: root, empty, single slash → all reject with `IllegalArgumentException` from a `require(...)` block.
- Property-based test (Kotest-property): for ~500 generated paths, exactly the safe ones reach the client mock.
- `delete` partial-success case: 350 mock paths, mock fails item #257 → `DeleteResult` reflects 349 successes and 1 failure with the right error string; UI test asserts the snackbar text says "Deleted 349 files. 1 file could not be deleted: …".

`SessionRegistryTest`, `WearMessageListenerServiceTest`, and any other constructor-changed test files are updated to inject the protocol map. Hilt-wiring-smoke test: spin up the `@HiltAndroidTest` graph and assert all four injection points (`Repository`, `SessionRegistry`, `Wear`, `@DefaultSshClient`) resolve without `MissingBindingException`.

Out of scope for unit tests: real-server SSH (manual smoke), delete performance on deep trees (UX assessment, not a unit test).

## Migration

Existing `ServerProfileEntity` rows with `protocol = SCP` were silently routed
to SFTP. After v0.34.5 they get the real SCP path. Two outcomes:

- **Server supports SFTP, but user picked SCP "by mistake":** continues to work iff the server *also* supports SCP plus GNU `ls`. Most Linux/BSD-compatible servers do.
- **Server supports neither SFTP for this user nor `ls -la --numeric-uid-gid --time-style`:** listing fails with the precise error message above. User changes the dropdown to `SFTP` (or whichever the server supports) and the connection works.

Constructor changes to `SessionRegistry` and `WearMessageListenerService` ripple to their tests and to any existing `@Provides` factories. Implementation plan must include the audit grep before the routing diff is approved.

Release-notes line, mandatory for v0.34.5:

> SCP is now a real protocol with distinct wire behaviour, no longer a silent SFTP alias. If you previously selected SCP in a connection profile and it surprisingly stops working, switch the protocol to SFTP — the listing failure includes a hint to do so.

## Out of scope

- BSD `ls -laT` fallback for the listing parser. (Mechanical follow-up if a user requests it.)
- SCP resume via positional `dd skip=N` reads. (Phase-12 resume feature stays SFTP-only.)
- A second strategy layer between `RemoteFileSystemRepository` and `SshClient`. (Premature; only meaningful with 3+ file-transfer modes.)
- FTPS coverage audit. (Tracked separately.)
- Improving the "Failed to list files: Request failed" UX with SFTP status codes. (Tracked separately.)
- Sandboxed/forced-command SSH setups (`authorized_keys` `command="…"`). The `ShellInvocation` wrapper assumes the user can run arbitrary commands. The bash-probe surfaces a specific error message for this case (decision #11), but no fallback shell-less mode is provided.
- Stale-after-resume connection detection beyond what SSHJ keep-alive (`KEEPALIVE_INTERVAL_SECONDS=15`) provides. After a long device-suspend, the first operation may incur up to 15 s of latency before the disconnect-listener prunes the dead session. Acceptable for v0.34.5; if telemetry shows it as a recurring complaint, a forward TCP-keep-alive probe in `getSession` is the planned remedy.
