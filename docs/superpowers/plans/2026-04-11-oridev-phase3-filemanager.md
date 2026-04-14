# Ori:Dev Phase 3: Dual-Pane File Manager (v3 -- post-review-2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Dual-Pane File Manager with local filesystem browsing (left) and remote SFTP/FTP browsing (right), draggable divider, foldable awareness via WindowSizeClass, file operations with context menu, git status badges, bookmarks, and drag-to-transfer.

**Architecture:** feature-filemanager depends on domain (use cases, FileSystemRepository interface) and core-ui. It NEVER imports from data module directly. Data module provides Hilt-qualified bindings (`@Local` / `@Remote`) for FileSystemRepository. Single FileManagerViewModel manages both panes via PaneState.

**Tech Stack:** Jetpack Compose, Hilt (with @Qualifier), WindowManager (WindowSizeClass for fold detection), Room (bookmarks).

**Depends on:** Phase 2 completed.

**Review fixes applied:** Module boundary violation fixed (Hilt qualifiers), use case pattern aligned (repository injected), 6 missing spec tasks added, error handling improved, thread safety addressed, test coverage expanded.

---

## File Structure

```
domain/src/main/kotlin/dev/ori/domain/
├── model/
│   └── Bookmark.kt
├── repository/
│   ├── BookmarkRepository.kt
│   └── FileSystemQualifiers.kt
├── usecase/
│   ├── ListFilesUseCase.kt
│   ├── DeleteFileUseCase.kt
│   ├── RenameFileUseCase.kt
│   ├── CreateDirectoryUseCase.kt
│   ├── ChmodUseCase.kt
│   └── GetBookmarksUseCase.kt

data/src/main/kotlin/dev/ori/data/
├── repository/
│   ├── LocalFileSystemRepository.kt
│   ├── RemoteFileSystemRepository.kt
│   └── BookmarkRepositoryImpl.kt
├── di/
│   └── FileSystemModule.kt

feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/
├── ui/
│   ├── FileManagerScreen.kt
│   ├── FileManagerViewModel.kt
│   ├── FileManagerUiState.kt
│   ├── FileListPane.kt
│   ├── FileItemRow.kt
│   ├── FileInfoSheet.kt
│   ├── FileContextMenu.kt
│   ├── DualPaneLayout.kt
│   └── BookmarkBar.kt
├── navigation/
│   └── FileManagerNavigation.kt
```

---

### Task 3.1: domain -- Qualifiers, Bookmark Model, and Repository Interface

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/FileSystemQualifiers.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/model/Bookmark.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/BookmarkRepository.kt`

- [ ] **Step 1: Create Hilt qualifiers for FileSystemRepository**

```kotlin
package dev.ori.domain.repository

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalFileSystem

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteFileSystem
```

- [ ] **Step 2: Create Bookmark domain model**

```kotlin
package dev.ori.domain.model

data class Bookmark(
    val id: Long = 0,
    val serverProfileId: Long?,
    val path: String,
    val label: String,
)
```

- [ ] **Step 3: Create BookmarkRepository interface**

```kotlin
package dev.ori.domain.repository

import dev.ori.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getBookmarksForServer(serverId: Long?): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark): Long
    suspend fun removeBookmark(bookmark: Bookmark)
}
```

- [ ] **Step 4: Commit**

Message: `feat(domain): add FileSystem qualifiers, Bookmark model, and BookmarkRepository interface`

---

### Task 3.2: domain -- File Use Cases (all 6)

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/ListFilesUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/DeleteFileUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/RenameFileUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/CreateDirectoryUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/ChmodUseCase.kt`
- Create: `domain/src/main/kotlin/dev/ori/domain/usecase/GetBookmarksUseCase.kt`
- Test: `domain/src/test/kotlin/dev/ori/domain/usecase/ListFilesUseCaseTest.kt`
- Test: `domain/src/test/kotlin/dev/ori/domain/usecase/FileOperationUseCaseTest.kt`

