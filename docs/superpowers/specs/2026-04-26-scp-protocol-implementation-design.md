# SCP-Protocol Implementation — Design

**Status:** Approved (brainstorming session 2026-04-26, devil's-advocate revision 2026-04-26)
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
| 4 | **Two `SshClient` implementations** keyed by `Protocol`, but **transport state is owned by a third class** (`SshSessionStore`). | Cleanest polymorphism for the file-ops, but the session map and disconnect-listener cleanup must be single-owner — otherwise the v0.34.2 race-condition fix gets split across two classes and stops working (devil's-advocate concern #2). |
| 5 | `delete(directory)` walks the tree client-side, **batching server commands** to a configurable max-args size (default 200 args ≈ ~16 KB). | Identical algorithmic shape to the SFTP path; deletes are cancellable and progress-reporting. Batching avoids exhausting OpenSSH's `MaxSessions=10` default (devil's-advocate concern #5). |
| 6 | **Listing requires GNU `coreutils`**, invoked with `--numeric-uid-gid` and `--time-style='+%Y-%m-%dT%H:%M:%S'`. BSD/Solaris servers fail with a precise `IOException`. | Numeric UID/GID prevents user/group strings with spaces from corrupting the parser silently (devil's-advocate concern #3). First-iteration scope; BSD `ls -laT` fallback is mechanical follow-up. |
| 7 | **All non-transfer commands are wrapped in `sh -c '<inner>'` invoked via `Session.exec`, with `bash --noprofile --norc -c` preferred when bash is available.** | The user's login shell may emit a MOTD, `~/.bashrc` may print things, `/etc/profile` may run `fortune`/`last-login`. None of that interferes with `sh -c`'s controlled output (devil's-advocate concerns #1 and #6). |
| 8 | `uploadFileResumable` / `downloadFileResumable` throw `UnsupportedOperationException("SCP does not support resume")` from the SCP client. | SSHJ's `SCPFileTransfer` has no offset parameter; SCP is fundamentally streaming. Repository must catch and either fall back to a fresh transfer-from-zero or surface the limitation to the user. |
| 9 | `Protocol.SSH` continues to use the SFTP client for file operations. | "SSH" in the dropdown means "I want a terminal and a file browser without thinking about which sub-protocol." No change to that user-facing semantic. |
| 10 | **Local-side I/O for upload/download stays the Repository's responsibility.** SCP and SFTP clients both accept a `String localPath` to a regular `File` on disk; if the source/destination is a SAF `content://` URI, the Repository materializes a temp file via `ContentResolver` first (current SFTP-path pattern). | Avoids forcing SSHJ to learn about Android's SAF (devil's-advocate concern #4). Keeps the contract uniform between modes. |
| 11 | **Every command's exit status is checked**; non-zero exit drains stderr and is wrapped as `IOException("<verb> failed: <stderr-first-line>", cause)`. | Empty stdout from a failing `ls` (e.g. `Permission denied`) must NOT silently render an empty directory (devil's-advocate concern #7). |

## Architecture

### Class layout

```
core/core-network/src/main/kotlin/dev/ori/core/network/ssh/
├── SshClient.kt                  (interface — unchanged)
├── SshSessionStore.kt            (NEW — @Singleton, owns the sessions map and disconnect-listener)
├── SshSftpClientImpl.kt          (renamed from SshClientImpl; now delegates connect/disconnect to SshSessionStore)
├── SshScpClientImpl.kt           (NEW — same delegation pattern)
├── ShellInvocation.kt            (NEW — internal helper: builds `sh -c '<inner>'` / `bash --noprofile --norc -c '<inner>'`,
│                                  picks bash when available via a once-per-session probe, tracks exit status,
│                                  drains stderr on failure)
└── ScpListingParser.kt           (NEW — parses `ls -la --numeric-uid-gid --time-style=…` output)

core/core-network/src/test/kotlin/dev/ori/core/network/ssh/
├── SshSessionStoreTest.kt        (NEW — disconnect-listener cleanup, getClient guard, both clients share state)
├── SshSftpClientImplTest.kt      (renamed from SshClientImplTest; transport tests removed, file-op tests stay)
├── SshScpClientImplTest.kt       (NEW)
├── ShellInvocationTest.kt        (NEW — wrapper builds correct strings, MOTD-line in stdout doesn't poison parser,
│                                  csh login default doesn't break quoting)
└── ScpListingParserTest.kt       (NEW)
```

### State ownership

`SshSessionStore` owns the single source of truth for "which transports are alive":

```kotlin
@Singleton
class SshSessionStore @Inject constructor(
    private val hostKeyVerifier: OriDevHostKeyVerifier,
) {
    private val sessions = ConcurrentHashMap<String, SSHClient>()

    suspend fun connect(host: String, port: Int, username: String, …): SshSession =
        withContext(Dispatchers.IO) { /* same as today's SshClientImpl.connect, plus registerDisconnectCleanup */ }

    suspend fun disconnect(sessionId: String) { sessions.remove(sessionId)?.close() }

    fun getClient(sessionId: String): SSHClient {
        val client = sessions[sessionId]
            ?: throw IOException("No active SSH session: $sessionId")
        if (!client.isConnected) {
            sessions.remove(sessionId, client)
            runCatching { client.close() }
            throw IOException("SSH session terminated: $sessionId")
        }
        return client
    }

    private fun registerDisconnectCleanup(sessionId: String, client: SSHClient) {
        client.transport.disconnectListener = DisconnectListener { _, _ ->
            sessions.remove(sessionId, client)
        }
    }
}
```

`SshSftpClientImpl` and `SshScpClientImpl` both inject `SshSessionStore` and route their `connect/disconnect/isConnected/getClient` through it. Neither holds its own session map. The v0.34.2 disconnect-listener fix and the v0.34.4 `IOException`-typed guard live in **one** place.

### Hilt wiring

`data/src/main/kotlin/dev/ori/data/di/SshModule.kt`:

```kotlin
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class ProtocolKey(val value: Protocol)

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
}
```

### Connect routing

`connect` is the entry point; the *caller* picks the right `SshClient` from the map. The two callers are `SessionRegistry.connect(profileId)` (Filemanager + Terminal user-initiated path) and the Wear `WearMessageListenerService` (which calls `executeCommand` directly today). Both already know the `Protocol` from the profile.

```kotlin
class SessionRegistry @Inject constructor(
    private val clients: Map<Protocol, @JvmSuppressWildcards SshClient>,
    private val profileRepository: ServerProfileRepository,
) {
    suspend fun connect(profileId: Long): Result<Session> {
        val profile = profileRepository.get(profileId)
        val client = clients[profile.protocol]
            ?: return Result.failure(IOException("Protocol ${profile.protocol} not supported"))
        // Both clients delegate through SshSessionStore — `connect` returns the same SshSession
        // regardless of which client object handled it. Subsequent file-ops route via `clients[profile.protocol]`.
        return runCatching { client.connect(profile.host, profile.port, profile.username, …) }
            .map { Session(it.sessionId, profile.protocol, profile, …) }
    }
}
```

`Session` (`domain/`) gains `protocol: Protocol` so `RemoteFileSystemRepository` can pick the right client:

```kotlin
private fun client(): SshClient {
    val sessionId = activeSessionId.get() ?: throw IOException("No active SSH session")
    val protocol = sessionRegistry.openSessions.value.firstOrNull { it.id == sessionId }?.protocol
        ?: throw IOException("Session $sessionId not in registry")
    return clients[protocol]
        ?: throw IOException("No SshClient registered for protocol $protocol")
}
```

`WearMessageListenerService` is updated symmetrically: it receives the same `Map<Protocol, SshClient>` and resolves by the profile's protocol before calling `executeCommand`.

## Wire-level operations (SCP path)

Every server-side action in the SCP client runs through one of two SSHJ
primitives:

- **Bulk transfer** uses `SSHClient.newSCPFileTransfer()` (upload + download).
- **Everything else** opens a session channel and runs `<wrapper>` where `<wrapper>` is the `ShellInvocation` helper's output (`bash --noprofile --norc -c '<inner>'` if bash is available on the server, `sh -c '<inner>'` as a portable fallback). The helper interprets stdout, stderr, and exit status uniformly.

| `SshClient` method | SCP behaviour |
|---|---|
| `connect / disconnect / isConnected` | Delegate to `SshSessionStore`. |
| `listFiles(sessionId, path)` | `<wrapper>` running `LANG=C ls -la --numeric-uid-gid --time-style='+%Y-%m-%dT%H:%M:%S' <escaped-path>`. Stdout streamed into `ScpListingParser` → `List<RemoteFile>`. Non-zero exit + stderr → `IOException("ls failed: <stderr first line>")`. |
| `uploadFile / downloadFile` | `client.newSCPFileTransfer().upload(local, remote)` / `.download(remote, local)`, with a `TransferListener` that maps SSHJ's byte-progress callback onto our `onProgress(transferred, total)`. The Repository materializes any SAF `content://` URI to a `File.createTempFile(…)` first. |
| `uploadFileResumable / downloadFileResumable` | Throw `UnsupportedOperationException("SCP does not support resume")`. |
| `mkdir(sessionId, path)` | `<wrapper>` running `mkdir -p <escaped-path>`. |
| `rename(sessionId, old, new)` | `<wrapper>` running `mv -- <escaped-old> <escaped-new>`. |
| `chmod(sessionId, path, octal)` | `<wrapper>` running `chmod <octal-string> <escaped-path>`. |
| `delete(sessionId, path)` | Client-side recursive walk; per directory level, all child files batched into a single `<wrapper>` running `rm -- <escaped-1> <escaped-2> … <escaped-N>` capped at 200 args per batch. Empty directories collected bottom-up and batched into `rmdir -- <…>`. |
| `executeCommand` | Unchanged — both modes share this. |
| `openShell` | Unchanged — terminal is protocol-agnostic. |
| `fileSize` | `<wrapper>` running `stat -c %s <escaped-path>`. |

### Shell-escape helper

```kotlin
internal fun shellEscape(path: String): String =
    "'" + path.replace("'", "'\\''") + "'"
```

POSIX single-quote escape. Combined with the always-`sh -c` invocation
strategy, the user's default login shell (csh, fish, zsh) is irrelevant — the
inner command runs under `sh` regardless.

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
returning to callers.

If a line cannot be parsed (BSD `ls`, Solaris, exotic locale, MOTD line that
somehow leaked through `sh -c`), `ScpListingParser` returns the line in a
separate "unparseable" bucket; `SshScpClientImpl.listFiles` throws
`IOException("Unsupported server: SCP listing requires GNU coreutils. Set the connection protocol to SFTP for this server.")` with the offending line in the cause-chain.

## Testing

| File | Coverage | ≈ tests |
|---|---|---|
| `SshSessionStoreTest` | Connect adds to map, disconnect removes, listener fires on EOF, getClient throws `IOException` for unknown id and for stale `isConnected==false`, two SshClient impls injected with the same store see the same session | 8 |
| `ShellInvocationTest` | Wrapper produces correct `bash --noprofile --norc -c …` and `sh -c …` strings; bash-availability probe is cached per session; non-zero exit raises `IOException` with stderr's first line; MOTD-prefixed stdout passes through unchanged because the wrapper isolates it | 6 |
| `ScpListingParserTest` | Spaces in filenames; symlinks; `->` in filenames; zero-byte files; format mismatch (BSD `ls`) → unparseable bucket; `total NN` line skipped; `.`/`..` filtered; empty input | 10 |
| `SshScpClientImplTest` | Each `SshClient` method with a mocked `SSHClient` + session channel + command stream: correct command strings sent, correct argument escaping (including paths with single quotes), listing returns `RemoteFile`s with numeric uid/gid, batched delete (count `startSession()` calls vs item count), `uploadFileResumable` throws `UnsupportedOperationException`, exit-code-non-zero → `IOException` with stderr | 12 |
| `SshSftpClientImplTest` | Renamed from current `SshClientImplTest`. Transport tests (connect/disconnect/getClient) move to `SshSessionStoreTest`; SFTP file-op tests stay | (existing −3) |

`RemoteFileSystemRepositoryTest` adds:
- Upload from SAF `content://` URI: `ContentResolver.openInputStream` is invoked, a `tmp` file is created, and the client's `uploadFile(tmp.absolutePath, …)` receives the local path.
- Path-edge-case tests on `deleteFile`: root, empty, single slash → all reject with `IllegalArgumentException` from a `require(...)` block.
- Property-based test (Kotest-property): for ~500 generated paths, exactly the safe ones reach the client mock.

Out of scope for unit tests: real-server SSH (manual smoke), delete performance on deep trees (UX assessment, not a unit test).

## Migration

Existing `ServerProfileEntity` rows with `protocol = SCP` were silently routed
to SFTP. After v0.34.5 they get the real SCP path. Two outcomes:

- **Server supports SFTP, but user picked SCP "by mistake":** continues to work iff the server *also* supports SCP plus GNU `ls`. Most Linux/BSD-compatible servers do.
- **Server supports neither SFTP for this user nor `ls -la --numeric-uid-gid --time-style`:** listing fails with the precise error message above. User changes the dropdown to `SFTP` (or whichever the server supports) and the connection works.

`SessionRegistry` and `WearMessageListenerService` constructors change shape (gain `Map<Protocol, SshClient>` instead of a single `SshClient`). All call sites — including any Hilt `@Provides` factories — need the same update.

Release-notes line, mandatory for v0.34.5:

> SCP is now a real protocol with distinct wire behaviour, no longer a silent SFTP alias. If you previously selected SCP in a connection profile and it surprisingly stops working, switch the protocol to SFTP — the listing failure includes a hint to do so.

## Out of scope

- BSD `ls -laT` fallback for the listing parser. (Mechanical follow-up if a user requests it.)
- SCP resume via positional `dd skip=N` reads. (Phase-12 resume feature stays SFTP-only.)
- A second strategy layer between `RemoteFileSystemRepository` and `SshClient`. (Premature; only meaningful with 3+ file-transfer modes.)
- FTPS coverage audit. (Tracked separately.)
- Improving the "Failed to list files: Request failed" UX with SFTP status codes. (Tracked separately.)
- Sandboxed/forced-command SSH setups (`authorized_keys` `command="…"`). The `sh -c` wrapper assumes the user can run arbitrary commands.
