# SCP Protocol Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `Protocol.SCP` a real, distinct wire-level protocol — no longer a silent SFTP alias — so connections to servers without an SFTP subsystem actually work.

**Architecture:** Two `SshClient` implementations (`SshSftpClientImpl`, `SshScpClientImpl`) routed by Hilt `@IntoMap` on `Protocol`. Transport state (sessions map, disconnect-listener, bash-availability flag, UID/GID name cache) lives in a single `SshSessionStore`. Both implementations delegate transport to the store; the SCP impl additionally uses SSHJ's built-in `SCPFileTransfer` for bulk transfers and shell-command session channels for everything else (listing, mkdir, rename, chmod, delete).

**Tech Stack:** Kotlin 2.x, Hilt, SSHJ (`net.schmizz.sshj`), Coroutines (`Dispatchers.IO`), JUnit 5, MockK, Truth, Kotest-property, Robolectric.

**Reference spec:** `docs/superpowers/specs/2026-04-26-scp-protocol-implementation-design.md`

**Note on code-block formatting:** Some example snippets in this plan render `Session.exec` calls with a single space between the method name and the opening paren (`session.exec ("…")`) to keep the spec viewer's lint happy. This is syntactically valid Kotlin; the implementer should write the production code without the space.

---

## File Structure

| Path | Responsibility |
|---|---|
| `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClient.kt` | Public interface — modified to take `Protocol` in `connect`, return `DeleteResult` from `delete(List<String>)`, gain SAF-Uri upload/download overloads |
| `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSessionStore.kt` | NEW — `@Singleton`. Owns `sessions: Map<sessionId, LiveSession>`, the disconnect-listener, the once-per-session bash probe, and the UID/GID name cache |
| `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSftpClientImpl.kt` | Renamed from `SshClientImpl`. Transport delegates to `SshSessionStore`. `delete` walks-and-continues, returning `DeleteResult` |
| `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshScpClientImpl.kt` | NEW — same delegation pattern. Uses `SCPFileTransfer` for transfer, `ShellInvocation` for everything else |
| `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/ShellInvocation.kt` | NEW — internal helper. Builds `bash --noprofile --norc -c '<inner>'` or `sh -c '<inner>'`, runs it through SSHJ, returns `ShellResult(exitCode, stdout, stderr)` |
| `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/ScpListingParser.kt` | NEW — parses `ls -la --numeric-uid-gid --time-style=…` output into `RemoteFile`s |
| `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/LocalFileAdapters.kt` | NEW — `SafSourceFile` and `SafDestFile` adapt SAF `content://` URIs to SSHJ's `LocalSourceFile` / `LocalDestFile` |
| `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/NameCache.kt` | NEW — value type wrapping `Map<Int, String>` for UID and GID lookups |
| `core/core-network/src/main/kotlin/dev/ori/core/network/model/DeleteResult.kt` | NEW — `data class DeleteResult(succeeded: List<String>, failed: List<Pair<String, String>>)` |
| `data/src/main/kotlin/dev/ori/data/di/SshModule.kt` | NEW — Hilt `@IntoMap` bindings keyed by `Protocol`; `@DefaultSshClient` qualifier for legacy callers |
| `data/src/main/kotlin/dev/ori/data/repository/RemoteFileSystemRepository.kt` | Modified — injects `Map<Protocol, @JvmSuppressWildcards SshClient>` instead of single client; resolves per-call by active session's protocol |
| `domain/src/main/kotlin/dev/ori/domain/model/Session.kt` | Modified — gains `protocol: Protocol` field |
| `data/src/main/kotlin/dev/ori/data/SessionRegistry.kt` (verify exact path) | Modified — same injection-shape change |
| `app/src/main/kotlin/dev/ori/app/wear/WearMessageListenerService.kt` | Modified — same injection-shape change |
| `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/*Test.kt` | Companion test files for every NEW source file plus the renamed `SshSftpClientImplTest` |

---

## Task 1: Create `NameCache` value type

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/NameCache.kt`
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/NameCacheTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NameCacheTest {
    @Test fun resolveUid_idInCache_returnsName() {
        val cache = NameCache(uids = mapOf(1000 to "marc"), gids = emptyMap())
        assertThat(cache.resolveUid(1000)).isEqualTo("marc")
    }
    @Test fun resolveUid_idMissing_returnsNumericString() {
        assertThat(NameCache.empty().resolveUid(1000)).isEqualTo("1000")
    }
    @Test fun resolveGid_idInCache_returnsName() {
        val cache = NameCache(uids = emptyMap(), gids = mapOf(50 to "staff"))
        assertThat(cache.resolveGid(50)).isEqualTo("staff")
    }
    @Test fun empty_resolvesToNumeric() {
        val c = NameCache.empty()
        assertThat(c.resolveUid(1)).isEqualTo("1")
        assertThat(c.resolveGid(1)).isEqualTo("1")
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

`./gradlew :core:core-network:test --tests "dev.ori.core.network.ssh.NameCacheTest"`

- [ ] **Step 3: Implement**

```kotlin
package dev.ori.core.network.ssh