File use cases differ from connection use cases: they operate on EITHER local or remote filesystem, so the repository cannot be constructor-injected. Instead, they have `@Inject constructor()` (no repository) and `invoke()` takes the repository as a parameter. The ViewModel holds both qualified repositories and passes the correct one per pane. This is a documented deviation from the constructor-injection pattern used by ConnectUseCase etc.

- [ ] **Step 1: Create all 6 use cases**

```kotlin
// ListFilesUseCase.kt
package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class ListFilesUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, path: String): AppResult<List<FileItem>> =
        try {
            val files = repository.listFiles(path)
            val sorted = files.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() },
            )
            appSuccess(sorted)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to list files: ${e.message}", e))
        }
}
```

```kotlin
// DeleteFileUseCase.kt
package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class DeleteFileUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, path: String): AppResult<Unit> =
        try {
            repository.deleteFile(path)
            appSuccess(Unit)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to delete: ${e.message}", e))
        }
}
```

```kotlin
// RenameFileUseCase.kt
package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class RenameFileUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, oldPath: String, newPath: String): AppResult<Unit> =
        try {
            repository.renameFile(oldPath, newPath)
            appSuccess(Unit)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to rename: ${e.message}", e))
        }
}
```

```kotlin
// CreateDirectoryUseCase.kt
package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class CreateDirectoryUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, path: String): AppResult<Unit> =
        try {
            repository.createDirectory(path)
            appSuccess(Unit)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to create directory: ${e.message}", e))
        }
}
```

```kotlin
// ChmodUseCase.kt
package dev.ori.domain.usecase

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppResult
import dev.ori.core.common.result.appFailure
import dev.ori.core.common.result.appSuccess
import dev.ori.domain.repository.FileSystemRepository
import javax.inject.Inject

class ChmodUseCase @Inject constructor() {
    suspend operator fun invoke(repository: FileSystemRepository, path: String, permissions: String): AppResult<Unit> =
        try {
            repository.chmod(path, permissions)
            appSuccess(Unit)
        } catch (e: Exception) {
            appFailure(AppError.FileOperationError("Failed to chmod: ${e.message}", e))
        }
}
```

```kotlin
// GetBookmarksUseCase.kt
package dev.ori.domain.usecase

import dev.ori.domain.model.Bookmark
import dev.ori.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookmarksUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(serverId: Long?): Flow<List<Bookmark>> =
        repository.getBookmarksForServer(serverId)
}
```

- [ ] **Step 2: Write ListFilesUseCaseTest**

```kotlin
package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.result.getAppError
import dev.ori.domain.model.FileItem
import dev.ori.domain.repository.FileSystemRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ListFilesUseCaseTest {

    private val repository = mockk<FileSystemRepository>()
    private val useCase = ListFilesUseCase()

    @Test
    fun invoke_success_returnsSortedDirectoriesFirst() = runTest {
        coEvery { repository.listFiles("/") } returns listOf(
            FileItem("README.md", "/README.md", isDirectory = false, size = 100),
            FileItem("src", "/src", isDirectory = true),
            FileItem("build", "/build", isDirectory = true),
            FileItem("app.kt", "/app.kt", isDirectory = false, size = 200),
        )

        val result = useCase(repository, "/")

        assertThat(result.isSuccess).isTrue()
        val sorted = result.getOrNull()!!
        assertThat(sorted[0].name).isEqualTo("build")
        assertThat(sorted[1].name).isEqualTo("src")
        assertThat(sorted[2].name).isEqualTo("app.kt")
        assertThat(sorted[3].name).isEqualTo("README.md")
    }

    @Test
    fun invoke_failure_returnsFileOperationError() = runTest {
        coEvery { repository.listFiles("/") } throws RuntimeException("Permission denied")

        val result = useCase(repository, "/")

        assertThat(result.isFailure).isTrue()
        assertThat(result.getAppError()!!.message).contains("Permission denied")
    }

    @Test
    fun invoke_emptyDirectory_returnsEmptyList() = runTest {
        coEvery { repository.listFiles("/empty") } returns emptyList()

        val result = useCase(repository, "/empty")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEmpty()
    }
}
```

