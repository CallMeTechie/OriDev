# SCP-Protocol Implementation — Design

**Status:** Approved (brainstorming session 2026-04-26)
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
| 4 | **Two `SshClient` implementations**, routed by Hilt-`@IntoMap` keyed by `Protocol`. | Cleanest polymorphism; each transfer mode lives in its own file with its own tests. The `SshClient` interface stays unchanged. |
| 5 | `delete(directory)` walks the tree client-side, never `rm -rf`. | Identical to the SFTP path; deletes are cancellable and progress-reporting; the same test suite covers both modes. |
| 6 | **Listing requires GNU `coreutils`** (`ls --time-style`). BSD/Solaris servers fail with a precise `IOException`. | First-iteration scope. Extending to BSD `ls -laT` is mechanical follow-up if a real user reports it. |
| 7 | `uploadFileResumable` / `downloadFileResumable` throw `UnsupportedOperationException("SCP does not support resume")` from the SCP client. | SSHJ's `SCPFileTransfer` has no offset parameter; SCP is fundamentally streaming. Repository must catch and either fall back to a fresh transfer-from-zero or surface the limitation to the user. |
| 8 | `Protocol.SSH` continues to use the SFTP client for file operations. | "SSH" in the dropdown means "I want a terminal and a file browser without thinking about which sub-protocol." No change to that user-facing semantic. |

## Module structure

```
core/core-network/src/main/kotlin/dev/ori/core/network/ssh/
├── SshClient.kt                  (interface — unchanged)
├── SshSftpClientImpl.kt          (renamed from SshClientImpl; body unchanged)
├── SshScpClientImpl.kt           (new)
└── ScpListingParser.kt           (new — parses `ls -la --time-style=…` output)

core/core-network/src/test/kotlin/dev/ori/core/network/ssh/
├── SshSftpClientImplTest.kt      (renamed from SshClientImplTest; body unchanged)
├── SshScpClientImplTest.kt       (new)
└── ScpListingParserTest.kt       (new)
```

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

### Repository routing

`data/src/main/kotlin/dev/ori/data/repository/RemoteFileSystemRepository.kt`:

```kotlin
@Singleton
class RemoteFileSystemRepository @Inject constructor(
    private val clients: Map<Protocol, @JvmSuppressWildcards SshClient>,
    private val sessionRegistry: SessionRegistry,
) : FileSystemRepository, RemoteFileSystemSession {

    private val activeSessionId = AtomicReference<String?>(null)

    private fun client(): SshClient {
        val sessionId = activeSessionId.get()
            ?: throw IOException("No active SSH session")
        val protocol = sessionRegistry.openSessions.value
            .firstOrNull { it.id == sessionId }
            ?.profile?.protocol
            ?: throw IOException("Session $sessionId not in registry")
        return clients[protocol]
            ?: throw IOException("No SshClient registered for protocol $protocol")
    }

    override suspend fun listFiles(path: String): List<FileItem> =
        client().listFiles(requireSession(), path).map { it.toFileItem() }

    // …
}
```

`Session` (in `domain/`) gains `protocol: Protocol` so the registry can answer
the lookup without a DB round-trip. `SshClient.connect(...)` adds a `protocol`
parameter that's stored alongside the SSH handle — already required for the
disconnect-listener cleanup to know which client to stop reading from.

## Wire-level operations (SCP path)

Every server-side action in the SCP client runs through one of two SSHJ
primitives:

- **Bulk transfer** uses `SSHClient.newSCPFileTransfer()` (upload + download).
- **Everything else** opens a session channel and runs a single shell command
  whose stdout/stderr/exit-status the client interprets. The shell command
  itself is built from a fixed verb (`ls`, `mkdir -p`, `mv`, `chmod`, `rm`,
  `rmdir`, `stat`) plus one or two path arguments wrapped through the
  shell-escape helper below.

| `SshClient` method | SCP behaviour |
|---|---|
| `connect / disconnect / isConnected` | Identical to SFTP — same SSHJ transport, same `DisconnectListener`, same `sessions: ConcurrentHashMap<String, SSHClient>` (held in an abstract base or shared via composition). |
| `listFiles(sessionId, path)` | Single-command session channel: `LANG=C ls -la --time-style='+%Y-%m-%dT%H:%M:%S' <escaped-path>`. Stdout streamed into `ScpListingParser` → `List<RemoteFile>`. |
| `uploadFile / downloadFile` | `client.newSCPFileTransfer().upload(local, remote)` / `.download(remote, local)`, with a `TransferListener` that maps SSHJ's byte-progress callback onto our `onProgress(transferred, total)`. |
| `uploadFileResumable / downloadFileResumable` | Throw `UnsupportedOperationException("SCP does not support resume")`. |
| `mkdir(sessionId, path)` | Single-command session channel: `mkdir -p <escaped-path>`. Failure derived from non-zero exit status + stderr. |
| `rename(sessionId, old, new)` | Single-command session channel: `mv <escaped-old> <escaped-new>`. |
| `chmod(sessionId, path, octal)` | Single-command session channel: `chmod <octal three-digit string> <escaped-path>`. |
| `delete(sessionId, path)` | Client-side recursive walk. `listFiles` to enumerate; per file, single-command session channel: `rm <escaped-file>`. Per directory bottom-up: `rmdir <escaped-dir>`. Cancellable between items. |
| `executeCommand` | Unchanged — both modes share this. |
| `openShell` | Unchanged — terminal is protocol-agnostic. |
| `fileSize` | Single-command session channel: `stat -c %s <escaped-path>`. |