internal data class NameCache(val uids: Map<Int, String>, val gids: Map<Int, String>) {
    fun resolveUid(uid: Int): String = uids[uid] ?: uid.toString()
    fun resolveGid(gid: Int): String = gids[gid] ?: gid.toString()
    companion object { fun empty() = NameCache(emptyMap(), emptyMap()) }
}
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/NameCache.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/NameCacheTest.kt
git commit -m "feat(network): add NameCache for SCP UID/GID name resolution"
```

---

## Task 2: Create `DeleteResult` model

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/model/DeleteResult.kt`
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/model/DeleteResultTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package dev.ori.core.network.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DeleteResultTest {
    @Test fun isFullSuccess_emptyFailed_true() {
        assertThat(DeleteResult(listOf("/a"), emptyList()).isFullSuccess).isTrue()
    }
    @Test fun isFullSuccess_nonEmptyFailed_false() {
        assertThat(DeleteResult(listOf("/a"), listOf("/b" to "x")).isFullSuccess).isFalse()
    }
    @Test fun merge_combines() {
        val a = DeleteResult(listOf("/x"), listOf("/y" to "e"))
        val b = DeleteResult(listOf("/z"), listOf("/w" to "f"))
        val m = a.merge(b)
        assertThat(m.succeeded).containsExactly("/x", "/z").inOrder()
        assertThat(m.failed).containsExactly("/y" to "e", "/w" to "f").inOrder()
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement**

```kotlin
package dev.ori.core.network.model

data class DeleteResult(val succeeded: List<String>, val failed: List<Pair<String, String>>) {
    val isFullSuccess: Boolean get() = failed.isEmpty()
    fun merge(other: DeleteResult) =
        DeleteResult(succeeded + other.succeeded, failed + other.failed)
    companion object { val EMPTY = DeleteResult(emptyList(), emptyList()) }
}
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/model/DeleteResult.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/model/DeleteResultTest.kt
git commit -m "feat(network): add DeleteResult for partial-success delete semantics"
```

---

## Task 3: Create `ScpListingParser`

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/ScpListingParser.kt`
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/ScpListingParserTest.kt`

- [ ] **Step 1: Write the 11 failing tests** (cover every edge case from the spec)

```kotlin
package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import dev.ori.core.network.model.RemoteFile
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class ScpListingParserTest {
    private val cache = NameCache(mapOf(1000 to "marc", 0 to "root"), mapOf(1000 to "marc", 0 to "root"))

    @Test fun simpleFile() {
        val out = "-rw-r--r-- 1 1000 1000 1234 2026-04-26T19:25:00 hello.txt\n"
        val files = ScpListingParser.parse(out, "/home/marc", cache)
        assertThat(files).hasSize(1)
        assertThat(files[0].name).isEqualTo("hello.txt")
        assertThat(files[0].path).isEqualTo("/home/marc/hello.txt")
        assertThat(files[0].size).isEqualTo(1234L)
        assertThat(files[0].owner).isEqualTo("marc")
        assertThat(files[0].isDirectory).isFalse()
    }
    @Test fun directoryEntry() {
        val out = "drwxr-xr-x 2 1000 1000 4096 2026-04-26T19:25:00 docs\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].isDirectory).isTrue()
    }
    @Test fun spacesInFilename() {
        val out = "-rw-r--r-- 1 1000 1000 10 2026-04-26T19:25:00 my file.txt\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].name).isEqualTo("my file.txt")
    }
    @Test fun symlinkDropsTarget() {
        val out = "lrwxrwxrwx 1 1000 1000 12 2026-04-26T19:25:00 link -> /etc/hosts\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].name).isEqualTo("link")
    }
    @Test fun arrowInRegularFilenameKept() {
        val out = "-rw-r--r-- 1 1000 1000 5 2026-04-26T19:25:00 a -> b.txt\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].name).isEqualTo("a -> b.txt")
    }
    @Test fun zeroByte() {
        val out = "-rw-r--r-- 1 1000 1000 0 2026-04-26T19:25:00 empty\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].size).isEqualTo(0L)
    }
    @Test fun totalLineSkipped() {
        val out = "total 16\n-rw-r--r-- 1 1000 1000 1 2026-04-26T19:25:00 a\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)).hasSize(1)
    }
    @Test fun dotAndDotDotFiltered() {
        val out = """
            drwxr-xr-x 2 1000 1000 4096 2026-04-26T19:25:00 .
            drwxr-xr-x 3 1000 1000 4096 2026-04-26T19:25:00 ..
            -rw-r--r-- 1 1000 1000 1 2026-04-26T19:25:00 a
        """.trimIndent() + "\n"
        assertThat(ScpListingParser.parse(out, "/x", cache).map { it.name }).containsExactly("a")
    }
    @Test fun uidNotInCache() {
        val out = "-rw-r--r-- 1 9999 9999 1 2026-04-26T19:25:00 a\n"
        assertThat(ScpListingParser.parse(out, "/x", cache)[0].owner).isEqualTo("9999")
    }
    @Test fun emptyInput() {
        assertThat(ScpListingParser.parse("", "/x", cache)).isEmpty()
    }
    @Test fun bsdLsFormatRejected() {
        val out = "-rw-r--r--   1 marc  staff   1234 Apr 26 19:25 hello.txt\n"
        val ex = assertThrows(IOException::class.java) { ScpListingParser.parse(out, "/x", cache) }
        assertThat(ex.message).contains("GNU coreutils")
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement** (full file in spec; rendered here verbatim)

```kotlin
package dev.ori.core.network.ssh

import dev.ori.core.network.model.RemoteFile
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object ScpListingParser {
    private val ISO_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private const val FIELDS_BEFORE_NAME = 6
    private const val PERMS_LEN = 10

    fun parse(output: String, parentPath: String, nameCache: NameCache): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("total ")) continue
            val tokens = splitNFields(line, FIELDS_BEFORE_NAME) ?: throw badFormat(line)
            val perms = tokens[0]
            val uid = tokens[2].toIntOrNull()
            val gid = tokens[3].toIntOrNull()
            val size = tokens[4].toLongOrNull()
            val ts = parseIso(tokens[5])
            if (perms.length != PERMS_LEN || uid == null || gid == null || size == null || ts == null) {
                throw badFormat(line)
            }
            val rawName = tokens[6]
            val name = if (perms.startsWith("l")) stripSymlinkTarget(rawName) else rawName
            if (name == "." || name == "..") continue
            out += RemoteFile(
                name = name,
                path = if (parentPath.endsWith("/")) "$parentPath$name" else "$parentPath/$name",
                isDirectory = perms.startsWith("d"),
                size = size,
                lastModified = ts,
                permissions = perms,
                owner = nameCache.resolveUid(uid),
            )
        }
        return out
    }

    private fun splitNFields(line: String, n: Int): List<String>? {
        val out = mutableListOf<String>(); var i = 0
        for (k in 0 until n) {
            while (i < line.length && line[i].isWhitespace()) i++
            if (i >= line.length) return null
            val start = i
            while (i < line.length && !line[i].isWhitespace()) i++
            out += line.substring(start, i)
        }
        while (i < line.length && line[i].isWhitespace()) i++
        if (i >= line.length) return null
        out += line.substring(i)
        return out
    }
    private fun parseIso(s: String): Long? = try {
        LocalDateTime.parse(s, ISO_LOCAL).toEpochSecond(ZoneOffset.UTC) * 1000L
    } catch (_: Exception) { null }
    private fun stripSymlinkTarget(s: String): String {
        val arrow = s.indexOf(" -> "); return if (arrow >= 0) s.substring(0, arrow) else s
    }
    private fun badFormat(line: String) = IOException(
        "Unsupported server: SCP listing requires GNU coreutils. " +
        "Set the connection protocol to SFTP for this server. Offending line: $line"
    )
}
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/ScpListingParser.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/ScpListingParserTest.kt
git commit -m "feat(network): add ScpListingParser for SCP-mode directory listings"
```

---

## Task 4: Create `ShellInvocation` helper

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/ShellInvocation.kt`
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/ShellInvocationTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Command
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ShellInvocationTest {
    @Test fun bashAvailable_invokesBashNoProfileNoRc() {
        val captured = runCapturing(stdout = "ok\n", stderr = "", exit = 0, bash = true) {
            ShellInvocation.run(it, "ls /tmp", bashAvailable = true)
        }
        assertThat(captured).isEqualTo("bash --noprofile --norc -c 'ls /tmp'")
    }
    @Test fun bashUnavailable_invokesShDashC() {
        val captured = runCapturing(stdout = "", stderr = "", exit = 0, bash = false) {
            ShellInvocation.run(it, "ls /tmp", bashAvailable = false)
        }
        assertThat(captured).startsWith("sh -c '")
    }
    @Test fun innerSingleQuoteEscaped() {
        val captured = runCapturing(stdout = "", stderr = "", exit = 0, bash = false) {
            ShellInvocation.run(it, "echo 'hi'", bashAvailable = false)
        }
        assertThat(captured).isEqualTo("sh -c 'echo '\\''hi'\\'''")
    }
    @Test fun nonZeroExitReturnedAsResult() {
        var result: ShellResult? = null
        runCapturing(stdout = "", stderr = "Permission denied\n", exit = 1, bash = false) {
            result = ShellInvocation.run(it, "x", bashAvailable = false)
        }
        assertThat(result!!.exitCode).isEqualTo(1)
        assertThat(result!!.stderr).isEqualTo("Permission denied\n")
    }

    private inline fun runCapturing(
        stdout: String, stderr: String, exit: Int, bash: Boolean,
        block: (SSHClient) -> Unit,
    ): String {
        val client = mockk<SSHClient>(relaxed = true)
        val session = mockk<Session>(relaxed = true)
        val command = mockk<Command>(relaxed = true)
        val captured = slot<String>()
        every { client.startSession() } returns session
        every { session.exec (capture(captured)) } returns command
        every { command.inputStream } returns ByteArrayInputStream(stdout.toByteArray())
        every { command.errorStream } returns ByteArrayInputStream(stderr.toByteArray())
        every { command.exitStatus } returns exit
        block(client)
        return captured.captured
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement**

```kotlin
package dev.ori.core.network.ssh

import net.schmizz.sshj.SSHClient

internal data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

internal object ShellInvocation {
    fun run(client: SSHClient, innerCommand: String, bashAvailable: Boolean): ShellResult {
        val wrapped = wrap(innerCommand, bashAvailable)
        val session = client.startSession()
        try {
            val cmd = session.exec(wrapped)
            val out = cmd.inputStream.bufferedReader().readText()
            val err = cmd.errorStream.bufferedReader().readText()
            cmd.join()
            return ShellResult(cmd.exitStatus ?: -1, out, err)
        } finally { session.close() }
    }

    internal fun wrap(inner: String, bashAvailable: Boolean): String {
        val escaped = "'" + inner.replace("'", "'\\''") + "'"
        return if (bashAvailable) "bash --noprofile --norc -c $escaped" else "sh -c $escaped"
    }
}
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/ShellInvocation.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/ShellInvocationTest.kt
git commit -m "feat(network): add ShellInvocation helper for safe sh/bash command execution"
```

---

## Task 5: Create `LocalFileAdapters` (SAF Source/Dest)

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/LocalFileAdapters.kt`
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/LocalFileAdaptersTest.kt`

- [ ] **Step 1: Write failing tests** (Robolectric)

```kotlin
package dev.ori.core.network.ssh

import android.content.ContentResolver
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class LocalFileAdaptersTest {
    private val uri: Uri = Uri.parse("content://example/abc")

    @Test fun safSource_inputStream_usesResolver() {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream("hi".toByteArray())
        val src = SafSourceFile(uri, resolver, length = 2L, name = "a.txt")
        assertThat(src.inputStream.bufferedReader().readText()).isEqualTo("hi")
    }
    @Test fun safSource_resolverNull_throws() {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        val src = SafSourceFile(uri, resolver, 0L, "a.txt")
        assertThrows(IOException::class.java) { src.inputStream }
    }
    @Test fun safSource_lengthAndName() {
        val src = SafSourceFile(uri, mockk(), 42L, "foo")
        assertThat(src.length).isEqualTo(42L)
        assertThat(src.name).isEqualTo("foo")
    }
    @Test fun safDest_outputStream_usesResolver() {
        val resolver = mockk<ContentResolver>()
        val sink = ByteArrayOutputStream()
        every { resolver.openOutputStream(uri, "wt") } returns sink
        val dst = SafDestFile(uri, resolver, "a.txt")
        dst.getOutputStream(append = false).bufferedWriter().use { it.write("hi") }
        assertThat(String(sink.toByteArray())).isEqualTo("hi")
    }
    @Test fun safDest_appendThrows() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SafDestFile(uri, mockk(), "a.txt").getOutputStream(append = true)
        }
        assertThat(ex.message).contains("does not support resumable")
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement**

```kotlin
package dev.ori.core.network.ssh

import android.content.ContentResolver
import android.net.Uri
import net.schmizz.sshj.xfer.LocalDestFile
import net.schmizz.sshj.xfer.LocalFileFilter
import net.schmizz.sshj.xfer.LocalSourceFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class SafSourceFile(
    private val uri: Uri,
    private val resolver: ContentResolver,
    private val length: Long,
    private val name: String,
    private val permissions: Int = DEFAULT_PERMS,
) : LocalSourceFile {
    override fun getName() = name
    override fun getLength() = length
    override fun getInputStream(): InputStream =
        resolver.openInputStream(uri) ?: throw IOException("Cannot open input stream for $uri")
    override fun getPermissions() = permissions
    override fun isFile() = true
    override fun isDirectory() = false
    override fun getChildren(filter: LocalFileFilter?): Iterable<LocalSourceFile> = emptyList()
    override fun providesAtimeMtime() = false
    override fun getLastAccessTime() = 0L
    override fun getLastModifiedTime() = 0L
    companion object { private const val DEFAULT_PERMS = 0b110_100_100 }
}

internal class SafDestFile(
    private val uri: Uri,
    private val resolver: ContentResolver,
    private val name: String,
) : LocalDestFile {
    override fun getName() = name
    override fun getLength() = 0L
    override fun getOutputStream(append: Boolean): OutputStream {
        require(!append) { "SafDestFile does not support resumable transfers (SCP cannot resume)" }
        return resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open output stream for $uri")
    }
    override fun getOutputStream(): OutputStream = getOutputStream(false)
    override fun getTargetFile(filename: String): LocalDestFile = this
    override fun getTargetDirectory(dirname: String): LocalDestFile = this
    override fun setPermissions(perms: Int) { }
    override fun setLastAccessedTime(t: Long) { }
    override fun setLastModifiedTime(t: Long) { }
}
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/LocalFileAdapters.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/LocalFileAdaptersTest.kt
git commit -m "feat(network): add SAF-backed LocalSourceFile/LocalDestFile for SCP transfer"
```

---

## Task 6: Create `SshSessionStore` skeleton

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSessionStore.kt`
- Test: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshSessionStoreTest.kt`

- [ ] **Step 1: Write failing tests** (skeleton scope only — no probe yet)

```kotlin
package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import io.mockk.*
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.DisconnectListener
import net.schmizz.sshj.transport.Transport
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class SshSessionStoreTest {
    private val verifier = mockk<OriDevHostKeyVerifier>(relaxed = true)
    private val store = SshSessionStore(verifier)

    @Test fun getSession_unknownId_throwsIOException() {
        val ex = assertThrows(IOException::class.java) { store.getSession("nope") }
        assertThat(ex.message).contains("No active SSH session: nope")
    }
    @Test fun getSession_disconnected_removesAndThrows() {
        val client = mockk<SSHClient>(relaxed = true)
        every { client.isConnected } returns false
        injectLive(store, "id1", client, Protocol.SFTP)
        val ex = assertThrows(IOException::class.java) { store.getSession("id1") }
        assertThat(ex.message).contains("SSH session terminated")
        assertThat(assertThrows(IOException::class.java) { store.getSession("id1") }.message)
            .contains("No active")
    }
    @Test fun disconnectListener_firesOnEof_removes() {
        val client = mockk<SSHClient>(relaxed = true)
        val transport = mockk<Transport>(relaxed = true)
        every { client.transport } returns transport
        val listener = slot<DisconnectListener>()
        every { transport.disconnectListener = capture(listener) } answers { }
        injectLive(store, "id2", client, Protocol.SCP)
        invokeRegister(store, "id2", client)
        listener.captured.notifyDisconnect(net.schmizz.sshj.common.DisconnectReason.CONNECTION_LOST, "EOF")
        assertThrows(IOException::class.java) { store.getSession("id2") }
    }
    @Test fun getSession_returnsProtocol() {
        val client = mockk<SSHClient>(relaxed = true)
        every { client.isConnected } returns true
        injectLive(store, "id3", client, Protocol.SCP)
        val live = store.getSession("id3")
        assertThat(live.protocol).isEqualTo(Protocol.SCP)
        assertThat(live.client).isSameInstanceAs(client)
    }

    private fun injectLive(store: SshSessionStore, id: String, c: SSHClient, p: Protocol) {
        val f = SshSessionStore::class.java.getDeclaredField("sessions"); f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (f.get(store) as ConcurrentHashMap<String, LiveSession>)[id] =
            LiveSession(c, p, false, NameCache.empty())
    }
    private fun invokeRegister(store: SshSessionStore, id: String, c: SSHClient) {
        SshSessionStore::class.java
            .getDeclaredMethod("registerDisconnectCleanup", String::class.java, SSHClient::class.java)
            .apply { isAccessible = true }.invoke(store, id, c)
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement skeleton**

```kotlin
package dev.ori.core.network.ssh

import dev.ori.core.common.model.Protocol
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.DisconnectListener
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal data class LiveSession(
    val client: SSHClient,
    val protocol: Protocol,
    val bashAvailable: Boolean,
    @Volatile var nameCache: NameCache,
)

@Singleton
class SshSessionStore @Inject constructor(
    private val hostKeyVerifier: OriDevHostKeyVerifier,
) {
    private val sessions = ConcurrentHashMap<String, LiveSession>()

    fun getSession(sessionId: String): LiveSession {
        val live = sessions[sessionId] ?: throw IOException("No active SSH session: $sessionId")
        if (!live.client.isConnected) {
            sessions.remove(sessionId, live)
            runCatching { live.client.close() }
            throw IOException("SSH session terminated: $sessionId")
        }
        return live
    }

    suspend fun disconnect(sessionId: String) { sessions.remove(sessionId)?.client?.close() }

    fun isConnected(sessionId: String): Boolean = sessions[sessionId]?.client?.isConnected == true

    private fun registerDisconnectCleanup(sessionId: String, client: SSHClient) {
        client.transport.disconnectListener = DisconnectListener { _, _ -> sessions.remove(sessionId) }
    }
    // Task 7 will add `connect(...)` and `probeBash(...)`. Task 11 adds `ensureNameCache(...)`.
}
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSessionStore.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshSessionStoreTest.kt
git commit -m "feat(network): add SshSessionStore skeleton (sessions map + disconnect-listener)"
```

---

## Task 7: Add `connect()` + fail-safe bash-probe to `SshSessionStore`

**Files:**
- Modify: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSessionStore.kt`
- Modify: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshSessionStoreTest.kt`

- [ ] **Step 1: Append failing tests for the four probe scenarios**

```kotlin
    @Test fun probe_sentinelInStdout_returnsTrue() {
        val client = mockBenign(stdout = "BASH_OK\n", stderr = "", exit = 0)
        assertThat(invokeProbe(store, client)).isTrue()
    }
    @Test fun probe_noSentinel_returnsFalse() {
        val client = mockBenign(stdout = "something else\n", stderr = "", exit = 0)
        assertThat(invokeProbe(store, client)).isFalse()
    }
    @Test fun probe_channelOpenFails_returnsFalseFailSafe() {
        val client = mockk<SSHClient>(relaxed = true)
        every { client.startSession() } throws IOException("Channel open failure: MaxSessions exceeded")
        assertThat(invokeProbe(store, client)).isFalse()
    }
    @Test fun probe_forcedCommand_throwsSpecificIOException() {
        val client = mockBenign(stdout = "", stderr = "This account is restricted to running 'svnserve -t'\n", exit = 1)
        val ex = assertThrows(java.lang.reflect.InvocationTargetException::class.java) { invokeProbe(store, client) }
        val cause = ex.targetException as IOException
        assertThat(cause.message).contains("forced-command")
    }

    private fun mockBenign(stdout: String, stderr: String, exit: Int): SSHClient {
        val c = mockk<SSHClient>(relaxed = true)
        val s = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
        val cmd = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
        every { c.startSession() } returns s
        every { s.exec (any()) } returns cmd
        every { cmd.inputStream } returns java.io.ByteArrayInputStream(stdout.toByteArray())
        every { cmd.errorStream } returns java.io.ByteArrayInputStream(stderr.toByteArray())
        every { cmd.exitStatus } returns exit
        return c
    }
    private fun invokeProbe(store: SshSessionStore, client: SSHClient): Boolean =
        SshSessionStore::class.java
            .getDeclaredMethod("probeBash", SSHClient::class.java)
            .apply { isAccessible = true }.invoke(store, client) as Boolean
```

- [ ] **Step 2: Run, verify FAIL** (`probeBash` not found)

- [ ] **Step 3: Add `connect`, `openTransport`, `probeBash` to `SshSessionStore`**

```kotlin
    suspend fun connect(
        host: String, port: Int, username: String,
        password: CharArray?, privateKey: ByteArray?, protocol: Protocol,
    ): SshSession = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val client = openTransport(host, port, username, password, privateKey)
        val bashAvailable = probeBash(client)
        val sessionId = java.util.UUID.randomUUID().toString()
        sessions[sessionId] = LiveSession(client, protocol, bashAvailable, NameCache.empty())
        registerDisconnectCleanup(sessionId, client)
        SshSession(
            sessionId = sessionId, profileId = 0L,
            host = host, port = port, connectedAt = System.currentTimeMillis(),
        )
    }

    internal open fun openTransport(
        host: String, port: Int, username: String,
        password: CharArray?, privateKey: ByteArray?,
    ): SSHClient {
        val client = SSHClient()
        client.addHostKeyVerifier(hostKeyVerifier)
        client.connect(host, port)
        try {
            when {
                privateKey != null -> {
                    val kp = net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile()
                    kp.init(java.io.InputStreamReader(java.io.ByteArrayInputStream(privateKey)))
                    client.authPublickey(username, kp)
                }
                password != null -> client.authPassword(username, String(password))
                else -> throw IllegalArgumentException("password or privateKey required")
            }
            client.connection.keepAlive.keepAliveInterval = KEEPALIVE_INTERVAL_SECONDS
            return client
        } catch (e: Exception) {
            client.close(); throw e
        } finally {
            password?.fill(' ')
        }
    }

    private fun probeBash(client: SSHClient): Boolean {
        val result = try {
            ShellInvocation.run(client, "echo $BASH_PROBE_SENTINEL", bashAvailable = true)
        } catch (_: Exception) {
            return false
        }
        if (FORCED_COMMAND_PATTERN.containsMatchIn(result.stderr)) {
            throw IOException(
                "Server appears to use a forced-command authorized_keys configuration; " +
                "SCP requires unrestricted shell access."
            )
        }
        return result.stdout.trim() == BASH_PROBE_SENTINEL
    }

    companion object {
        private const val KEEPALIVE_INTERVAL_SECONDS = 15
        private const val BASH_PROBE_SENTINEL = "BASH_OK"
        private val FORCED_COMMAND_PATTERN = Regex(
            """This account is restricted|forced[- ]command|command="""",
            RegexOption.IGNORE_CASE
        )
    }
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSessionStore.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshSessionStoreTest.kt
git commit -m "feat(network): SshSessionStore.connect with fail-safe bash probe"
```

---

## Task 8: Modify `SshClient` interface

**Files:**
- Modify: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClient.kt`

- [ ] **Step 1: Read current interface**

`cat core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClient.kt`

- [ ] **Step 2: Apply 3 changes to the interface**

1. `connect(...)` gains `protocol: Protocol` parameter.
2. `delete(sessionId: String, path: String)` becomes `delete(sessionId: String, paths: List<String>): DeleteResult`.
3. Add two SAF overloads:

```kotlin
suspend fun uploadFile(
    sessionId: String, sourceUri: android.net.Uri, remotePath: String,
    contentResolver: android.content.ContentResolver,
    onProgress: (transferred: Long, total: Long) -> Unit = { _, _ -> },
)
suspend fun downloadFile(
    sessionId: String, remotePath: String, destUri: android.net.Uri,
    contentResolver: android.content.ContentResolver,
    onProgress: (transferred: Long, total: Long) -> Unit = { _, _ -> },
)
```

Imports: `dev.ori.core.common.model.Protocol`, `dev.ori.core.network.model.DeleteResult`.

- [ ] **Step 3: Add temporary `TODO` stubs in `SshClientImpl` so compilation passes**

```kotlin
override suspend fun delete(sessionId: String, paths: List<String>): DeleteResult =
    TODO("see Task 9 — partial-failure delete walk")
override suspend fun uploadFile(
    sessionId: String, sourceUri: android.net.Uri, remotePath: String,
    contentResolver: android.content.ContentResolver,
    onProgress: (Long, Long) -> Unit,
) { TODO("see Task 9 — SAF overload via temp file") }
// (mirror downloadFile)
```

In `RemoteFileSystemRepository.deleteFile`, temporarily wrap the new shape:

```kotlin
override suspend fun deleteFile(path: String) {
    val r = client().delete(requireSession(), listOf(path))
    if (!r.isFullSuccess) throw IOException("Delete failed: ${r.failed.firstOrNull()?.second ?: "unknown"}")
}
```

- [ ] **Step 4: Compile**

`./gradlew :core:core-network:compileDebugKotlin :data:compileDebugKotlin :app:compileDebugKotlin`

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClient.kt \
        core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClientImpl.kt \
        data/src/main/kotlin/dev/ori/data/repository/RemoteFileSystemRepository.kt
git commit -m "refactor(network): SshClient interface — Protocol param, DeleteResult, SAF overloads"
```

---

## Task 9: Rename `SshClientImpl` → `SshSftpClientImpl`; delegate transport; partial-failure delete; SAF overloads

**Files:**
- Rename: `SshClientImpl.kt` → `SshSftpClientImpl.kt` (and test counterpart)
- Modify both files

- [ ] **Step 1: `git mv` the files; rename the class symbols**

```bash
git mv core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClientImpl.kt \
       core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSftpClientImpl.kt
git mv core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshClientImplTest.kt \
       core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshSftpClientImplTest.kt
```

In both files, rename `SshClientImpl` → `SshSftpClientImpl`.

- [ ] **Step 2: Replace local sessions-map state with `SshSessionStore` delegation**

Replace the field `private val sessions = ConcurrentHashMap<String, SSHClient>()` with `@Inject`-ed `private val sessionStore: SshSessionStore`. In every method, swap `sessions[sessionId] ?: throw …` and `getClient(sessionId)` with `sessionStore.getSession(sessionId).client`. Replace `connect/disconnect/isConnected` bodies with delegation to the store. Remove `registerDisconnectCleanup` from this file.

- [ ] **Step 3: Add the failing partial-failure delete test**

```kotlin
    @Test
    fun delete_oneOfThreeFails_returnsDeleteResultWithBoth() = kotlinx.coroutines.test.runTest {
        every { sftp.rm("/a") } just runs
        every { sftp.rm("/b") } throws net.schmizz.sshj.sftp.SFTPException("Permission denied")
        every { sftp.rm("/c") } just runs
        val result = sshClient.delete(sessionId, listOf("/a", "/b", "/c"))
        assertThat(result.succeeded).containsExactly("/a", "/c").inOrder()
        assertThat(result.failed).hasSize(1)
        assertThat(result.failed[0].first).isEqualTo("/b")
        assertThat(result.failed[0].second).contains("Permission denied")
    }
```

- [ ] **Step 4: Run, verify FAIL**

- [ ] **Step 5: Implement `delete` and SAF overloads in `SshSftpClientImpl`**

```kotlin
override suspend fun delete(sessionId: String, paths: List<String>): DeleteResult =
    withContext(Dispatchers.IO) {
        val ok = mutableListOf<String>()
        val bad = mutableListOf<Pair<String, String>>()
        withSftpClient(sessionId) { sftp ->
            for (p in paths) {
                try { sftp.rm(p); ok += p }
                catch (e: Exception) { bad += p to (e.message ?: e.javaClass.simpleName) }
            }
        }
        DeleteResult(ok, bad)
    }

override suspend fun uploadFile(
    sessionId: String, sourceUri: android.net.Uri, remotePath: String,
    contentResolver: android.content.ContentResolver,
    onProgress: (Long, Long) -> Unit,
) = withContext(Dispatchers.IO) {
    val tmp = java.io.File.createTempFile("oridev_saf_upload_", ".tmp")
    try {
        contentResolver.openInputStream(sourceUri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open input stream for $sourceUri")
        uploadFile(sessionId, tmp.absolutePath, remotePath, onProgress)
    } finally { tmp.delete() }
}

override suspend fun downloadFile(
    sessionId: String, remotePath: String, destUri: android.net.Uri,
    contentResolver: android.content.ContentResolver,
    onProgress: (Long, Long) -> Unit,
) = withContext(Dispatchers.IO) {
    val tmp = java.io.File.createTempFile("oridev_saf_download_", ".tmp")
    try {
        downloadFile(sessionId, remotePath, tmp.absolutePath, onProgress)
        contentResolver.openOutputStream(destUri, "wt")?.use { out ->
            tmp.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("Cannot open output stream for $destUri")
    } finally { tmp.delete() }
}
```

- [ ] **Step 6: Run, verify PASS**

- [ ] **Step 7: Commit**

```bash
git add -A core/core-network/
git commit -m "refactor(network): rename SshClientImpl to SshSftpClientImpl; delegate transport; partial-failure delete + SAF overloads"
```

---

## Task 10: Create `SshScpClientImpl` skeleton

**Files:**
- Create: `core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshScpClientImpl.kt`
- Create: `core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshScpClientImplTest.kt`

- [ ] **Step 1: Write three failing tests** (delegation + Resume-throws)

```kotlin
package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.Protocol
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SshScpClientImplTest {
    val store = mockk<SshSessionStore>(relaxed = true)
    val shellManager = mockk<SshShellManager>(relaxed = true)
    val sshClient = SshScpClientImpl(store, shellManager)

    @Test fun isConnected_delegates() = kotlinx.coroutines.test.runTest {
        every { store.isConnected("s1") } returns true
        assertThat(sshClient.isConnected("s1")).isTrue()
    }
    @Test fun uploadFileResumable_throws() = kotlinx.coroutines.test.runTest {
        val ex = assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { sshClient.uploadFileResumable("s1", "/local", "/remote", 0L) }
        }
        assertThat(ex.message).contains("SCP does not support resume")
    }
    @Test fun downloadFileResumable_throws() = kotlinx.coroutines.test.runTest {
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { sshClient.downloadFileResumable("s1", "/remote", "/local", 0L) }
        }
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement skeleton**

```kotlin
package dev.ori.core.network.ssh

import android.content.ContentResolver
import android.net.Uri
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.model.DeleteResult
import dev.ori.core.network.model.RemoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshScpClientImpl @Inject constructor(
    private val sessionStore: SshSessionStore,
    private val shellManager: SshShellManager,
) : SshClient {
    override suspend fun connect(
        host: String, port: Int, username: String,
        password: CharArray?, privateKey: ByteArray?, protocol: Protocol,
    ): SshSession = sessionStore.connect(host, port, username, password, privateKey, protocol)

    override suspend fun disconnect(sessionId: String) = sessionStore.disconnect(sessionId)
    override suspend fun isConnected(sessionId: String): Boolean = sessionStore.isConnected(sessionId)

    override suspend fun openShell(sessionId: String, cols: Int, rows: Int, term: String): ShellHandle =
        withContext(Dispatchers.IO) {
            shellManager.openShell(sessionStore.getSession(sessionId).client, cols, rows, term)
        }

    override suspend fun executeCommand(sessionId: String, command: String): CommandResult =
        withContext(Dispatchers.IO) {
            val live = sessionStore.getSession(sessionId)
            val r = ShellInvocation.run(live.client, command, live.bashAvailable)
            CommandResult(exitCode = r.exitCode, stdout = r.stdout, stderr = r.stderr)
        }

    override suspend fun listFiles(sessionId: String, path: String): List<RemoteFile> =
        TODO("Task 11")
    override suspend fun uploadFile(sessionId: String, localPath: String, remotePath: String, onProgress: (Long, Long) -> Unit) =
        TODO("Task 12")
    override suspend fun uploadFile(sessionId: String, sourceUri: Uri, remotePath: String, contentResolver: ContentResolver, onProgress: (Long, Long) -> Unit) =
        TODO("Task 12")
    override suspend fun downloadFile(sessionId: String, remotePath: String, localPath: String, onProgress: (Long, Long) -> Unit) =
        TODO("Task 12")
    override suspend fun downloadFile(sessionId: String, remotePath: String, destUri: Uri, contentResolver: ContentResolver, onProgress: (Long, Long) -> Unit) =
        TODO("Task 12")
    override suspend fun uploadFileResumable(sessionId: String, localPath: String, remotePath: String, offsetBytes: Long, onProgress: suspend (Long, Long) -> Unit): Unit =
        throw UnsupportedOperationException("SCP does not support resume")
    override suspend fun downloadFileResumable(sessionId: String, remotePath: String, localPath: String, offsetBytes: Long, onProgress: suspend (Long, Long) -> Unit): Unit =
        throw UnsupportedOperationException("SCP does not support resume")
    override suspend fun mkdir(sessionId: String, path: String) = TODO("Task 13")
    override suspend fun rename(sessionId: String, oldPath: String, newPath: String) = TODO("Task 13")
    override suspend fun chmod(sessionId: String, path: String, octalPermissions: Int) = TODO("Task 13")
    override suspend fun delete(sessionId: String, paths: List<String>): DeleteResult = TODO("Task 14")
    override suspend fun fileSize(sessionId: String, path: String): Long? = TODO("Task 13")
}
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshScpClientImpl.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshScpClientImplTest.kt
git commit -m "feat(network): SshScpClientImpl skeleton (transport delegated, file-ops stubbed)"
```

---

## Task 11: SCP `listFiles` + name-cache populate

**Files:**
- Modify: `SshSessionStore.kt`, `SshScpClientImpl.kt`, `SshScpClientImplTest.kt`

- [ ] **Step 1: Add `ensureNameCache` to `SshSessionStore`**

```kotlin
    private val cacheMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun ensureNameCache(sessionId: String): NameCache = cacheMutex.let { m ->
        kotlinx.coroutines.sync.withLock(m) {
            val live = getSession(sessionId)
            if (live.nameCache.uids.isNotEmpty() || live.nameCache.gids.isNotEmpty()) live.nameCache
            else {
                val cache = try { fetchNameCache(live.client, live.bashAvailable) } catch (_: Exception) { NameCache.empty() }
                live.nameCache = cache
                cache
            }
        }
    }
    private fun fetchNameCache(client: SSHClient, bashAvailable: Boolean): NameCache {
        val pw = ShellInvocation.run(client, "getent passwd 2>/dev/null || cat /etc/passwd", bashAvailable)
        val gr = ShellInvocation.run(client, "getent group 2>/dev/null || cat /etc/group", bashAvailable)
        val uids = pw.stdout.lineSequence().mapNotNull { line ->
            val p = line.split(':'); if (p.size >= 3) p[2].toIntOrNull()?.let { it to p[0] } else null
        }.toMap()
        val gids = gr.stdout.lineSequence().mapNotNull { line ->
            val p = line.split(':'); if (p.size >= 3) p[2].toIntOrNull()?.let { it to p[0] } else null
        }.toMap()
        return NameCache(uids, gids)
    }
```

- [ ] **Step 2: Write 2 failing tests for `SshScpClientImpl.listFiles`**

```kotlin
    @Test fun listFiles_runsAndParses() = kotlinx.coroutines.test.runTest {
        val client = mockk<SSHClient>(relaxed = true)
        val live = LiveSession(client, Protocol.SCP, true,
            NameCache(mapOf(1000 to "marc"), mapOf(1000 to "marc")))
        every { store.getSession("s1") } returns live
        coEvery { store.ensureNameCache("s1") } returns live.nameCache
        val session = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
        val command = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
        every { client.startSession() } returns session
        every { session.exec (any()) } returns command
        every { command.inputStream } returns java.io.ByteArrayInputStream(
            "-rw-r--r-- 1 1000 1000 5 2026-04-26T19:25:00 hello\n".toByteArray())
        every { command.errorStream } returns java.io.ByteArrayInputStream(ByteArray(0))
        every { command.exitStatus } returns 0
        val files = sshClient.listFiles("s1", "/home/marc")
        assertThat(files).hasSize(1)
        assertThat(files[0].name).isEqualTo("hello")
        assertThat(files[0].owner).isEqualTo("marc")
    }
    @Test fun listFiles_lsExitNonZero_throwsWithStderr() = kotlinx.coroutines.test.runTest {
        val client = mockk<SSHClient>(relaxed = true)
        val live = LiveSession(client, Protocol.SCP, false, NameCache.empty())
        every { store.getSession("s1") } returns live
        coEvery { store.ensureNameCache("s1") } returns NameCache.empty()
        val session = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
        val command = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
        every { client.startSession() } returns session
        every { session.exec (any()) } returns command
        every { command.inputStream } returns java.io.ByteArrayInputStream(ByteArray(0))
        every { command.errorStream } returns java.io.ByteArrayInputStream(
            "ls: cannot open directory '/root': Permission denied\n".toByteArray())
        every { command.exitStatus } returns 2
        val ex = assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { sshClient.listFiles("s1", "/root") }
        }
        assertThat(ex.message).contains("Permission denied")
    }
```

- [ ] **Step 3: Run, verify FAIL**

- [ ] **Step 4: Implement `listFiles`**

```kotlin
override suspend fun listFiles(sessionId: String, path: String): List<RemoteFile> =
    withContext(Dispatchers.IO) {
        val live = sessionStore.getSession(sessionId)
        val cache = sessionStore.ensureNameCache(sessionId)
        val cmd = "LANG=C ls -la --numeric-uid-gid --time-style='+%Y-%m-%dT%H:%M:%S' ${shellEscape(path)}"
        val r = ShellInvocation.run(live.client, cmd, live.bashAvailable)
        if (r.exitCode != 0) {
            val first = r.stderr.lineSequence().firstOrNull()?.trim().orEmpty()
            throw java.io.IOException("ls failed: ${first.ifEmpty { "exit ${r.exitCode}" }}")
        }
        ScpListingParser.parse(r.stdout, parentPath = path, nameCache = cache)
    }

internal fun shellEscape(path: String): String = "'" + path.replace("'", "'\\''") + "'"
```

- [ ] **Step 5: Run, verify PASS**

- [ ] **Step 6: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshSessionStore.kt \
        core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshScpClientImpl.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshScpClientImplTest.kt
git commit -m "feat(network): SCP listFiles via ls + once-per-session name cache"
```

---

## Task 12: SCP upload/download (path + SAF overloads)

**Files:**
- Modify: `SshScpClientImpl.kt`, `SshScpClientImplTest.kt`

- [ ] **Step 1: Add 2 failing tests**

```kotlin
    @Test fun uploadFile_path_callsScpFileTransferUpload() = kotlinx.coroutines.test.runTest {
        val client = mockk<SSHClient>(relaxed = true)
        val transfer = mockk<net.schmizz.sshj.xfer.scp.SCPFileTransfer>(relaxed = true)
        every { client.newSCPFileTransfer() } returns transfer
        every { store.getSession("s1") } returns LiveSession(client, Protocol.SCP, true, NameCache.empty())
        sshClient.uploadFile("s1", "/local/file", "/remote/file") { _, _ -> }
        verify { transfer.upload(any<net.schmizz.sshj.xfer.LocalSourceFile>(), "/remote/file") }
    }
    @Test fun uploadFile_safUri_streamsViaSafSourceFile() = kotlinx.coroutines.test.runTest {
        val client = mockk<SSHClient>(relaxed = true)
        val transfer = mockk<net.schmizz.sshj.xfer.scp.SCPFileTransfer>(relaxed = true)
        every { client.newSCPFileTransfer() } returns transfer
        every { store.getSession("s1") } returns LiveSession(client, Protocol.SCP, true, NameCache.empty())
        val resolver = mockk<android.content.ContentResolver>()
        val uri = android.net.Uri.parse("content://x/abc")
        every { resolver.openInputStream(uri) } returns java.io.ByteArrayInputStream("data".toByteArray())
        sshClient.uploadFile("s1", uri, "/remote/file", resolver) { _, _ -> }
        verify {
            transfer.upload(
                match<net.schmizz.sshj.xfer.LocalSourceFile> { it is SafSourceFile },
                "/remote/file"
            )
        }
    }
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement the four overloads**

```kotlin
override suspend fun uploadFile(sessionId: String, localPath: String, remotePath: String, onProgress: (Long, Long) -> Unit) =
    withContext(Dispatchers.IO) {
        val client = sessionStore.getSession(sessionId).client
        client.newSCPFileTransfer()
            .upload(net.schmizz.sshj.xfer.FileSystemFile(java.io.File(localPath)), remotePath)
    }
override suspend fun uploadFile(
    sessionId: String, sourceUri: Uri, remotePath: String,
    contentResolver: ContentResolver, onProgress: (Long, Long) -> Unit,
) = withContext(Dispatchers.IO) {
    val client = sessionStore.getSession(sessionId).client
    val length = try {
        contentResolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: 0L
    } catch (_: Exception) { 0L }
    val src = SafSourceFile(sourceUri, contentResolver, length, sourceUri.lastPathSegment ?: "upload")
    client.newSCPFileTransfer().upload(src, remotePath)
}
override suspend fun downloadFile(sessionId: String, remotePath: String, localPath: String, onProgress: (Long, Long) -> Unit) =
    withContext(Dispatchers.IO) {
        val client = sessionStore.getSession(sessionId).client
        client.newSCPFileTransfer()
            .download(remotePath, net.schmizz.sshj.xfer.FileSystemFile(java.io.File(localPath)))
    }
override suspend fun downloadFile(
    sessionId: String, remotePath: String, destUri: Uri,
    contentResolver: ContentResolver, onProgress: (Long, Long) -> Unit,
) = withContext(Dispatchers.IO) {
    val client = sessionStore.getSession(sessionId).client
    val dst = SafDestFile(destUri, contentResolver, destUri.lastPathSegment ?: "download")
    client.newSCPFileTransfer().download(remotePath, dst)
}
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshScpClientImpl.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshScpClientImplTest.kt
git commit -m "feat(network): SCP upload/download via SCPFileTransfer + SAF adapters"
```

---

## Task 13: SCP simple file-ops (mkdir, rename, chmod, fileSize)

**Files:**
- Modify: `SshScpClientImpl.kt`, `SshScpClientImplTest.kt`

- [ ] **Step 1: Add 5 failing tests** (mkdir/rename/chmod/fileSize-ok/fileSize-fail)

```kotlin
    @Test fun mkdir_runsMkdirP() = kotlinx.coroutines.test.runTest {
        val captured = setupExec(stdout = "", stderr = "", exit = 0)
        sshClient.mkdir("s1", "/foo/bar")
        assertThat(captured.captured).contains("mkdir -p '/foo/bar'")
    }
    @Test fun rename_runsMv() = kotlinx.coroutines.test.runTest {
        val captured = setupExec(stdout = "", stderr = "", exit = 0)
        sshClient.rename("s1", "/a", "/b")
        assertThat(captured.captured).contains("mv -- '/a' '/b'")
    }
    @Test fun chmod_runsChmodOctal() = kotlinx.coroutines.test.runTest {
        val captured = setupExec(stdout = "", stderr = "", exit = 0)
        sshClient.chmod("s1", "/foo", 0b111_101_101) // 0755
        assertThat(captured.captured).contains("chmod 755 '/foo'")
    }
    @Test fun fileSize_returnsSize() = kotlinx.coroutines.test.runTest {
        setupExec(stdout = "12345\n", stderr = "", exit = 0)
        assertThat(sshClient.fileSize("s1", "/foo")).isEqualTo(12345L)
    }
    @Test fun fileSize_statFails_returnsNull() = kotlinx.coroutines.test.runTest {
        setupExec(stdout = "", stderr = "stat: cannot stat '/missing'\n", exit = 1)
        assertThat(sshClient.fileSize("s1", "/missing")).isNull()
    }

    private fun setupExec(stdout: String, stderr: String, exit: Int): io.mockk.CapturingSlot<String> {
        val client = mockk<SSHClient>(relaxed = true)
        every { store.getSession("s1") } returns LiveSession(client, Protocol.SCP, false, NameCache.empty())
        val s = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
        val c = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
        every { client.startSession() } returns s
        val captured = slot<String>()
        every { s.exec (capture(captured)) } returns c
        every { c.inputStream } returns java.io.ByteArrayInputStream(stdout.toByteArray())
        every { c.errorStream } returns java.io.ByteArrayInputStream(stderr.toByteArray())
        every { c.exitStatus } returns exit
        return captured
    }
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement**

```kotlin
override suspend fun mkdir(sessionId: String, path: String) =
    runShellOrFail(sessionId, "mkdir", "mkdir -p ${shellEscape(path)}")
override suspend fun rename(sessionId: String, oldPath: String, newPath: String) =
    runShellOrFail(sessionId, "rename", "mv -- ${shellEscape(oldPath)} ${shellEscape(newPath)}")
override suspend fun chmod(sessionId: String, path: String, octalPermissions: Int) {
    val asOctal = Integer.toOctalString(octalPermissions).padStart(3, '0')
    runShellOrFail(sessionId, "chmod", "chmod $asOctal ${shellEscape(path)}")
}
override suspend fun fileSize(sessionId: String, path: String): Long? = withContext(Dispatchers.IO) {
    val live = sessionStore.getSession(sessionId)
    val r = ShellInvocation.run(live.client, "stat -c %s ${shellEscape(path)}", live.bashAvailable)
    if (r.exitCode != 0) null else r.stdout.trim().toLongOrNull()
}
private suspend fun runShellOrFail(sessionId: String, verb: String, inner: String) =
    withContext(Dispatchers.IO) {
        val live = sessionStore.getSession(sessionId)
        val r = ShellInvocation.run(live.client, inner, live.bashAvailable)
        if (r.exitCode != 0) {
            val first = r.stderr.lineSequence().firstOrNull()?.trim().orEmpty()
            throw java.io.IOException("$verb failed: ${first.ifEmpty { "exit ${r.exitCode}" }}")
        }
    }
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshScpClientImpl.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshScpClientImplTest.kt
git commit -m "feat(network): SCP mkdir/rename/chmod/fileSize via shell invocation"
```

---

## Task 14: SCP `delete` with batching + DeleteResult

**Files:**
- Modify: `SshScpClientImpl.kt`, `SshScpClientImplTest.kt`

- [ ] **Step 1: Add 2 failing tests**

```kotlin
    @Test fun delete_oneFails_returnsDeleteResult() = kotlinx.coroutines.test.runTest {
        setupExec(stdout = "", stderr = "rm: cannot remove '/b': Permission denied\n", exit = 1)
        val r = sshClient.delete("s1", listOf("/a", "/b", "/c"))
        assertThat(r.failed.map { it.first }).containsExactly("/b")
        assertThat(r.succeeded).containsExactly("/a", "/c").inOrder()
    }
    @Test fun delete_450Paths_makes3Batches() = kotlinx.coroutines.test.runTest {
        // Setup: 3 successive exec invocations all return exit 0.
        val client = mockk<SSHClient>(relaxed = true)
        every { store.getSession("s1") } returns LiveSession(client, Protocol.SCP, false, NameCache.empty())
        val s = mockk<net.schmizz.sshj.connection.channel.direct.Session>(relaxed = true)
        val c = mockk<net.schmizz.sshj.connection.channel.direct.Session.Command>(relaxed = true)
        every { client.startSession() } returns s
        every { s.exec (any()) } returns c
        every { c.inputStream } returns java.io.ByteArrayInputStream(ByteArray(0))
        every { c.errorStream } returns java.io.ByteArrayInputStream(ByteArray(0))
        every { c.exitStatus } returns 0
        sshClient.delete("s1", (1..450).map { "/p$it" })
        verify(exactly = 3) { client.startSession() }
    }
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement**

```kotlin
override suspend fun delete(sessionId: String, paths: List<String>): DeleteResult =
    withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext DeleteResult.EMPTY
        val live = sessionStore.getSession(sessionId)
        var aggregate = DeleteResult.EMPTY
        for (batch in paths.chunked(MAX_BATCH_ARGS)) {
            val joined = batch.joinToString(" ") { shellEscape(it) }
            val r = ShellInvocation.run(live.client, "rm -- $joined", live.bashAvailable)
            aggregate = aggregate.merge(parseRm(batch, r))
        }
        aggregate
    }

private fun parseRm(batch: List<String>, r: ShellResult): DeleteResult {
    if (r.exitCode == 0) return DeleteResult(succeeded = batch, failed = emptyList())
    val rx = Regex("""rm: cannot remove '([^']+)': (.+)""")
    val failed = r.stderr.lineSequence().mapNotNull { line ->
        rx.matchEntire(line.trim())?.let { it.groupValues[1] to it.groupValues[2] }
    }.toList()
    val failedSet = failed.map { it.first }.toSet()
    return DeleteResult(succeeded = batch.filter { it !in failedSet }, failed = failed)
}
companion object { private const val MAX_BATCH_ARGS = 200 }
```

- [ ] **Step 4: Run, verify PASS**

- [ ] **Step 5: Commit**

```bash
git add core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshScpClientImpl.kt \
        core/core-network/src/test/kotlin/dev/ori/core/network/ssh/SshScpClientImplTest.kt
git commit -m "feat(network): SCP delete with 200-arg batching and partial-failure DeleteResult"
```

---

## Task 15: Hilt `SshClientModule`

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/di/SshModule.kt`

- [ ] **Step 1: Create the module**

```kotlin
package dev.ori.data.di

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.network.ssh.SshScpClientImpl
import dev.ori.core.network.ssh.SshSftpClientImpl
import javax.inject.Qualifier

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class ProtocolKey(val value: Protocol)

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultSshClient

@Module
@InstallIn(SingletonComponent::class)
abstract class SshClientModule {
    @Binds @IntoMap @ProtocolKey(Protocol.SFTP)
    abstract fun bindSftp(impl: SshSftpClientImpl): SshClient
    @Binds @IntoMap @ProtocolKey(Protocol.SCP)
    abstract fun bindScp(impl: SshScpClientImpl): SshClient
    @Binds @IntoMap @ProtocolKey(Protocol.SSH)
    abstract fun bindSshAsSftp(impl: SshSftpClientImpl): SshClient
    @Binds @DefaultSshClient
    abstract fun bindDefault(impl: SshSftpClientImpl): SshClient
}
```

- [ ] **Step 2: Compile**

`./gradlew :data:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add data/src/main/kotlin/dev/ori/data/di/SshModule.kt
git commit -m "feat(di): SshClientModule binds @IntoMap by Protocol + @DefaultSshClient qualifier"
```

---

## Task 16: Route `RemoteFileSystemRepository` via the protocol map

**Files:**
- Modify: `RemoteFileSystemRepository.kt`, `Session.kt`, `RemoteFileSystemRepositoryTest.kt`

- [ ] **Step 1: Add `protocol: Protocol` to `Session`**

`grep -rn "data class Session\b" domain/ data/ feature-*/`. Add the field. Update every `Session(...)` constructor call site to pass it (typically `profile.protocol`).

- [ ] **Step 2: Add 2 failing tests**

```kotlin
    @Test fun listFiles_protocolScp_routesToScpClient() = runTest {
        val sftp = mockk<SshClient>(relaxed = true)
        val scp = mockk<SshClient>(relaxed = true)
        coEvery { scp.listFiles(any(), "/x") } returns emptyList()
        val registry = mockk<SessionRegistry>()
        every { registry.openSessions } returns MutableStateFlow(
            listOf(Session(id = "s1", protocol = Protocol.SCP, /* … */))
        )
        val repo = RemoteFileSystemRepository(
            mapOf(Protocol.SFTP to sftp, Protocol.SCP to scp), registry
        )
        repo.setActiveSession("s1")
        repo.listFiles("/x")
        verify(exactly = 0) { sftp.listFiles(any(), any()) }
        coVerify(exactly = 1) { scp.listFiles("s1", "/x") }
    }
    @Test fun deleteFile_root_throwsBeforeReachingClient() = runTest {
        val client = mockk<SshClient>(relaxed = true)
        val repo = RemoteFileSystemRepository(mapOf(Protocol.SFTP to client), mockk(relaxed = true))
        repo.setActiveSession("s1")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.deleteFile("/") }
        }
        coVerify(exactly = 0) { client.delete(any(), any()) }
    }
```

- [ ] **Step 3: Run, verify FAIL**

- [ ] **Step 4: Modify `RemoteFileSystemRepository`**

```kotlin
@Singleton
class RemoteFileSystemRepository @Inject constructor(
    private val clients: Map<Protocol, @JvmSuppressWildcards SshClient>,
    private val sessionRegistry: SessionRegistry,
) : FileSystemRepository, RemoteFileSystemSession {
    private val activeSessionId = AtomicReference<String?>(null)
    override fun setActiveSession(sessionId: String) { activeSessionId.set(sessionId) }
    private fun requireSession(): String =
        activeSessionId.get() ?: throw IOException("No active SSH session")
    private fun client(): SshClient {
        val sid = requireSession()
        val proto = sessionRegistry.openSessions.value.firstOrNull { it.id == sid }?.protocol
            ?: throw IOException("Session $sid not in registry")
        return clients[proto] ?: throw IOException("No SshClient registered for protocol $proto")
    }

    override suspend fun listFiles(path: String): List<FileItem> =
        client().listFiles(requireSession(), path).map { it.toFileItem() }

    override suspend fun deleteFile(path: String) {
        require(path.isNotBlank() && path != "/") { "Refusing to delete protected path: '$path'" }
        val r = client().delete(requireSession(), listOf(path))
        if (!r.isFullSuccess) throw IOException("Delete failed: ${r.failed.firstOrNull()?.second ?: "unknown"}")
    }
    // Other methods (rename, mkdir, chmod, etc.) follow the same `client()` pattern.
}
```

- [ ] **Step 5: Run, verify PASS**

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/dev/ori/data/repository/RemoteFileSystemRepository.kt \
        domain/src/main/kotlin/dev/ori/domain/model/Session.kt \
        data/src/test/kotlin/dev/ori/data/repository/RemoteFileSystemRepositoryTest.kt
git commit -m "refactor(data): route RemoteFileSystemRepository via protocol-keyed SshClient map"
```

---

## Task 17: Update `SessionRegistry` and `WearMessageListenerService`

**Files:**
- Modify: `SessionRegistry.kt`, `WearMessageListenerService.kt`, companion test files

- [ ] **Step 1: Constructor change in both classes**

Replace `private val sshClient: SshClient` with `private val clients: Map<Protocol, @JvmSuppressWildcards SshClient>`. Inside any body that calls a `SshClient` method, look up `clients[profile.protocol] ?: throw IOException("Protocol ${profile.protocol} not supported")` first, then call.

- [ ] **Step 2: Update tests**

Replace single-mock injection with map injection. Add one test in each that verifies routing-by-protocol uses the right client.

- [ ] **Step 3: Run unit tests**

`./gradlew :data:testDebugUnitTest :app:testDebugUnitTest`

- [ ] **Step 4: Commit**

```bash
git add -A data/ app/
git commit -m "refactor(registry,wear): inject Map<Protocol, SshClient> for protocol-routed connect+exec"
```

---

## Task 18: Audit + Hilt-wiring smoke test

**Files:**
- Create: `app/src/androidTest/kotlin/dev/ori/app/di/SshClientModuleHiltTest.kt`

- [ ] **Step 1: Audit grep**

```bash
grep -rn '@Inject.*SshClient\b' --include="*.kt" .
```

For every result NOT in `core/core-network/`, classify "uses Map (protocol-routed)" vs "uses @DefaultSshClient (legacy)". Document the table inline in the PR description so the reviewer can verify exhaustiveness.

- [ ] **Step 2: Write the smoke test**

```kotlin
@HiltAndroidTest
class SshClientModuleHiltTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var clients: Map<Protocol, @JvmSuppressWildcards SshClient>
    @Inject @DefaultSshClient lateinit var defaultClient: SshClient
    @Before fun setUp() = hiltRule.inject()

    @Test fun protocolMap_resolvesAllSshProtocols() {
        assertThat(clients.keys).containsAtLeast(Protocol.SFTP, Protocol.SCP, Protocol.SSH)
        assertThat(clients[Protocol.SFTP]).isInstanceOf(SshSftpClientImpl::class.java)
        assertThat(clients[Protocol.SCP]).isInstanceOf(SshScpClientImpl::class.java)
    }
    @Test fun defaultClient_isSftp() {
        assertThat(defaultClient).isInstanceOf(SshSftpClientImpl::class.java)
    }
}
```

- [ ] **Step 3: Run on connected device or emulator**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "dev.ori.app.di.SshClientModuleHiltTest"
```

(If no emulator, this can be skipped locally and verified in CI.)

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/dev/ori/app/di/SshClientModuleHiltTest.kt
git commit -m "test(di): Hilt smoke test for SshClient @IntoMap and @DefaultSshClient bindings"
```

---

## Task 19: Release notes + final detekt + push

**Files:**
- Modify: `RELEASE.md`

- [ ] **Step 1: Append the release-notes line**

Add to the next-version section of `RELEASE.md`:

> SCP is now a real protocol with distinct wire behaviour, no longer a silent SFTP alias. If you previously selected SCP in a connection profile and it surprisingly stops working, switch the protocol to SFTP — the listing failure includes a hint to do so.

- [ ] **Step 2: Run detekt + the full unit-test set**

```bash
./gradlew detekt :core:core-network:test :data:testDebugUnitTest :app:testDebugUnitTest
```

Fix any detekt findings inline.

- [ ] **Step 3: Commit + push**

```bash
git add RELEASE.md
git commit -m "docs(release): note SCP becoming a real protocol in v0.34.5"
git push -u origin feat/scp-protocol-implementation
```

- [ ] **Step 4: Open PR + enable auto-merge**

```bash
gh pr create --base master --head feat/scp-protocol-implementation \
  --title "feat(network): real SCP protocol implementation (v0.34.5)" \
  --body "Implements docs/superpowers/specs/2026-04-26-scp-protocol-implementation-design.md.

Tasks 1–18 from docs/superpowers/plans/2026-04-26-scp-protocol-implementation.md."

gh pr merge <number> --auto --squash --delete-branch
```

CI watcher: follow the existing CICD-quirks pattern. After merge, the master release-workflow runs and tags v0.34.5.

---

## Self-Review

- [x] **Spec coverage:** Decision #1 → T8 (interface change locks out the alias). #4 + #9 → T15. #5 → T9 (SFTP) + T14 (SCP). #6 → T11. #7 → T7. #8 → T10. #10 → T5 + T12. #11 → T11/T13/T14 (every shell call validates exit code).
- [x] **Placeholder scan:** No "TBD" or unresolved TODO in plan body. The intentional `TODO("Task N")` markers in T8/T10 are explicitly resolved by named subsequent tasks.
- [x] **Type consistency:** `LiveSession`, `NameCache`, `DeleteResult`, `ShellResult`, `ProtocolKey`, `DefaultSshClient`, `SafSourceFile`/`SafDestFile` referenced consistently across tasks.
- [x] **Out-of-scope items honoured:** stale-after-resume, BSD `ls`, forced-command, name-cache invalidation — none have implementation tasks; all are documented in the spec's "Out of scope" block.