- [ ] **Step 3: Write FileOperationUseCaseTest**

```kotlin
package dev.ori.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.result.getAppError
import dev.ori.domain.repository.FileSystemRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FileOperationUseCaseTest {

    private val repository = mockk<FileSystemRepository>()

    @Test
    fun deleteFile_success() = runTest {
        coEvery { repository.deleteFile("/file.txt") } just runs
        val result = DeleteFileUseCase()(repository, "/file.txt")
        assertThat(result.isSuccess).isTrue()
        coVerify { repository.deleteFile("/file.txt") }
    }

    @Test
    fun deleteFile_failure_returnsError() = runTest {
        coEvery { repository.deleteFile(any()) } throws RuntimeException("busy")
        val result = DeleteFileUseCase()(repository, "/file.txt")
        assertThat(result.isFailure).isTrue()
        assertThat(result.getAppError()!!.message).contains("busy")
    }

    @Test
    fun renameFile_success() = runTest {
        coEvery { repository.renameFile("/old.txt", "/new.txt") } just runs
        val result = RenameFileUseCase()(repository, "/old.txt", "/new.txt")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun createDirectory_success() = runTest {
        coEvery { repository.createDirectory("/newdir") } just runs
        val result = CreateDirectoryUseCase()(repository, "/newdir")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun chmod_success() = runTest {
        coEvery { repository.chmod("/file.sh", "755") } just runs
        val result = ChmodUseCase()(repository, "/file.sh", "755")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun chmod_failure_returnsError() = runTest {
        coEvery { repository.chmod(any(), any()) } throws RuntimeException("not supported")
        val result = ChmodUseCase()(repository, "/file.sh", "755")
        assertThat(result.isFailure).isTrue()
    }
}
```

