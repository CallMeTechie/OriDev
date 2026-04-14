# Ori:Dev Phase 1: Core Modules & Data Model

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement core-common (enums, Result type), core-security (Keystore, EncryptedPrefs), core-network (SSHJ/FTP wrappers), core-ui (Theme, shared components), and data (Room entities, DAOs, database).

**Architecture:** Bottom-up build: core-common first (no deps), then domain models, then core-security + core-network, then data (Room), then core-ui (Theme). Each module is independently testable.

**Tech Stack:** Kotlin, Room 2.7.x, SSHJ 0.40.x, Apache Commons Net 3.11.x, Hilt, Jetpack Compose Material 3, Android Keystore, EncryptedSharedPreferences.

**Depends on:** Phase 0 completed (all modules compile as empty shells).

---

## File Structure

```
core/core-common/src/main/kotlin/dev/ori/core/common/
├── model/
│   ├── Protocol.kt
│   ├── AuthMethod.kt
│   ├── SshKeyType.kt
│   ├── TransferDirection.kt
│   └── TransferStatus.kt
├── result/
│   └── AppResult.kt
├── error/
│   └── AppError.kt
└── extension/
    ├── LongExt.kt
    └── StringExt.kt

core/core-security/src/main/kotlin/dev/ori/core/security/
├── KeyStoreManager.kt
├── EncryptedPrefsManager.kt
├── BiometricAuthManager.kt
└── di/
    └── SecurityModule.kt

core/core-network/src/main/kotlin/dev/ori/core/network/
├── ssh/
│   ├── SshClient.kt          (interface)
│   ├── SshClientImpl.kt
│   └── SshSession.kt
├── ftp/
│   ├── FtpClient.kt          (interface)
│   └── FtpClientImpl.kt
├── model/
│   └── RemoteFile.kt
└── di/
    └── NetworkModule.kt

domain/src/main/kotlin/dev/ori/domain/
├── model/
│   ├── ServerProfile.kt      (domain model, not Room entity)
│   ├── Connection.kt
│   ├── FileItem.kt
│   └── TransferRequest.kt
└── repository/
    ├── ConnectionRepository.kt
    ├── FileSystemRepository.kt
    └── TransferRepository.kt

data/src/main/kotlin/dev/ori/data/
├── db/
│   ├── OriDevDatabase.kt
│   └── Converters.kt
├── entity/
│   ├── ServerProfileEntity.kt
│   ├── TransferRecordEntity.kt
│   ├── BookmarkEntity.kt
│   ├── CommandSnippetEntity.kt
│   ├── SessionLogEntity.kt
│   ├── ProxmoxNodeEntity.kt
│   └── KnownHostEntity.kt
├── dao/
│   ├── ServerProfileDao.kt
│   ├── TransferRecordDao.kt
│   ├── BookmarkDao.kt
│   ├── CommandSnippetDao.kt
│   ├── SessionLogDao.kt
│   ├── ProxmoxNodeDao.kt
│   └── KnownHostDao.kt
├── mapper/
│   └── ServerProfileMapper.kt
└── di/
    └── DatabaseModule.kt

core/core-ui/src/main/kotlin/dev/ori/core/ui/
├── theme/
│   ├── Color.kt
│   ├── Type.kt
│   ├── Shape.kt
│   └── OriDevTheme.kt
└── component/
    ├── StatusBadge.kt
    ├── LoadingIndicator.kt
    └── OriDevTopBar.kt
```

---

### Task 1.1: core-common -- Enums and Constants

**Files:**
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/model/Protocol.kt`
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/model/AuthMethod.kt`
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/model/SshKeyType.kt`
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/model/TransferDirection.kt`
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/model/TransferStatus.kt`
- Test: `core/core-common/src/test/kotlin/dev/ori/core/common/model/ProtocolTest.kt`

- [ ] **Step 1: Create Protocol.kt**

```kotlin
package dev.ori.core.common.model

enum class Protocol(val displayName: String, val defaultPort: Int) {
    SSH("SSH", 22),
    SFTP("SFTP", 22),
    SCP("SCP", 22),
    FTP("FTP", 21),
    FTPS("FTPS", 990),
    PROXMOX("Proxmox API", 8006);

    val isSshBased: Boolean get() = this in setOf(SSH, SFTP, SCP)
    val requiresEncryption: Boolean get() = this != FTP
}
```

- [ ] **Step 2: Create AuthMethod.kt**

```kotlin
package dev.ori.core.common.model

enum class AuthMethod(val displayName: String) {
    PASSWORD("Password"),
    SSH_KEY("SSH Key"),
    KEY_AGENT("Key Agent")
}
```

- [ ] **Step 3: Create SshKeyType.kt**

```kotlin
package dev.ori.core.common.model

enum class SshKeyType(val algorithmName: String, val displayName: String) {
    ED25519("ssh-ed25519", "Ed25519"),
    RSA("ssh-rsa", "RSA")
}
```

- [ ] **Step 4: Create TransferDirection.kt**

```kotlin
package dev.ori.core.common.model

enum class TransferDirection {
    UPLOAD,
    DOWNLOAD
}
```

- [ ] **Step 5: Create TransferStatus.kt**

```kotlin
package dev.ori.core.common.model