### Shell-escape helper

```kotlin
internal fun shellEscape(path: String): String =
    "'" + path.replace("'", "'\\''") + "'"
```

Single-quote wrapping with `'\''` for embedded single quotes. Eliminates shell
injection: every value is a literal argument to the verb. Tested with paths
containing spaces, `$VAR`, `;`, `|`, backticks, double quotes, single quotes,
newlines.

### Listing parser

Expected line format produced by `LANG=C ls -la --time-style='+%Y-%m-%dT%H:%M:%S'`:

```
drwxr-xr-x 2 user group 4096 2026-04-26T19:25:00 dirname
-rw-r--r-- 1 user group 1234 2026-04-26T19:25:00 file with spaces.txt
lrwxrwxrwx 1 user group   12 2026-04-26T19:25:00 link -> /etc/hosts
```

Parser splits on the first six whitespace runs; everything after is the name.
Symlinks are detected by leading `l` on the permission string and a ` -> `
substring in the name; the target is dropped from `name`. Lines starting with
`total ` are skipped.

If a line cannot be parsed (BSD `ls`, Solaris, exotic locale), `ScpListingParser`
returns the line in a separate "unparseable" bucket; `SshScpClientImpl.listFiles`
throws `IOException("Unsupported server: SCP listing requires GNU coreutils. Set the connection protocol to SFTP for this server.")` with the offending line in the cause-chain.

## Testing

| File | Coverage | ≈ tests |
|---|---|---|
| `ScpListingParserTest` | Spaces in filenames, symlinks, `->` in filenames, zero-byte files, group/owner with spaces, format-mismatch (BSD `ls`), trailing whitespace, leading `total NN`, empty output | 12 |
| `SshScpClientImplTest` | Each `SshClient` method with a mocked `SSHClient` + session channel + command stream: correct command strings sent, correct argument escaping, listing returns `RemoteFile`s, `delete` walks recursively (verified call order on the mock), `uploadFileResumable` throws `UnsupportedOperationException` | 10 |
| `SshSftpClientImplTest` | Renamed from current `SshClientImplTest`, body unchanged | (existing) |

`RemoteFileSystemRepositoryTest` adds:
- Path-edge-case tests on `deleteFile`: root, empty, single slash → all reject with `IllegalArgumentException` from a `require(...)` block before the call reaches the client.
- Property-based test (Kotest-property): for ~500 generated paths, exactly the safe ones reach the client mock.

Out of scope for unit tests: real-server SSH (manual smoke), delete performance on deep trees (UX assessment, not a unit test).

## Migration

Existing `ServerProfileEntity` rows with `protocol = SCP` were silently routed
to SFTP. After v0.34.5 they get the real SCP path. Two outcomes:

- **Server supports SFTP, but user picked SCP "by mistake":** continues to work iff the server *also* supports SCP plus GNU `ls`. Most Linux/BSD-compatible servers do.
- **Server supports neither SFTP for this user nor `ls -la --time-style`:** listing fails with the precise error message above. User changes the dropdown to `SFTP` (or whichever the server supports) and the connection works.

Release-notes line, mandatory for v0.34.5:

> SCP is now a real protocol with distinct wire behaviour, no longer a silent SFTP alias. If you previously selected SCP in a connection profile and it surprisingly stops working, switch the protocol to SFTP — the listing failure includes a hint to do so.

## Out of scope

- BSD `ls -laT` fallback for the listing parser. (Mechanical follow-up if a user requests it.)
- SCP resume via `dd skip=N` + binary stitching. (Phase-12 resume feature stays SFTP-only.)
- A second strategy layer between `RemoteFileSystemRepository` and `SshClient`. (Premature; only meaningful with 3+ file-transfer modes.)
- FTPS coverage audit. (Tracked separately.)
- Improving the "Failed to list files: Request failed" UX with SFTP status codes. (Tracked separately.)