- [ ] **Step 4: Run tests and commit**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew :domain:test`
Message: `feat(domain): add file and chmod use cases with Bookmark model and qualifiers`

---

### Task 3.3: data -- FileSystem Repository Implementations

**Files:**
- Create: `data/src/main/kotlin/dev/ori/data/repository/LocalFileSystemRepository.kt`
- Create: `data/src/main/kotlin/dev/ori/data/repository/RemoteFileSystemRepository.kt`
- Create: `data/src/main/kotlin/dev/ori/data/repository/BookmarkRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/di/FileSystemModule.kt`

- [ ] **Step 1: Create LocalFileSystemRepository**

Implements `FileSystemRepository`. Uses `java.io.File` for local operations. All methods run on `Dispatchers.IO`. `chmod()` is a no-op on non-rooted Android (documented with comment).

- [ ] **Step 2: Create RemoteFileSystemRepository**

Implements `FileSystemRepository`. Delegates to `SshClient` with a session ID. Session ID is stored in `AtomicReference<String?>` for thread safety. Maps `RemoteFile` to `FileItem`. For `getFileContent`/`writeFileContent`, uses temp files.

IMPORTANT: The `FileSystemRepository` interface has no session concept. To bridge this, `RemoteFileSystemRepository` exposes an additional `fun setActiveSession(sessionId: String)` method (NOT on the interface). The Hilt module provides it as `@RemoteFileSystem FileSystemRepository`, but the ViewModel also gets the concrete `RemoteFileSystemRepository` via a separate binding to set the session. Alternatively, simpler approach: add a `SessionAwareFileSystemRepository` wrapper interface in domain that extends `FileSystemRepository` with `fun setActiveSession(sessionId: String)`. The ViewModel checks `if (remoteRepo is SessionAwareFileSystemRepository)` before file ops.

SIMPLEST CORRECT APPROACH: Make `RemoteFileSystemRepository` NOT a singleton. Instead, `FileSystemModule` provides a new instance per injection via `@Provides` without `@Singleton`. The ViewModel sets the session ID after injection. Since the ViewModel is scoped to the screen lifecycle, this is safe. Update the @Provides to remove @Singleton on the remote binding.

Also: `chmod()` must parse permissions string as OCTAL: `permissions.toInt(8)`. Document this explicitly in the implementation.

- [ ] **Step 3: Create BookmarkRepositoryImpl**

@Singleton. Implements `BookmarkRepository`. Wraps `BookmarkDao` with entity/domain mapping.

- [ ] **Step 4: Create FileSystemModule with qualified bindings**

```kotlin
package dev.ori.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.core.network.ssh.SshClient
import dev.ori.data.repository.BookmarkRepositoryImpl
import dev.ori.data.repository.LocalFileSystemRepository
import dev.ori.data.repository.RemoteFileSystemRepository
import dev.ori.domain.repository.BookmarkRepository
import dev.ori.domain.repository.FileSystemRepository
import dev.ori.domain.repository.LocalFileSystem
import dev.ori.domain.repository.RemoteFileSystem
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FileSystemModule {

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    companion object {
        @Provides
        @Singleton
        @LocalFileSystem
        fun provideLocalFileSystemRepository(): FileSystemRepository =
            LocalFileSystemRepository()

        @Provides
        @RemoteFileSystem
        fun provideRemoteFileSystemRepository(sshClient: SshClient): FileSystemRepository =
            RemoteFileSystemRepository(sshClient)
        // NOT @Singleton -- ViewModel manages lifecycle and sets session ID

        @Provides
        fun provideRemoteFileSystemRepositoryConcrete(sshClient: SshClient): RemoteFileSystemRepository =
            RemoteFileSystemRepository(sshClient)
        // Separate concrete binding so ViewModel can call setActiveSession()
    }
}
```

- [ ] **Step 5: Commit**

Message: `feat(data): add Local/Remote FileSystemRepository with Hilt qualifiers and BookmarkRepository`

---

### Task 3.4: feature-filemanager -- UiState and ViewModel

**Files:**
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileManagerUiState.kt`
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileManagerViewModel.kt`

- [ ] **Step 1: Create FileManagerUiState**

Contains: `PaneState` (currentPath, files, selectedFiles, isLoading, error, pathStack as bounded list max 50, isRemote, serverName, viewMode), `ViewMode` enum, `FileManagerUiState` (leftPane, rightPane, isFolded, activePane, bookmarks, splitRatio, showFileInfo, contextMenuFile), `ActivePane` enum, `FileManagerEvent` sealed class with all events including Chmod, ShowContextMenu, AddBookmark, RemoveBookmark.

- [ ] **Step 2: Create FileManagerViewModel**

Key fixes from review:
- Injects `@LocalFileSystem FileSystemRepository` (for left pane) and `RemoteFileSystemRepository` concrete type (for right pane, to call `setActiveSession()`)
- The ViewModel uses `@LocalFileSystem FileSystemRepository` as the domain interface for local, and casts `RemoteFileSystemRepository` to `FileSystemRepository` when passing to use cases
- `navigateUp()` uses `File(path).parent ?: "/"` to handle root correctly
- `pathStack` is popped on navigateUp, capped at 50 entries
- `deleteSelected()`, `renameFile()`, `createDir()`, `chmod()` all handle `AppResult` failures and set error in pane state
- `clearError()` takes a pane parameter
- No import from `dev.ori.data.*`

- [ ] **Step 3: Commit**

Message: `feat(filemanager): add FileManagerViewModel with qualified repositories and error handling`

---

### Task 3.5: feature-filemanager -- FileItemRow and FileContextMenu

**Files:**
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileItemRow.kt`
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileContextMenu.kt`

- [ ] **Step 1: Create FileItemRow**

Uses `@OptIn(ExperimentalFoundationApi::class)` for `combinedClickable`. Shows: checkbox, file-type icon, name, size/permissions, git status dot. Selected files get `Indigo50` background.

- [ ] **Step 2: Create FileContextMenu**

```kotlin
package dev.ori.feature.filemanager.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.ori.domain.model.FileItem