enum class TransferStatus {
    QUEUED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED
    val isActive: Boolean get() = this == ACTIVE || this == QUEUED || this == PAUSED
}
```

- [ ] **Step 6: Write tests**

Create `core/core-common/src/test/kotlin/dev/ori/core/common/model/ProtocolTest.kt`:

```kotlin
package dev.ori.core.common.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ProtocolTest {

    @Test
    fun ssh_isSshBased_returnsTrue() {
        assertThat(Protocol.SSH.isSshBased).isTrue()
        assertThat(Protocol.SFTP.isSshBased).isTrue()
        assertThat(Protocol.SCP.isSshBased).isTrue()
    }

    @Test
    fun ftp_isSshBased_returnsFalse() {
        assertThat(Protocol.FTP.isSshBased).isFalse()
        assertThat(Protocol.FTPS.isSshBased).isFalse()
        assertThat(Protocol.PROXMOX.isSshBased).isFalse()
    }

    @Test
    fun ftp_requiresEncryption_returnsFalse() {
        assertThat(Protocol.FTP.requiresEncryption).isFalse()
    }

    @Test
    fun allOtherProtocols_requireEncryption_returnsTrue() {
        Protocol.entries
            .filter { it != Protocol.FTP }
            .forEach { assertThat(it.requiresEncryption).isTrue() }
    }

    @Test
    fun ssh_defaultPort_is22() {
        assertThat(Protocol.SSH.defaultPort).isEqualTo(22)
    }

    @Test
    fun proxmox_defaultPort_is8006() {
        assertThat(Protocol.PROXMOX.defaultPort).isEqualTo(8006)
    }
}
```

Create `core/core-common/src/test/kotlin/dev/ori/core/common/model/TransferStatusTest.kt`:

```kotlin
package dev.ori.core.common.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TransferStatusTest {

    @Test
    fun completed_isTerminal() {
        assertThat(TransferStatus.COMPLETED.isTerminal).isTrue()
    }

    @Test
    fun failed_isTerminal() {
        assertThat(TransferStatus.FAILED.isTerminal).isTrue()
    }

    @Test
    fun active_isNotTerminal() {
        assertThat(TransferStatus.ACTIVE.isTerminal).isFalse()
    }

    @Test
    fun queued_isActive() {
        assertThat(TransferStatus.QUEUED.isActive).isTrue()
    }

    @Test
    fun completed_isNotActive() {
        assertThat(TransferStatus.COMPLETED.isActive).isFalse()
    }
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :core:core-common:test`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add core/core-common/
git commit -m "feat(core-common): add Protocol, AuthMethod, SshKeyType, TransferDirection, TransferStatus enums"
```

---

### Task 1.2: core-common -- AppResult and AppError

**Files:**
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/result/AppResult.kt`
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/error/AppError.kt`
- Test: `core/core-common/src/test/kotlin/dev/ori/core/common/result/AppResultTest.kt`

- [ ] **Step 1: Create AppError.kt**

```kotlin
package dev.ori.core.common.error

sealed class AppError(val message: String, val cause: Throwable? = null) {
    class NetworkError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class AuthenticationError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class HostKeyMismatch(val host: String, val expectedFingerprint: String, val actualFingerprint: String) :
        AppError("Host key mismatch for $host")
    class HostKeyUnknown(val host: String, val fingerprint: String, val keyType: String) :
        AppError("Unknown host key for $host")
    class FileOperationError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class TransferError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class PermissionDenied(message: String) : AppError(message)
    class StorageError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class ProxmoxApiError(val statusCode: Int, message: String) : AppError(message)
    class PremiumRequired(val feature: String) : AppError("Premium required for $feature")
    class LimitReached(val resource: String, val limit: Int) : AppError("Limit of $limit reached for $resource")
}
```

- [ ] **Step 2: Create AppResult.kt**

```kotlin
package dev.ori.core.common.result

import dev.ori.core.common.error.AppError

typealias AppResult<T> = Result<T>

fun <T> AppResult<T>.getAppError(): AppError? =
    exceptionOrNull()?.let { it as? AppErrorException }?.error

fun <T> AppResult<T>.onAppError(block: (AppError) -> Unit): AppResult<T> {
    getAppError()?.let(block)
    return this
}

fun <T> appSuccess(value: T): AppResult<T> = Result.success(value)

fun <T> appFailure(error: AppError): AppResult<T> = Result.failure(AppErrorException(error))

class AppErrorException(val error: AppError) : Exception(error.message, error.cause)
```

- [ ] **Step 3: Write tests**

Create `core/core-common/src/test/kotlin/dev/ori/core/common/result/AppResultTest.kt`:

```kotlin
package dev.ori.core.common.result

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import org.junit.jupiter.api.Test

class AppResultTest {

    @Test
    fun appSuccess_isSuccess() {
        val result = appSuccess("hello")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("hello")
    }

    @Test
    fun appFailure_isFailure() {
        val result = appFailure<String>(AppError.NetworkError("timeout"))
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun getAppError_returnsError() {
        val error = AppError.AuthenticationError("bad password")
        val result = appFailure<String>(error)
        assertThat(result.getAppError()).isEqualTo(error)
    }

    @Test
    fun getAppError_onSuccess_returnsNull() {
        val result = appSuccess("ok")
        assertThat(result.getAppError()).isNull()
    }

    @Test
    fun onAppError_callsBlock() {
        var captured: AppError? = null
        val error = AppError.PermissionDenied("nope")
        appFailure<Unit>(error).onAppError { captured = it }
        assertThat(captured).isEqualTo(error)
    }

    @Test
    fun onAppError_onSuccess_doesNotCallBlock() {
        var called = false
        appSuccess("ok").onAppError { called = true }
        assertThat(called).isFalse()
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:core-common:test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/core-common/
git commit -m "feat(core-common): add AppError sealed class and AppResult utility functions"
```

---

### Task 1.3: core-common -- Extension Functions

**Files:**
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/extension/LongExt.kt`
- Create: `core/core-common/src/main/kotlin/dev/ori/core/common/extension/StringExt.kt`
- Test: `core/core-common/src/test/kotlin/dev/ori/core/common/extension/LongExtTest.kt`
- Test: `core/core-common/src/test/kotlin/dev/ori/core/common/extension/StringExtTest.kt`

- [ ] **Step 1: Create LongExt.kt**

```kotlin
package dev.ori.core.common.extension

import java.text.DecimalFormat

fun Long.toHumanReadableSize(): String {
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val formatter = DecimalFormat("#.##")
    var size = this.toDouble()
    var unitIndex = -1
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return "${formatter.format(size)} ${units[unitIndex]}"
}

fun Long.toRelativeTimeString(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> "${diff / 604_800_000}w ago"
    }
}
```

- [ ] **Step 2: Create StringExt.kt**

```kotlin
package dev.ori.core.common.extension