@Composable
fun FileContextMenu(
    file: FileItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChmod: () -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text("Info") },
            onClick = { onInfo(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Info, null) },
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = { onRename(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
        )
        DropdownMenuItem(
            text = { Text("Permissions") },
            onClick = { onChmod(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
        )
        DropdownMenuItem(
            text = { Text("Transfer") },
            onClick = { onTransfer(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = { onDelete(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        )
    }
}
```

- [ ] **Step 3: Commit**

Message: `feat(filemanager): add FileItemRow with git badges and FileContextMenu`

---

### Task 3.6: feature-filemanager -- FileListPane, DualPaneLayout, BookmarkBar

**Files:**
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileListPane.kt`
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/DualPaneLayout.kt`
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/BookmarkBar.kt`
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileInfoSheet.kt`

- [ ] **Step 1: Create FileListPane**

Composable showing: header (Local/Remote label with StatusDot), toolbar (select all, view toggle, new folder), breadcrumb bar (clickable path segments), LazyColumn of FileItemRow with ".." parent directory entry, context menu integration (long-press shows FileContextMenu dropdown).

- [ ] **Step 2: Create DualPaneLayout**

Composable with two weighted boxes and a draggable divider (4.dp wide Box with `pointerInput` detecting drag gestures). Split ratio clamped to 0.2-0.8.

- [ ] **Step 3: Create BookmarkBar**

Horizontal scrollable row of bookmark chips. Each chip shows label and path, tappable to navigate.

- [ ] **Step 4: Create FileInfoSheet**

ModalBottomSheet showing file properties: name, path, type, size, permissions, owner, git status.

- [ ] **Step 5: Commit**

Message: `feat(filemanager): add FileListPane, DualPaneLayout, BookmarkBar, FileInfoSheet`

---

### Task 3.7: feature-filemanager -- FileManagerScreen with Foldable Awareness

**Files:**
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileManagerScreen.kt`
- Create: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/navigation/FileManagerNavigation.kt`
- Modify: `app/src/main/kotlin/dev/ori/app/navigation/OriDevNavHost.kt`
- Modify: `app/src/main/kotlin/dev/ori/app/ui/OriDevApp.kt`

- [ ] **Step 1: Create FileManagerScreen with WindowSizeClass**

Uses `WindowInfoTracker` from `androidx.window:window` (already in version catalog as `libs.window`) to detect fold state and window size. DO NOT use `material3-window-size-class` (deprecated/moved). Instead:
- Use `WindowInfoTracker.getOrCreate(activity).windowLayoutInfo()` to detect fold posture
- Use `BoxWithConstraints` or `LocalConfiguration.current.screenWidthDp` to determine compact/expanded width
- Compact width (< 600dp) -> single pane (folded mode) with Local/Remote TabRow
- Expanded width (>= 600dp) -> dual pane with DualPaneLayout

Includes: OriDevTopBar, BookmarkBar (if bookmarks exist), content area (single or dual pane), bottom action bar (when files selected: Delete + Transfer buttons with selection count), FileInfoSheet overlay, delete confirmation dialog.

NOTE: Add `implementation(libs.window)` and `implementation(libs.compose.material.icons.extended)` to feature-filemanager build.gradle.kts. These are REQUIRED for fold detection and extended icons (DriveFileRenameOutline, ContentCopy, SwapHoriz, etc.).

- [ ] **Step 2: Create FileManagerNavigation**

Route constant `FILE_MANAGER_ROUTE = "filemanager"`. NavGraphBuilder extension `fileManagerScreen()`. NavController extension `navigateToFileManager()`.

- [ ] **Step 3: Update OriDevNavHost**

Replace placeholder `composable("filemanager")` with `fileManagerScreen()`. Import from `dev.ori.feature.filemanager.navigation`.

- [ ] **Step 4: Update OriDevApp bottom nav**

Import and use `FILE_MANAGER_ROUTE` constant instead of hardcoded string.

- [ ] **Step 5: Verify build**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew assembleDebug`

- [ ] **Step 6: Commit**

Message: `feat(filemanager): add FileManagerScreen with WindowSizeClass fold detection and navigation`

---

### Task 3.8: feature-filemanager -- ViewModel Tests

**Files:**
- Test: `feature-filemanager/src/test/kotlin/dev/ori/feature/filemanager/ui/FileManagerViewModelTest.kt`

Tests use `@LocalFileSystem FileSystemRepository` mock (domain interface, NOT data class):

- [ ] **Step 1: Write comprehensive ViewModel tests**

Tests covering:
1. `init_loadsLeftPaneFiles` -- verify files loaded on init
2. `navigateToPath_updatesFilesAndPath` -- verify path + files change
3. `navigateUp_goesToParent` -- verify parent navigation
4. `navigateUp_atRoot_staysAtRoot` -- verify root boundary
5. `toggleSelection_addsAndRemoves` -- toggle on/off
6. `selectAll_selectsAllFiles` -- all files selected
7. `deleteSelected_callsUseCaseAndRefreshes` -- verify use case called, error handled
8. `deleteSelected_failure_setsError` -- verify error surfaces in pane state
9. `renameFile_success_refreshesPane` -- verify refresh after rename
10. `createDirectory_failure_setsError` -- error path
11. `setViewMode_updatesMode` -- grid/list toggle
12. `updateSplitRatio_clampsToValidRange` -- 0.2-0.8 clamping
13. `toggleFoldState_togglesIsFolded` -- fold toggle

All tests use MockK for repository, UnconfinedTestDispatcher, Turbine for StateFlow.

- [ ] **Step 2: Run tests**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew :feature-filemanager:test`

- [ ] **Step 3: Commit**

Message: `test(filemanager): add comprehensive FileManagerViewModel tests`

---

### Task 3.9: Verify Phase 3

- [ ] **Step 1: Run all tests**

Run: `export ANDROID_HOME=/opt/android-sdk && ./gradlew test`

- [ ] **Step 2: Compile**

Run: `./gradlew assembleDebug`

- [ ] **Step 3: Fix and commit if needed**

Message: `chore: resolve Phase 3 build and lint issues`

---

## Phase 3 Completion Checklist

- [ ] `domain`: Hilt qualifiers (@LocalFileSystem, @RemoteFileSystem), Bookmark model + repository, 6 use cases (List, Delete, Rename, CreateDir, Chmod, GetBookmarks) -- all tested
- [ ] `data`: LocalFileSystemRepository, RemoteFileSystemRepository (thread-safe session), BookmarkRepositoryImpl, FileSystemModule with qualified bindings
- [ ] `feature-filemanager`: FileManagerViewModel (dual-pane, error handling, bounded path stack), FileItemRow (git badges, @OptIn), FileContextMenu (6 actions), FileListPane (breadcrumbs, toolbar), DualPaneLayout (draggable divider), BookmarkBar, FileInfoSheet, FileManagerScreen (WindowSizeClass fold detection, confirmation dialogs)
- [ ] `app`: NavHost + bottom nav updated
- [ ] All tests pass, project compiles

## Deferred to Later Phases
- Git status badge LOGIC (parsing .git directory) -- deferred to Phase 6 (Claude Code Integration)
- Drag & Drop between panels (initiates transfer) -- deferred to Phase 5 (Transfer Queue, needs TransferWorker)
- FTP support in RemoteFileSystemRepository -- deferred, SFTP covers primary use case