fun String.isValidHost(): Boolean {
    if (isBlank()) return false
    val ipPattern = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
    val hostnamePattern = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)*$""")
    return ipPattern.matches(this) || hostnamePattern.matches(this)
}

fun String.isValidPort(): Boolean {
    val port = toIntOrNull() ?: return false
    return port in 1..65535
}

fun String.truncateMiddle(maxLength: Int): String {
    if (length <= maxLength) return this
    val keep = (maxLength - 3) / 2
    return "${take(keep)}...${takeLast(keep)}"
}
```

- [ ] **Step 3: Write tests**

Create `core/core-common/src/test/kotlin/dev/ori/core/common/extension/LongExtTest.kt`:

```kotlin
package dev.ori.core.common.extension

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LongExtTest {

    @Test
    fun bytes_formatsAsBytes() {
        assertThat(512L.toHumanReadableSize()).isEqualTo("512 B")
    }

    @Test
    fun kilobytes_formatsAsKB() {
        assertThat(1024L.toHumanReadableSize()).isEqualTo("1 KB")
    }

    @Test
    fun megabytes_formatsAsMB() {
        assertThat((5L * 1024 * 1024).toHumanReadableSize()).isEqualTo("5 MB")
    }

    @Test
    fun gigabytes_formatsWithDecimals() {
        assertThat((1536L * 1024 * 1024).toHumanReadableSize()).isEqualTo("1.5 GB")
    }
}
```

Create `core/core-common/src/test/kotlin/dev/ori/core/common/extension/StringExtTest.kt`:

```kotlin
package dev.ori.core.common.extension

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StringExtTest {

    @Test
    fun validIp_isValidHost() {
        assertThat("192.168.1.100".isValidHost()).isTrue()
    }

    @Test
    fun validHostname_isValidHost() {
        assertThat("server01.example.com".isValidHost()).isTrue()
    }

    @Test
    fun localhost_isValidHost() {
        assertThat("pve1.local".isValidHost()).isTrue()
    }

    @Test
    fun empty_isNotValidHost() {
        assertThat("".isValidHost()).isFalse()
    }

    @Test
    fun specialChars_isNotValidHost() {
        assertThat("server;rm -rf".isValidHost()).isFalse()
    }

    @Test
    fun validPort_isValid() {
        assertThat("22".isValidPort()).isTrue()
        assertThat("8006".isValidPort()).isTrue()
    }

    @Test
    fun zeroPort_isNotValid() {
        assertThat("0".isValidPort()).isFalse()
    }

    @Test
    fun portAbove65535_isNotValid() {
        assertThat("70000".isValidPort()).isFalse()
    }

    @Test
    fun nonNumericPort_isNotValid() {
        assertThat("abc".isValidPort()).isFalse()
    }

    @Test
    fun truncateMiddle_shortString_unchanged() {
        assertThat("hello".truncateMiddle(10)).isEqualTo("hello")
    }

    @Test
    fun truncateMiddle_longString_truncated() {
        val result = "/very/long/path/to/some/file.txt".truncateMiddle(20)
        assertThat(result.length).isAtMost(20)
        assertThat(result).contains("...")
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:core-common:test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/core-common/
git commit -m "feat(core-common): add Long and String extension functions"
```

---

### Task 1.4: domain -- Domain Models and Repository Interfaces

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/model/ServerProfile.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/model/Connection.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/model/FileItem.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/model/TransferRequest.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/ConnectionRepository.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/FileSystemRepository.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/TransferRepository.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/CredentialStore.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/KnownHostRepository.kt`

- [ ] **Step 1: Create ServerProfile domain model**

```kotlin
package dev.ori.domain.model

import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.SshKeyType

data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: Protocol,
    val username: String,
    val authMethod: AuthMethod,
    val credentialRef: String,
    val sshKeyType: SshKeyType? = null,
    val startupCommand: String? = null,
    val projectDirectory: String? = null,
    val claudeCodeModel: String? = null,
    val claudeMdPath: String? = null,
    val isFavorite: Boolean = false,
    val lastConnected: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)
```

- [ ] **Step 2: Create Connection domain model**

```kotlin
package dev.ori.domain.model

data class Connection(
    val profileId: Long,
    val serverName: String,
    val host: String,
    val status: ConnectionStatus,
    val connectedSince: Long? = null
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}
```

- [ ] **Step 3: Create FileItem domain model**

```kotlin
package dev.ori.domain.model

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val permissions: String? = null,
    val owner: String? = null,
    val gitStatus: GitStatus? = null
)

enum class GitStatus {
    STAGED,
    MODIFIED,
    UNTRACKED
}
```

- [ ] **Step 4: Create TransferRequest domain model**

```kotlin
package dev.ori.domain.model

import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus

data class TransferRequest(
    val id: Long = 0,
    val serverProfileId: Long,
    val sourcePath: String,
    val destinationPath: String,
    val direction: TransferDirection,
    val status: TransferStatus = TransferStatus.QUEUED,
    val totalBytes: Long = 0,
    val transferredBytes: Long = 0,
    val fileCount: Int = 1,
    val filesTransferred: Int = 0
)
```

- [ ] **Step 5: Create repository interfaces**

Create `domain/src/main/kotlin/dev/ori/domain/repository/ConnectionRepository.kt`:

```kotlin
package dev.ori.domain.repository

import dev.ori.domain.model.Connection
import dev.ori.domain.model.ServerProfile
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    fun getAllProfiles(): Flow<List<ServerProfile>>
    fun getFavoriteProfiles(): Flow<List<ServerProfile>>
    suspend fun getProfileById(id: Long): ServerProfile?
    suspend fun getProfileCount(): Int
    suspend fun saveProfile(profile: ServerProfile): Long
    suspend fun updateProfile(profile: ServerProfile)
    suspend fun deleteProfile(profile: ServerProfile)
    suspend fun connect(profileId: Long): Connection
    suspend fun disconnect(profileId: Long)
    fun getActiveConnections(): Flow<List<Connection>>
}
```

Create `domain/src/main/kotlin/dev/ori/domain/repository/FileSystemRepository.kt`:

```kotlin
package dev.ori.domain.repository

import dev.ori.domain.model.FileItem

interface FileSystemRepository {
    suspend fun listFiles(path: String): List<FileItem>
    suspend fun deleteFile(path: String)
    suspend fun renameFile(oldPath: String, newPath: String)
    suspend fun createDirectory(path: String)
    suspend fun chmod(path: String, permissions: String)
    suspend fun getFileContent(path: String): ByteArray
    suspend fun writeFileContent(path: String, content: ByteArray)
}
```

Create `domain/src/main/kotlin/dev/ori/domain/repository/TransferRepository.kt`:

```kotlin
package dev.ori.domain.repository

import dev.ori.domain.model.TransferRequest
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    fun getAllTransfers(): Flow<List<TransferRequest>>
    fun getActiveTransfers(): Flow<List<TransferRequest>>
    suspend fun enqueue(transfer: TransferRequest): Long
    suspend fun pause(transferId: Long)
    suspend fun resume(transferId: Long)
    suspend fun cancel(transferId: Long)
    suspend fun clearCompleted()
}
```

Create `domain/src/main/kotlin/dev/ori/domain/repository/CredentialStore.kt`:

```kotlin
package dev.ori.domain.repository

interface CredentialStore {
    suspend fun storePassword(alias: String, password: CharArray)
    suspend fun getPassword(alias: String): CharArray?
    suspend fun storeSshKey(alias: String, privateKey: ByteArray)
    suspend fun getSshKey(alias: String): ByteArray?
    suspend fun deleteCredential(alias: String)
    suspend fun hasCredential(alias: String): Boolean
}
```

Create `domain/src/main/kotlin/dev/ori/domain/repository/KnownHostRepository.kt`:

```kotlin
package dev.ori.domain.repository

import kotlinx.coroutines.flow.Flow

interface KnownHostRepository {
    suspend fun findHost(host: String, port: Int): KnownHostEntry?
    suspend fun trustHost(host: String, port: Int, keyType: String, fingerprint: String)
    suspend fun removeHost(host: String, port: Int)
    fun getAllKnownHosts(): Flow<List<KnownHostEntry>>
}

data class KnownHostEntry(
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
    val firstSeen: Long,
    val lastSeen: Long
)
```

- [ ] **Step 6: Commit**

```bash
git add domain/
git commit -m "feat(domain): add domain models and repository interfaces"
```

---

### Task 1.5: data -- Room Entities

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/entity/ServerProfileEntity.kt`
- Create: `data/src/main/kotlin/dev/ori/data/entity/TransferRecordEntity.kt`
- Create: `data/src/main/kotlin/dev/ori/data/entity/BookmarkEntity.kt`
- Create: `data/src/main/kotlin/dev/ori/data/entity/CommandSnippetEntity.kt`
- Create: `data/src/main/kotlin/dev/ori/data/entity/SessionLogEntity.kt`
- Create: `data/src/main/kotlin/dev/ori/data/entity/ProxmoxNodeEntity.kt`
- Create: `data/src/main/kotlin/dev/ori/data/entity/KnownHostEntity.kt`
- Create: `data/src/main/kotlin/dev/ori/data/db/Converters.kt`

- [ ] **Step 1: Create ServerProfileEntity**

```kotlin
package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.SshKeyType

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: Protocol,
    val username: String,
    val authMethod: AuthMethod,
    val credentialRef: String,
    val sshKeyType: SshKeyType?,
    val startupCommand: String?,
    val projectDirectory: String?,
    val claudeCodeModel: String?,
    val claudeMdPath: String?,
    val isFavorite: Boolean = false,
    val lastConnected: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)
```

- [ ] **Step 2: Create TransferRecordEntity**

```kotlin
package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus

@Entity(
    tableName = "transfer_records",
    foreignKeys = [ForeignKey(
        entity = ServerProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["serverProfileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("serverProfileId")]
)
data class TransferRecordEntity(
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
```

- [ ] **Step 3: Create remaining entities**

Create `data/src/main/kotlin/dev/ori/data/entity/BookmarkEntity.kt`:

```kotlin
package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long?,
    val path: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

Create `data/src/main/kotlin/dev/ori/data/entity/CommandSnippetEntity.kt`:

```kotlin
package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_snippets")
data class CommandSnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long?,
    val name: String,
    val command: String,
    val category: String,
    val isWatchQuickCommand: Boolean = false,
    val sortOrder: Int = 0
)
```

Create `data/src/main/kotlin/dev/ori/data/entity/SessionLogEntity.kt`:

```kotlin
package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_logs",
    foreignKeys = [ForeignKey(
        entity = ServerProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["serverProfileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("serverProfileId")]
)
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverProfileId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val logFilePath: String
)
```

Create `data/src/main/kotlin/dev/ori/data/entity/ProxmoxNodeEntity.kt`:

```kotlin
package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxmox_nodes")
data class ProxmoxNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 8006,
    val tokenId: String,
    val tokenSecretRef: String,
    val certFingerprint: String? = null,
    val lastSyncAt: Long? = null
)
```

Create `data/src/main/kotlin/dev/ori/data/entity/KnownHostEntity.kt`:

```kotlin
package dev.ori.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_hosts",
    indices = [Index(value = ["host", "port"], unique = true)]
)
data class KnownHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)
```

- [ ] **Step 4: Create Converters**

```kotlin
package dev.ori.data.db

import androidx.room.TypeConverter
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.SshKeyType
import dev.ori.core.common.model.TransferDirection
import dev.ori.core.common.model.TransferStatus

class Converters {

    @TypeConverter
    fun fromProtocol(value: Protocol): String = value.name

    @TypeConverter
    fun toProtocol(value: String): Protocol = Protocol.valueOf(value)

    @TypeConverter
    fun fromAuthMethod(value: AuthMethod): String = value.name

    @TypeConverter
    fun toAuthMethod(value: String): AuthMethod = AuthMethod.valueOf(value)

    @TypeConverter
    fun fromSshKeyType(value: SshKeyType?): String? = value?.name

    @TypeConverter
    fun toSshKeyType(value: String?): SshKeyType? = value?.let { SshKeyType.valueOf(it) }

    @TypeConverter
    fun fromTransferDirection(value: TransferDirection): String = value.name

    @TypeConverter
    fun toTransferDirection(value: String): TransferDirection = TransferDirection.valueOf(value)

    @TypeConverter
    fun fromTransferStatus(value: TransferStatus): String = value.name

    @TypeConverter
    fun toTransferStatus(value: String): TransferStatus = TransferStatus.valueOf(value)
}
```

- [ ] **Step 5: Commit**

```bash
git add data/
git commit -m "feat(data): add Room entities and type converters for all 7 tables"
```

---

### Task 1.6: data -- DAOs and Database

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/dao/ServerProfileDao.kt`
- Create: `data/src/main/kotlin/dev/ori/data/dao/TransferRecordDao.kt`
- Create: `data/src/main/kotlin/dev/ori/data/dao/BookmarkDao.kt`
- Create: `data/src/main/kotlin/dev/ori/data/dao/CommandSnippetDao.kt`
- Create: `data/src/main/kotlin/dev/ori/data/dao/SessionLogDao.kt`
- Create: `data/src/main/kotlin/dev/ori/data/dao/ProxmoxNodeDao.kt`
- Create: `data/src/main/kotlin/dev/ori/data/dao/KnownHostDao.kt`
- Create: `data/src/main/kotlin/dev/ori/data/db/OriDevDatabase.kt`
- Create: `data/src/main/kotlin/dev/ori/data/di/DatabaseModule.kt`

- [ ] **Step 1: Create ServerProfileDao**

```kotlin
package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.ServerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY sortOrder, name")
    fun getAll(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE isFavorite = 1 ORDER BY name")
    fun getFavorites(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE id = :id")
    suspend fun getById(id: Long): ServerProfileEntity?

    @Query("SELECT COUNT(*) FROM server_profiles")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(profile: ServerProfileEntity): Long

    @Update
    suspend fun update(profile: ServerProfileEntity)

    @Delete
    suspend fun delete(profile: ServerProfileEntity)

    @Query("UPDATE server_profiles SET lastConnected = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long = System.currentTimeMillis())
}
```

- [ ] **Step 2: Create remaining DAOs**

Create `data/src/main/kotlin/dev/ori/data/dao/TransferRecordDao.kt`:

```kotlin
package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferRecordDao {
    @Query("SELECT * FROM transfer_records ORDER BY startedAt DESC")
    fun getAll(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_records WHERE status IN ('QUEUED', 'ACTIVE', 'PAUSED')")
    fun getActive(): Flow<List<TransferRecordEntity>>

    @Query("SELECT * FROM transfer_records WHERE id = :id")
    suspend fun getById(id: Long): TransferRecordEntity?

    @Insert
    suspend fun insert(record: TransferRecordEntity): Long

    @Update
    suspend fun update(record: TransferRecordEntity)

    @Query("DELETE FROM transfer_records WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
```

Create `data/src/main/kotlin/dev/ori/data/dao/BookmarkDao.kt`:

```kotlin
package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import dev.ori.data.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY label")
    fun getAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE serverProfileId = :serverId OR serverProfileId IS NULL ORDER BY label")
    fun getForServer(serverId: Long?): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}
```

Create `data/src/main/kotlin/dev/ori/data/dao/CommandSnippetDao.kt`:

```kotlin
package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.CommandSnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandSnippetDao {
    @Query("SELECT * FROM command_snippets WHERE serverProfileId = :serverId OR serverProfileId IS NULL ORDER BY sortOrder")
    fun getForServer(serverId: Long?): Flow<List<CommandSnippetEntity>>

    @Query("SELECT * FROM command_snippets WHERE isWatchQuickCommand = 1 ORDER BY sortOrder")
    fun getWatchCommands(): Flow<List<CommandSnippetEntity>>

    @Insert
    suspend fun insert(snippet: CommandSnippetEntity): Long

    @Update
    suspend fun update(snippet: CommandSnippetEntity)

    @Delete
    suspend fun delete(snippet: CommandSnippetEntity)
}
```

Create `data/src/main/kotlin/dev/ori/data/dao/SessionLogDao.kt`:

```kotlin
package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.SessionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs WHERE serverProfileId = :serverId ORDER BY startedAt DESC")
    fun getForServer(serverId: Long): Flow<List<SessionLogEntity>>

    @Insert
    suspend fun insert(log: SessionLogEntity): Long

    @Update
    suspend fun update(log: SessionLogEntity)
}
```

Create `data/src/main/kotlin/dev/ori/data/dao/ProxmoxNodeDao.kt`:

```kotlin
package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.ori.data.entity.ProxmoxNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxmoxNodeDao {
    @Query("SELECT * FROM proxmox_nodes ORDER BY name")
    fun getAll(): Flow<List<ProxmoxNodeEntity>>

    @Query("SELECT * FROM proxmox_nodes WHERE id = :id")
    suspend fun getById(id: Long): ProxmoxNodeEntity?

    @Insert
    suspend fun insert(node: ProxmoxNodeEntity): Long

    @Update
    suspend fun update(node: ProxmoxNodeEntity)

    @Delete
    suspend fun delete(node: ProxmoxNodeEntity)
}
```

Create `data/src/main/kotlin/dev/ori/data/dao/KnownHostDao.kt`:

```kotlin
package dev.ori.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.ori.data.entity.KnownHostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun find(host: String, port: Int): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(knownHost: KnownHostEntity)

    @Delete
    suspend fun delete(knownHost: KnownHostEntity)

    @Query("SELECT * FROM known_hosts ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<KnownHostEntity>>
}
```

- [ ] **Step 3: Create OriDevDatabase**

```kotlin
package dev.ori.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.ori.data.dao.BookmarkDao
import dev.ori.data.dao.CommandSnippetDao
import dev.ori.data.dao.KnownHostDao
import dev.ori.data.dao.ProxmoxNodeDao
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.dao.SessionLogDao
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.entity.BookmarkEntity
import dev.ori.data.entity.CommandSnippetEntity
import dev.ori.data.entity.KnownHostEntity
import dev.ori.data.entity.ProxmoxNodeEntity
import dev.ori.data.entity.ServerProfileEntity
import dev.ori.data.entity.SessionLogEntity
import dev.ori.data.entity.TransferRecordEntity

@Database(
    entities = [
        ServerProfileEntity::class,
        TransferRecordEntity::class,
        BookmarkEntity::class,
        CommandSnippetEntity::class,
        SessionLogEntity::class,
        ProxmoxNodeEntity::class,
        KnownHostEntity::class,
    ],
    version = 1,
    exportSchema = true,
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

- [ ] **Step 4: Create DatabaseModule (Hilt)**

```kotlin
package dev.ori.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ori.data.dao.BookmarkDao
import dev.ori.data.dao.CommandSnippetDao
import dev.ori.data.dao.KnownHostDao
import dev.ori.data.dao.ProxmoxNodeDao
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.dao.SessionLogDao
import dev.ori.data.dao.TransferRecordDao
import dev.ori.data.db.OriDevDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OriDevDatabase =
        Room.databaseBuilder(
            context,
            OriDevDatabase::class.java,
            "oridev.db",
        ).build()

    @Provides
    fun provideServerProfileDao(db: OriDevDatabase): ServerProfileDao = db.serverProfileDao()

    @Provides
    fun provideTransferRecordDao(db: OriDevDatabase): TransferRecordDao = db.transferRecordDao()

    @Provides
    fun provideBookmarkDao(db: OriDevDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideCommandSnippetDao(db: OriDevDatabase): CommandSnippetDao = db.commandSnippetDao()

    @Provides
    fun provideSessionLogDao(db: OriDevDatabase): SessionLogDao = db.sessionLogDao()

    @Provides
    fun provideProxmoxNodeDao(db: OriDevDatabase): ProxmoxNodeDao = db.proxmoxNodeDao()

    @Provides
    fun provideKnownHostDao(db: OriDevDatabase): KnownHostDao = db.knownHostDao()
}
```

- [ ] **Step 5: Commit**

```bash
git add data/
git commit -m "feat(data): add Room DAOs, Database, and Hilt DatabaseModule"
```

---

### Task 1.7: data -- DAO Tests

**Files:**
- Test: `data/src/androidTest/kotlin/dev/ori/data/dao/ServerProfileDaoTest.kt`
- Test: `data/src/androidTest/kotlin/dev/ori/data/dao/KnownHostDaoTest.kt`

Note: Room DAO tests require Android instrumented tests (they need a Context for the in-memory DB).

- [ ] **Step 1: Create ServerProfileDaoTest**

```kotlin
package dev.ori.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.data.db.OriDevDatabase
import dev.ori.data.entity.ServerProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerProfileDaoTest {

    private lateinit var db: OriDevDatabase
    private lateinit var dao: ServerProfileDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OriDevDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.serverProfileDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun testProfile(name: String = "test", host: String = "192.168.1.1") =
        ServerProfileEntity(
            name = name,
            host = host,
            port = 22,
            protocol = Protocol.SSH,
            username = "user",
            authMethod = AuthMethod.PASSWORD,
            credentialRef = "alias_test",
            sshKeyType = null,
            startupCommand = null,
            projectDirectory = null,
            claudeCodeModel = null,
            claudeMdPath = null,
        )

    @Test
    fun insert_andGetAll_returnsProfile() = runTest {
        dao.insert(testProfile())
        val profiles = dao.getAll().first()
        assertThat(profiles).hasSize(1)
        assertThat(profiles[0].name).isEqualTo("test")
    }

    @Test
    fun getById_returnsCorrectProfile() = runTest {
        val id = dao.insert(testProfile())
        val profile = dao.getById(id)
        assertThat(profile).isNotNull()
        assertThat(profile!!.host).isEqualTo("192.168.1.1")
    }

    @Test
    fun getById_nonExistent_returnsNull() = runTest {
        val profile = dao.getById(999)
        assertThat(profile).isNull()
    }

    @Test
    fun getCount_returnsCorrectCount() = runTest {
        dao.insert(testProfile("a"))
        dao.insert(testProfile("b"))
        assertThat(dao.getCount()).isEqualTo(2)
    }

    @Test
    fun getFavorites_returnsOnlyFavorites() = runTest {
        dao.insert(testProfile("normal"))
        dao.insert(testProfile("fav").copy(isFavorite = true))
        val favorites = dao.getFavorites().first()
        assertThat(favorites).hasSize(1)
        assertThat(favorites[0].name).isEqualTo("fav")
    }

    @Test
    fun delete_removesProfile() = runTest {
        val id = dao.insert(testProfile())
        val profile = dao.getById(id)!!
        dao.delete(profile)
        assertThat(dao.getById(id)).isNull()
    }

    @Test
    fun updateLastConnected_updatesTimestamp() = runTest {
        val id = dao.insert(testProfile())
        val before = dao.getById(id)!!.lastConnected
        assertThat(before).isNull()

        dao.updateLastConnected(id, 1234567890L)
        val after = dao.getById(id)!!.lastConnected
        assertThat(after).isEqualTo(1234567890L)
    }
}
```

- [ ] **Step 2: Create KnownHostDaoTest**

```kotlin
package dev.ori.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.ori.data.db.OriDevDatabase
import dev.ori.data.entity.KnownHostEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnownHostDaoTest {

    private lateinit var db: OriDevDatabase
    private lateinit var dao: KnownHostDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OriDevDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.knownHostDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_andFind_returnsHost() = runTest {
        dao.upsert(testHost())
        val found = dao.find("example.com", 22)
        assertThat(found).isNotNull()
        assertThat(found!!.fingerprint).isEqualTo("SHA256:abc123def456")
    }

    @Test
    fun find_nonExistent_returnsNull() = runTest {
        assertThat(dao.find("unknown.com", 22)).isNull()
    }

    @Test
    fun upsert_sameHostPort_replacesEntry() = runTest {
        dao.upsert(testHost(fingerprint = "old"))
        dao.upsert(testHost().copy(fingerprint = "new"))
        val found = dao.find("example.com", 22)
        assertThat(found!!.fingerprint).isEqualTo("new")
    }

    @Test
    fun getAll_returnsAllHosts() = runTest {
        dao.upsert(testHost("a.com"))
        dao.upsert(testHost("b.com"))
        val all = dao.getAll().first()
        assertThat(all).hasSize(2)
    }

    @Test
    fun delete_removesHost() = runTest {
        dao.upsert(testHost())
        val host = dao.find("example.com", 22)!!
        dao.delete(host)
        assertThat(dao.find("example.com", 22)).isNull()
    }

    private fun testHost(host: String = "example.com", port: Int = 22, fingerprint: String = "SHA256:abc123def456") =
        KnownHostEntity(host = host, port = port, keyType = "ssh-ed25519", fingerprint = fingerprint)
}
```

- [ ] **Step 3: Add test dependencies to data module**

Ensure `data/build.gradle.kts` has these androidTest dependencies:

```kotlin
androidTestImplementation(libs.test.runner)
androidTestImplementation(libs.truth)
androidTestImplementation(libs.kotlinx.coroutines.test)
androidTestImplementation(libs.room.testing)
```

- [ ] **Step 4: Run DAO tests**

Run: `./gradlew :data:connectedAndroidTest` (requires emulator)
Alternative for CI: `./gradlew :data:test` if using Robolectric

- [ ] **Step 5: Commit**

```bash
git add data/
git commit -m "test(data): add instrumented tests for ServerProfileDao and KnownHostDao"
```

---

### Task 1.8: data -- Entity Mapper

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/mapper/ServerProfileMapper.kt`
- Test: `data/src/test/kotlin/dev/ori/data/mapper/ServerProfileMapperTest.kt`

- [ ] **Step 1: Create ServerProfileMapper**

```kotlin
package dev.ori.data.mapper

import dev.ori.data.entity.ServerProfileEntity
import dev.ori.domain.model.ServerProfile

fun ServerProfileEntity.toDomain(): ServerProfile =
    ServerProfile(
        id = id,
        name = name,
        host = host,
        port = port,
        protocol = protocol,
        username = username,
        authMethod = authMethod,
        credentialRef = credentialRef,
        sshKeyType = sshKeyType,
        startupCommand = startupCommand,
        projectDirectory = projectDirectory,
        claudeCodeModel = claudeCodeModel,
        claudeMdPath = claudeMdPath,
        isFavorite = isFavorite,
        lastConnected = lastConnected,
        createdAt = createdAt,
        sortOrder = sortOrder,
    )

fun ServerProfile.toEntity(): ServerProfileEntity =
    ServerProfileEntity(
        id = id,
        name = name,
        host = host,
        port = port,
        protocol = protocol,
        username = username,
        authMethod = authMethod,
        credentialRef = credentialRef,
        sshKeyType = sshKeyType,
        startupCommand = startupCommand,
        projectDirectory = projectDirectory,
        claudeCodeModel = claudeCodeModel,
        claudeMdPath = claudeMdPath,
        isFavorite = isFavorite,
        lastConnected = lastConnected,
        createdAt = createdAt,
        sortOrder = sortOrder,
    )
```

- [ ] **Step 2: Write mapper test**

Create `data/src/test/kotlin/dev/ori/data/mapper/ServerProfileMapperTest.kt`:

```kotlin
package dev.ori.data.mapper

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.model.SshKeyType
import dev.ori.data.entity.ServerProfileEntity
import dev.ori.domain.model.ServerProfile
import org.junit.jupiter.api.Test

class ServerProfileMapperTest {

    @Test
    fun entityToDomain_mapsAllFields() {
        val entity = ServerProfileEntity(
            id = 1,
            name = "test",
            host = "192.168.1.1",
            port = 22,
            protocol = Protocol.SSH,
            username = "admin",
            authMethod = AuthMethod.SSH_KEY,
            credentialRef = "key_1",
            sshKeyType = SshKeyType.ED25519,
            startupCommand = "cd /app",
            projectDirectory = "/app",
            claudeCodeModel = "opus",
            claudeMdPath = "/app/CLAUDE.md",
            isFavorite = true,
            lastConnected = 1000L,
            createdAt = 500L,
            sortOrder = 3,
        )

        val domain = entity.toDomain()

        assertThat(domain.id).isEqualTo(1)
        assertThat(domain.name).isEqualTo("test")
        assertThat(domain.host).isEqualTo("192.168.1.1")
        assertThat(domain.protocol).isEqualTo(Protocol.SSH)
        assertThat(domain.authMethod).isEqualTo(AuthMethod.SSH_KEY)
        assertThat(domain.sshKeyType).isEqualTo(SshKeyType.ED25519)
        assertThat(domain.isFavorite).isTrue()
        assertThat(domain.claudeCodeModel).isEqualTo("opus")
    }

    @Test
    fun domainToEntity_mapsAllFields() {
        val domain = ServerProfile(
            id = 2,
            name = "prod",
            host = "prod.example.com",
            port = 2222,
            protocol = Protocol.SFTP,
            username = "deploy",
            authMethod = AuthMethod.PASSWORD,
            credentialRef = "pw_2",
        )

        val entity = domain.toEntity()

        assertThat(entity.id).isEqualTo(2)
        assertThat(entity.name).isEqualTo("prod")
        assertThat(entity.port).isEqualTo(2222)
        assertThat(entity.protocol).isEqualTo(Protocol.SFTP)
    }

    @Test
    fun roundTrip_preservesAllFields() {
        val original = ServerProfileEntity(
            id = 5,
            name = "roundtrip",
            host = "rt.local",
            port = 22,
            protocol = Protocol.SCP,
            username = "user",
            authMethod = AuthMethod.KEY_AGENT,
            credentialRef = "agent_5",
            sshKeyType = null,
            startupCommand = null,
            projectDirectory = null,
            claudeCodeModel = null,
            claudeMdPath = null,
            isFavorite = false,
            lastConnected = null,
            createdAt = 999L,
            sortOrder = 0,
        )

        val result = original.toDomain().toEntity()

        assertThat(result).isEqualTo(original)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :data:test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add data/
git commit -m "feat(data): add ServerProfile entity-domain mapper with roundtrip tests"
```

---

### Task 1.9: core-ui -- Theme and Design System

**Files:**
- Create: `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/Color.kt`
- Create: `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/Type.kt`
- Create: `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/Shape.kt`
- Create: `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/OriDevTheme.kt`

- [ ] **Step 1: Create Color.kt**

```kotlin
package dev.ori.core.ui.theme

import androidx.compose.ui.graphics.Color

// Material 3 Purple Theme -- Dark
val Purple80 = Color(0xFFCCC2DC)
val PurpleGrey80 = Color(0xFFCAC4D0)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6750A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// Status Colors
val StatusConnected = Color(0xFF3FB950)
val StatusDisconnected = Color(0xFFF85149)
val StatusWarning = Color(0xFFD29922)
val StatusInfo = Color(0xFF58A6FF)

// Terminal Colors
val TerminalBackground = Color(0xFF0D1117)
val TerminalText = Color(0xFFC9D1D9)
val TerminalGreen = Color(0xFF3FB950)
val TerminalRed = Color(0xFFF85149)
val TerminalYellow = Color(0xFFD29922)
val TerminalBlue = Color(0xFF58A6FF)
val TerminalPurple = Color(0xFFBC8CFF)

// Premium
val PremiumGold = Color(0xFFFFD700)
```

- [ ] **Step 2: Create Type.kt**

```kotlin
package dev.ori.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val OriDevTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

val TerminalFontFamily = FontFamily.Monospace
```

- [ ] **Step 3: Create Shape.kt**

```kotlin
package dev.ori.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val OriDevShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
```

- [ ] **Step 4: Create OriDevTheme.kt**

```kotlin
package dev.ori.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

@Composable
fun OriDevTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OriDevTypography,
        shapes = OriDevShapes,
        content = content,
    )
}
```

- [ ] **Step 5: Commit**

```bash
git add core/core-ui/
git commit -m "feat(core-ui): add Material 3 theme with dark/light/dynamic color support"
```

---

### Task 1.10: core-ui -- Shared Components

**Files:**
- Create: `core/core-ui/src/main/kotlin/dev/ori/core/ui/component/StatusBadge.kt`
- Create: `core/core-ui/src/main/kotlin/dev/ori/core/ui/component/LoadingIndicator.kt`
- Create: `core/core-ui/src/main/kotlin/dev/ori/core/ui/component/OriDevTopBar.kt`

- [ ] **Step 1: Create StatusBadge**

```kotlin
package dev.ori.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.StatusConnected
import dev.ori.core.ui.theme.StatusDisconnected
import dev.ori.core.ui.theme.StatusWarning

@Composable
fun StatusDot(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (isConnected) StatusConnected else StatusDisconnected,
        label = "statusDotColor",
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun ProtocolBadge(
    protocol: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = protocol,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Preview
@Composable
private fun StatusDotPreview() {
    StatusDot(isConnected = true)
}
```

- [ ] **Step 2: Create LoadingIndicator**

```kotlin
package dev.ori.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
```

- [ ] **Step 3: Create OriDevTopBar**

```kotlin
package dev.ori.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriDevTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
```

- [ ] **Step 4: Commit**

```bash
git add core/core-ui/
git commit -m "feat(core-ui): add StatusDot, ProtocolBadge, LoadingIndicator, OriDevTopBar components"
```

---

### Task 1.11: Verify Phase 1

- [ ] **Step 1: Run all tests**

Run: `./gradlew test`
Expected: All unit tests in core-common and data PASS

- [ ] **Step 2: Compile all modules**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run detekt**

Run: `./gradlew detekt`
Expected: No violations (or fix any that arise)

- [ ] **Step 4: Fix any issues and commit**

```bash
git add -A
git commit -m "chore: resolve Phase 1 build and lint issues"
```

---

## Phase 1 Completion Checklist

- [ ] `core-common`: 5 enums, AppResult, AppError, 2 extension files -- all tested
- [ ] `domain`: 4 domain models, 5 repository interfaces
- [ ] `data`: 7 entities, 7 DAOs, Database, Converters, DatabaseModule, Mapper -- DAO tests written
- [ ] `core-ui`: Theme (Color, Type, Shape, OriDevTheme), 3 shared components
- [ ] `./gradlew test` passes
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew detekt` passes
