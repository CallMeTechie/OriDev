# Foldable Split Terminal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auf dem Pixel Fold unfolded (`screenWidthDp >= 600`) rendert der Terminal-Tab zwei Tabs parallel im Split-Layout; Phone + folded bleiben Single-Pane. Closes task #34.

**Architecture:** Drei neue Felder in `TerminalUiState` (`leftPaneTabId`, `rightPaneTabId`, `activePaneIndex`), ein reiner Reducer `resolvePaneAssignments` mit zweistufiger Safety (Rules 1-4 + Sanitization-Clamp), extrahierte `PaneContent`-Composable, ein einziges `KeyboardHost` das über `computeActiveTabId` zum aktiven Pane routet. Persistence erweitert Task #33's `SessionResumePreferences` um drei Keys. Cold-Start-Restore-Race gegen Orphan-Cleanup wird durch einen `isRestoringPanes`-Latch mit `try/finally` + 60s-Timeout-Fallback gelöst.

**Tech Stack:** Kotlin 2.3.20, Compose BoM 2026.03.01, Hilt 2.58, coroutines 1.10, kotlinx.serialization 1.9, ConnectBot termlib, SSHJ.

**Spec:** `docs/superpowers/specs/2026-04-24-foldable-split-terminal-design.md` (Rev 4).

**Branch:** `feat/foldable-split-terminal` off master (after docs PR lands).

---

## File Structure

### Created

| Path | Responsibility |
|------|----------------|
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneAssignment.kt` | Pure data class + `resolvePaneAssignments` reducer (Rules 1-4 + clamp). |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneContent.kt` | Extracted pane-body composable with focus border, empty-state, + tab picker sheet. |
| `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/PaneAssignmentTest.kt` | Unit tests for reducer — 12+ cases covering orphan, duplicate, fill, clamp, restoring-mode. |

### Modified

| Path | Change |
|------|--------|
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt` | Add `leftPaneTabId`, `rightPaneTabId`, `activePaneIndex` fields. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt` | Add `isRestoringPanes` latch, Phase 1 pre-load, Phase 3 `try/finally` with 60s timeout, `setActivePane`, `moveTabToPane`, reducer hook into tab-lifecycle, snapshot-writer extension with 3 new keys, `computeActiveTabId` helper. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt` | Split-or-single rendering per `screenWidthDp >= 600`; wires `TabBar + PaneContent(s) + KeyboardHost`. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalTabBar.kt` | Dual-underline (2dp primary focused-pane / 1dp outlineVariant non-active-pane), auto-scroll on `setActivePane`, long-press popup with "In Pane X bewegen" options (split-mode only), popup dismiss on `isSplitActive` transition. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/KeyboardHost.kt` | Route `onKey` via `computeActiveTabId`; `LaunchedEffect(activePaneIndex)` re-requests IME focus guarded by `WindowInsets.isImeVisible`. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalView.kt` (or existing resize host) | `ResizeTerminal` debounce keyed per-tabId; 30ms stagger on fold emissions. |
| `domain/src/main/kotlin/dev/ori/domain/preferences/SessionResumePreferences.kt` | Add `leftPaneTabId`, `rightPaneTabId`, `activePaneIndex` flows + setters; extend `clearResumeSubset()` wipe-set. |
| `data/src/main/kotlin/dev/ori/data/session/SessionPersistencePreferences.kt` | Implement 3 new keys (two `stringPreferencesKey`, one `intPreferencesKey`); wire into `clearResumeSubset()`. |
| `data/src/main/kotlin/dev/ori/data/session/ResumeCoordinator.kt` | Add `enum RestoreState { Idle, InProgress, Settled }` + `val restoreState: StateFlow<RestoreState>`; `runResume` sets `InProgress` before `async.awaitAll()`, `Settled` in `finally`. |
| `data/src/test/kotlin/dev/ori/data/session/ResumeCoordinatorTest.kt` | Add `restoreState emits Idle -> InProgress -> Settled` test. |
| `data/src/test/kotlin/dev/ori/data/session/SessionPersistencePreferencesTest.kt` | Round-trip + `clearResumeSubset` + corrupt-value tests for 3 new keys. |
| `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt` | Tests: restore-gate, snapshot-writer-with-panes, `setActivePane` persistence, `moveTabToPane` transactional, tab-lifecycle-hooks. |

---

## Task 1: `ResumeCoordinator.restoreState` — prerequisite

**Files:**
- Modify: `data/src/main/kotlin/dev/ori/data/session/ResumeCoordinator.kt`
- Modify: `data/src/test/kotlin/dev/ori/data/session/ResumeCoordinatorTest.kt`

Spec §7 Phase 3 relies on `awaitRestoreComplete()` semantics — a terminal-event from the coordinator regardless of per-profile success. That requires exposing a new `StateFlow<RestoreState>`.

- [ ] **Step 1: Write failing test**

Add to `ResumeCoordinatorTest.kt`:

```kotlin
@Test
fun `restoreState transitions Idle to InProgress to Settled on full run`() = runTest {
    appPrefs.autoResume = true
    resumePrefs.profileIds = setOf(1L, 2L)
    profileDao.setProfile(ServerProfile(id = 1L, name = "A", host = "h", port = 22))
    profileDao.setProfile(ServerProfile(id = 2L, name = "B", host = "h", port = 22))

    val states = mutableListOf<ResumeCoordinator.RestoreState>()
    backgroundScope.launch { coordinator.restoreState.toList(states) }

    assertThat(coordinator.restoreState.value).isEqualTo(ResumeCoordinator.RestoreState.Idle)
    coordinator.start()
    advanceUntilIdle()

    assertThat(states).containsAtLeast(
        ResumeCoordinator.RestoreState.Idle,
        ResumeCoordinator.RestoreState.InProgress,
        ResumeCoordinator.RestoreState.Settled,
    ).inOrder()
}

@Test
fun `restoreState reaches Settled even on all-failure`() = runTest {
    appPrefs.autoResume = true
    resumePrefs.profileIds = setOf(1L)
    sessionRegistry.failConnectFor(1L, reason = "timeout")

    coordinator.start()
    advanceUntilIdle()

    assertThat(coordinator.restoreState.value).isEqualTo(ResumeCoordinator.RestoreState.Settled)
}
```

- [ ] **Step 2: Run — expect FAIL**

```
cd /root/OriDev && ./gradlew :data:testDebugUnitTest --tests "dev.ori.data.session.ResumeCoordinatorTest"
```

Expected: unresolved `RestoreState`, `restoreState`.

- [ ] **Step 3: Add state to the coordinator**

In `ResumeCoordinator.kt`, add the enum at class-level and a new `StateFlow`:

```kotlin
enum class RestoreState { Idle, InProgress, Settled }

private val _restoreState = MutableStateFlow(RestoreState.Idle)
val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()
```

- [ ] **Step 4: Update `runResume` to transition the state**

Wrap the method body in try/finally (keep all existing behaviour):

```kotlin
private suspend fun runResume() {
    _restoreState.value = RestoreState.InProgress
    try {
        val enabled = autoResumePrefs.autoResumeSessions.first()
        if (!enabled) return

        val persisted = resumePrefs.profileIds.first()
        if (persisted.isEmpty()) return

        pendingFailures.clear()
        persisted.map { profileId ->
            scope.async { connectWithHostKeyQueue(profileId) }
        }.awaitAll()

        val focusedProfileId = resumePrefs.focusedProfileId.first()
        val sessions = sessionRegistry.openSessions.first()
        sessions.firstOrNull { it.profileId == focusedProfileId }?.id?.let { sessionRegistry.focus(it) }

        emitFailureSnackbarIfAny()
    } finally {
        _restoreState.value = RestoreState.Settled
    }
}
```

- [ ] **Step 5: Run — expect PASS**

```
./gradlew :data:testDebugUnitTest --tests "dev.ori.data.session.ResumeCoordinatorTest"
```

Expected: all green including the two new tests.

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/dev/ori/data/session/ResumeCoordinator.kt \
        data/src/test/kotlin/dev/ori/data/session/ResumeCoordinatorTest.kt
git commit -m "feat(data): expose ResumeCoordinator.restoreState for terminal restore-gate"
```

---

## Task 2: `SessionResumePreferences` extended — domain + data + tests

**Files:**
- Modify: `domain/src/main/kotlin/dev/ori/domain/preferences/SessionResumePreferences.kt`
- Modify: `data/src/main/kotlin/dev/ori/data/session/SessionPersistencePreferences.kt`
- Modify: `data/src/test/kotlin/dev/ori/data/session/SessionPersistencePreferencesTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `SessionPersistencePreferencesTest.kt`:

```kotlin
@Test
fun `leftPaneTabId round-trip including null`() = runTest {
    prefs.setLeftPaneTabId("tab-42")
    assertThat(prefs.leftPaneTabId.first()).isEqualTo("tab-42")
    prefs.setLeftPaneTabId(null)
    assertThat(prefs.leftPaneTabId.first()).isNull()
}

@Test
fun `rightPaneTabId round-trip including null`() = runTest {
    prefs.setRightPaneTabId("tab-7")
    assertThat(prefs.rightPaneTabId.first()).isEqualTo("tab-7")
    prefs.setRightPaneTabId(null)
    assertThat(prefs.rightPaneTabId.first()).isNull()
}

@Test
fun `activePaneIndex default is 0 and round-trips`() = runTest {
    assertThat(prefs.activePaneIndex.first()).isEqualTo(0)
    prefs.setActivePaneIndex(1)
    assertThat(prefs.activePaneIndex.first()).isEqualTo(1)
}

@Test
fun `clearResumeSubset wipes pane keys too`() = runTest {
    prefs.setLeftPaneTabId("L")
    prefs.setRightPaneTabId("R")
    prefs.setActivePaneIndex(1)
    prefs.setLastTopLevelRoute("terminal")
    prefs.clearResumeSubset()
    assertThat(prefs.leftPaneTabId.first()).isNull()
    assertThat(prefs.rightPaneTabId.first()).isNull()
    assertThat(prefs.activePaneIndex.first()).isEqualTo(0)
    assertThat(prefs.lastTopLevelRoute.first()).isEqualTo("terminal")  // preserved
}
```

- [ ] **Step 2: Run — expect FAIL**

```
./gradlew :data:testDebugUnitTest --tests "dev.ori.data.session.SessionPersistencePreferencesTest"
```

Expected: unresolved references.

- [ ] **Step 3: Extend domain interface**

In `domain/src/main/kotlin/dev/ori/domain/preferences/SessionResumePreferences.kt`, add to the existing interface:

```kotlin
interface SessionResumePreferences {
    // ... existing flows + setters ...

    val leftPaneTabId: Flow<String?>
    val rightPaneTabId: Flow<String?>
    val activePaneIndex: Flow<Int>

    suspend fun setLeftPaneTabId(tabId: String?)
    suspend fun setRightPaneTabId(tabId: String?)
    suspend fun setActivePaneIndex(index: Int)

    // existing clearResumeSubset — extended in impl
    suspend fun clearResumeSubset()
}
```

- [ ] **Step 4: Extend impl with new keys**

In `data/src/main/kotlin/dev/ori/data/session/SessionPersistencePreferences.kt`, add keys to the `Keys` object:

```kotlin
private object Keys {
    // ... existing ...
    val leftPaneTabId = stringPreferencesKey("left_pane_tab_id")
    val rightPaneTabId = stringPreferencesKey("right_pane_tab_id")
    val activePaneIndex = intPreferencesKey("active_pane_index")
}
```

Add `import androidx.datastore.preferences.core.intPreferencesKey` if not yet present.

Add flows + setters (place after existing ones):

```kotlin
override val leftPaneTabId: Flow<String?> =
    dataStore.data.map { prefs -> prefs[Keys.leftPaneTabId] }

override val rightPaneTabId: Flow<String?> =
    dataStore.data.map { prefs -> prefs[Keys.rightPaneTabId] }

override val activePaneIndex: Flow<Int> =
    dataStore.data.map { prefs -> prefs[Keys.activePaneIndex]?.coerceIn(0, 1) ?: 0 }

override suspend fun setLeftPaneTabId(tabId: String?) {
    dataStore.edit {
        if (tabId == null) it.remove(Keys.leftPaneTabId) else it[Keys.leftPaneTabId] = tabId
    }
}

override suspend fun setRightPaneTabId(tabId: String?) {
    dataStore.edit {
        if (tabId == null) it.remove(Keys.rightPaneTabId) else it[Keys.rightPaneTabId] = tabId
    }
}

override suspend fun setActivePaneIndex(index: Int) {
    dataStore.edit { it[Keys.activePaneIndex] = index.coerceIn(0, 1) }
}
```

- [ ] **Step 5: Extend `clearResumeSubset`**

Find the existing `clearResumeSubset()` body and add the three new `remove()` calls:

```kotlin
override suspend fun clearResumeSubset() {
    dataStore.edit {
        it.remove(Keys.profileIds)
        it.remove(Keys.tabMemos)
        it.remove(Keys.focusedProfileId)
        it.remove(Keys.remotePaths)
        it.remove(Keys.leftPaneTabId)
        it.remove(Keys.rightPaneTabId)
        it.remove(Keys.activePaneIndex)
    }
}
```

- [ ] **Step 6: Run — expect PASS**

```
./gradlew :data:testDebugUnitTest --tests "dev.ori.data.session.SessionPersistencePreferencesTest"
```

Expected: all 4 new tests green, existing tests still green.

- [ ] **Step 7: Build gate**

```
./gradlew :domain:compileKotlin :data:assembleDebug
```

Expected: clean.

- [ ] **Step 8: Commit**

```bash
git add domain/src/main/kotlin/dev/ori/domain/preferences/SessionResumePreferences.kt \
        data/src/main/kotlin/dev/ori/data/session/SessionPersistencePreferences.kt \
        data/src/test/kotlin/dev/ori/data/session/SessionPersistencePreferencesTest.kt
git commit -m "feat(data): persist leftPaneTabId, rightPaneTabId, activePaneIndex"
```

---

## Task 3: `PaneAssignment` reducer — pure Kotlin, complete test suite

**Files:**
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneAssignment.kt`
- Create: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/PaneAssignmentTest.kt`

- [ ] **Step 1: Write failing tests**

Create `PaneAssignmentTest.kt`:

```kotlin
package dev.ori.feature.terminal.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PaneAssignmentTest {

    private fun tab(id: String, profileId: Long = 1L) = TerminalTab(
        id = id,
        profileId = profileId,
        profileName = "P$profileId",
    )

    @Test
    fun `empty tabs yields empty slots and active 0`() {
        val r = resolvePaneAssignments(
            tabs = emptyList(),
            leftPaneTabId = null,
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isNull()
        assertThat(r.rightPaneTabId).isNull()
        assertThat(r.activePaneIndex).isEqualTo(0)
    }

    @Test
    fun `orphan left slot is nulled outside restore`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T2")),
            leftPaneTabId = "T-ghost",
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T2")  // Rule 3 refills
    }

    @Test
    fun `orphan NOT nulled during restore (pending tab)`() {
        val r = resolvePaneAssignments(
            tabs = emptyList(),
            leftPaneTabId = "T-pending",
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = true,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T-pending")
    }

    @Test
    fun `duplicate cleanup preserves active-pane slot (active=0)`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = "T1",
            rightPaneTabId = "T1",
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T1")
        assertThat(r.rightPaneTabId).isEqualTo("T2")  // right refilled with next
    }

    @Test
    fun `duplicate cleanup preserves active-pane slot (active=1)`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = "T1",
            rightPaneTabId = "T1",
            activePaneIndex = 1,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T2")  // left cleared then refilled
        assertThat(r.rightPaneTabId).isEqualTo("T1")
    }

    @Test
    fun `left-slot fill uses activeTabIndex`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = null,
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 1,  // active is T2
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T2")
        assertThat(r.rightPaneTabId).isEqualTo("T1")
    }

    @Test
    fun `right-slot not filled with only one tab`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1")),
            leftPaneTabId = null,
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T1")
        assertThat(r.rightPaneTabId).isNull()
    }

    @Test
    fun `single tab in rightSlot + active=0 keeps active=0 (regression DA-3)`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1")),
            leftPaneTabId = null,
            rightPaneTabId = "T1",
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        // Rule 2 (duplicate) n/a, Rule 3 fills left=T1, Rule 4 keeps right=T1
        // Rule 2 on re-check: left==right → clear right (active=0 preserved)
        assertThat(r.leftPaneTabId).isEqualTo("T1")
        assertThat(r.rightPaneTabId).isNull()
        assertThat(r.activePaneIndex).isEqualTo(0)
    }

    @Test
    fun `activePaneIndex clamped when rightSlot genuinely empty`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1")),
            leftPaneTabId = "T1",
            rightPaneTabId = null,
            activePaneIndex = 1,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        // No way to fill right (only 1 tab), clamp active=1 -> 0
        assertThat(r.activePaneIndex).isEqualTo(0)
    }

    @Test
    fun `activePaneIndex out of range coerced`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = "T1",
            rightPaneTabId = "T2",
            activePaneIndex = 7,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.activePaneIndex).isEqualTo(1)  // coerceIn(0,1)
    }

    @Test
    fun `orphan cleanup of rightSlot with refill`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2"), tab("T3")),
            leftPaneTabId = "T1",
            rightPaneTabId = "T-ghost",
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.rightPaneTabId).isEqualTo("T2")  // first non-left tab
    }

    @Test
    fun `when only right has a tab outside restore, active flips to 1`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = null,
            rightPaneTabId = "T2",
            activePaneIndex = 0,
            activeTabIndex = -1,  // corrupt activeTabIndex
            isRestoringPanes = false,
        )
        // Rule 3 fills left from tabs.first() (activeTabIndex invalid)
        // left=T1, right=T2 both non-null, active stays 0
        assertThat(r.leftPaneTabId).isEqualTo("T1")
        assertThat(r.rightPaneTabId).isEqualTo("T2")
        assertThat(r.activePaneIndex).isEqualTo(0)
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```
./gradlew :feature-terminal:testDebugUnitTest --tests "dev.ori.feature.terminal.ui.PaneAssignmentTest"
```

Expected: unresolved `resolvePaneAssignments`, `PaneAssignment`, `TerminalTab`.

- [ ] **Step 3: Write the reducer**

Create `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneAssignment.kt`:

```kotlin
package dev.ori.feature.terminal.ui

/**
 * Snapshot of pane state. Output of [resolvePaneAssignments].
 */
data class PaneAssignment(
    val leftPaneTabId: String?,
    val rightPaneTabId: String?,
    val activePaneIndex: Int,
)

/**
 * Pure reducer that collapses any pane-state input into a consistent
 * output via two-stage rules:
 *
 * Phase A: Rules 1-4 (orphan-cleanup → duplicate-cleanup → left-fill →
 * right-fill). Rule 1 is suspended while [isRestoringPanes] is true so
 * cold-start pane IDs are not nulled before the matching tabs arrive.
 *
 * Phase B: Sanitization-Clamp runs only AFTER Phase A. It never flips
 * the active pane while Rule 3/4 could still fill the matching slot,
 * preventing the silent-keystroke-misroute regression from DA round 3.
 */
@Suppress("LongParameterList")
fun resolvePaneAssignments(
    tabs: List<TerminalTab>,
    leftPaneTabId: String?,
    rightPaneTabId: String?,
    activePaneIndex: Int,
    activeTabIndex: Int,
    isRestoringPanes: Boolean,
): PaneAssignment {
    // Rule 1: orphan cleanup (skipped during restore)
    var left = if (isRestoringPanes) leftPaneTabId
               else leftPaneTabId?.takeIf { id -> tabs.any { it.id == id } }
    var right = if (isRestoringPanes) rightPaneTabId
                else rightPaneTabId?.takeIf { id -> tabs.any { it.id == id } }

    // Rule 2: duplicate cleanup — non-active slot is cleared, preserves active
    if (left != null && left == right) {
        if (activePaneIndex == 0) right = null else left = null
    }

    // Rule 3: left-slot fill
    if (left == null && tabs.isNotEmpty()) {
        val activeId = tabs.getOrNull(activeTabIndex)?.id ?: tabs.first().id
        if (activeId != right) left = activeId
    }

    // Rule 4: right-slot fill
    if (right == null && tabs.size >= 2) {
        right = tabs.firstOrNull { it.id != left }?.id
    }

    // Re-run Rule 2 after fills — Rule 3's activeId may collide with right
    if (left != null && left == right) {
        if (activePaneIndex == 0) right = null else left = null
    }

    // Phase B: sanitization clamp (last-resort; never flips active when
    // Rules 3/4 could have filled the matching slot)
    var active = activePaneIndex.coerceIn(0, 1)
    if (active == 1 && right == null) active = 0
    if (active == 0 && left == null && right != null) active = 1

    return PaneAssignment(left, right, active)
}
```

- [ ] **Step 4: Ensure `TerminalTab` exists and has the fields the tests use**

Grep to confirm:

```
grep -n "data class TerminalTab\|class TerminalTab" /root/OriDev/feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/
```

`TerminalTab` is the existing domain-of-feature class used by `TerminalUiState`. Verify it has `id: String`, `profileId: Long`, `profileName: String`. If not, adapt the test's `tab()` helper to match.

- [ ] **Step 5: Run — expect PASS**

```
./gradlew :feature-terminal:testDebugUnitTest --tests "dev.ori.feature.terminal.ui.PaneAssignmentTest"
```

Expected: 12/12 green.

- [ ] **Step 6: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneAssignment.kt \
        feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/PaneAssignmentTest.kt
git commit -m "feat(terminal): add PaneAssignment reducer with two-stage safety"
```

---

## Task 4: `TerminalUiState` — three new fields

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt`

- [ ] **Step 1: Add fields to the data class**

Open `TerminalUiState.kt`. Add (default values matter — `activePaneIndex = 0`, both Tab-IDs null):

```kotlin
data class TerminalUiState(
    // ... existing fields (tabs, activeTabIndex, modifierState, keyboardMode, etc.) ...
    val leftPaneTabId: String? = null,
    val rightPaneTabId: String? = null,
    val activePaneIndex: Int = 0,
)
```

Make the addition idempotent — if any of these already exist from a prior attempt, don't duplicate.

- [ ] **Step 2: Build check**

```
./gradlew :feature-terminal:compileDebugKotlin
```

Expected: clean. Existing callers of `TerminalUiState(...)` positional still work because all three new fields have defaults.

- [ ] **Step 3: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt
git commit -m "feat(terminal): add leftPaneTabId, rightPaneTabId, activePaneIndex to ui state"
```

---

## Task 5: `TerminalViewModel` — `computeActiveTabId` helper

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`
- Modify: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `TerminalViewModelTest.kt`:

```kotlin
@Test
fun `computeActiveTabId returns left-pane tab in split active=0`() {
    val state = TerminalUiState(
        tabs = listOf(tab("T1"), tab("T2")),
        activeTabIndex = 0,
        leftPaneTabId = "T1",
        rightPaneTabId = "T2",
        activePaneIndex = 0,
    )
    assertThat(computeActiveTabId(state, isSplitActive = true)).isEqualTo("T1")
}

@Test
fun `computeActiveTabId returns right-pane tab in split active=1`() {
    val state = TerminalUiState(
        tabs = listOf(tab("T1"), tab("T2")),
        activeTabIndex = 0,
        leftPaneTabId = "T1",
        rightPaneTabId = "T2",
        activePaneIndex = 1,
    )
    assertThat(computeActiveTabId(state, isSplitActive = true)).isEqualTo("T2")
}

@Test
fun `computeActiveTabId returns activeTabIndex tab in single-pane`() {
    val state = TerminalUiState(
        tabs = listOf(tab("T1"), tab("T2")),
        activeTabIndex = 1,
        leftPaneTabId = "T1",
        rightPaneTabId = "T2",
        activePaneIndex = 0,
    )
    // isSplitActive=false means panes are ignored
    assertThat(computeActiveTabId(state, isSplitActive = false)).isEqualTo("T2")
}
```

`tab()` is an existing test helper in that file; keep using it.

- [ ] **Step 2: Run — expect FAIL**

```
./gradlew :feature-terminal:testDebugUnitTest --tests "dev.ori.feature.terminal.ui.TerminalViewModelTest"
```

Expected: unresolved `computeActiveTabId`.

- [ ] **Step 3: Add the helper — internal top-level function**

At the top of `TerminalViewModel.kt`, above the class (after imports), add:

```kotlin
/**
 * Resolves which tab ID should receive keystrokes + output. In split
 * mode (>=600dp + >=2 tabs) the active-pane slot wins; otherwise the
 * classic single-pane activeTabIndex applies.
 */
internal fun computeActiveTabId(state: TerminalUiState, isSplitActive: Boolean): String? =
    if (isSplitActive) {
        when (state.activePaneIndex) {
            1 -> state.rightPaneTabId
            else -> state.leftPaneTabId
        }
    } else {
        state.tabs.getOrNull(state.activeTabIndex)?.id
    }
```

- [ ] **Step 4: Run — expect PASS**

```
./gradlew :feature-terminal:testDebugUnitTest --tests "dev.ori.feature.terminal.ui.TerminalViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt \
        feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt
git commit -m "feat(terminal): add computeActiveTabId helper for split/single routing"
```

---

## Task 6: `TerminalViewModel` — restore-gate Phase 1 + 3

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`
- Modify: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `TerminalViewModelTest.kt`:

```kotlin
@Test
fun `restore phase 1 preloads pane IDs from preferences before tabs arrive`() = runTest {
    fakeResumePrefs.setLeftPaneTabId("tab-left")
    fakeResumePrefs.setRightPaneTabId("tab-right")
    fakeResumePrefs.setActivePaneIndex(1)

    val vm = createViewModel()  // fresh VM, no sessions yet
    advanceUntilIdle()

    assertThat(vm.state.value.leftPaneTabId).isEqualTo("tab-left")
    assertThat(vm.state.value.rightPaneTabId).isEqualTo("tab-right")
    assertThat(vm.state.value.activePaneIndex).isEqualTo(1)
}

@Test
fun `restore latch clears after Coordinator reaches Settled`() = runTest {
    val vm = createViewModel()
    fakeCoordinator.setRestoreState(ResumeCoordinator.RestoreState.InProgress)
    advanceTimeBy(1_000)
    assertThat(vm.isRestoringPanesForTest()).isTrue()

    fakeCoordinator.setRestoreState(ResumeCoordinator.RestoreState.Settled)
    advanceUntilIdle()
    assertThat(vm.isRestoringPanesForTest()).isFalse()
}

@Test
fun `restore latch clears via 60s timeout if Coordinator never settles`() = runTest {
    val vm = createViewModel()
    fakeCoordinator.setRestoreState(ResumeCoordinator.RestoreState.InProgress)
    advanceTimeBy(59_000)
    assertThat(vm.isRestoringPanesForTest()).isTrue()
    advanceTimeBy(1_001)
    assertThat(vm.isRestoringPanesForTest()).isFalse()
}
```

Use `@VisibleForTesting internal fun isRestoringPanesForTest() = isRestoringPanes.get()` to expose the latch.

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add the latch + restore sequence**

In `TerminalViewModel.kt`, inject `resumeCoordinator: ResumeCoordinator`:

```kotlin
@HiltViewModel
class TerminalViewModel @Inject constructor(
    // ... existing params ...
    private val resumeCoordinator: ResumeCoordinator,
) : ViewModel() {

    private val isRestoringPanes = AtomicBoolean(false)

    @VisibleForTesting
    internal fun isRestoringPanesForTest(): Boolean = isRestoringPanes.get()

    init {
        // ... existing init collectors (Task #8) ...
        launchRestoreGate()
    }

    private fun launchRestoreGate() {
        viewModelScope.launch {
            try {
                // Phase 1: preload pane IDs
                val left = sessionResumePrefs.leftPaneTabId.first()
                val right = sessionResumePrefs.rightPaneTabId.first()
                val active = sessionResumePrefs.activePaneIndex.first()

                _uiState.update {
                    it.copy(
                        leftPaneTabId = left,
                        rightPaneTabId = right,
                        activePaneIndex = active,
                    )
                }
                isRestoringPanes.set(true)

                // Phase 3: wait for terminal event (bounded)
                withTimeoutOrNull(RESTORE_TIMEOUT_MS) {
                    resumeCoordinator.restoreState
                        .filter { it == ResumeCoordinator.RestoreState.Settled }
                        .first()
                }
            } finally {
                isRestoringPanes.set(false)
                runReducer()  // re-apply full rules now
            }
        }
    }

    private fun runReducer() {
        _uiState.update { state ->
            val pa = resolvePaneAssignments(
                tabs = state.tabs,
                leftPaneTabId = state.leftPaneTabId,
                rightPaneTabId = state.rightPaneTabId,
                activePaneIndex = state.activePaneIndex,
                activeTabIndex = state.activeTabIndex,
                isRestoringPanes = isRestoringPanes.get(),
            )
            state.copy(
                leftPaneTabId = pa.leftPaneTabId,
                rightPaneTabId = pa.rightPaneTabId,
                activePaneIndex = pa.activePaneIndex,
            )
        }
    }

    private companion object {
        const val RESTORE_TIMEOUT_MS = 60_000L
    }
}
```

Import additions:
- `import androidx.annotation.VisibleForTesting`
- `import java.util.concurrent.atomic.AtomicBoolean`
- `import kotlinx.coroutines.flow.filter`
- `import kotlinx.coroutines.flow.first`
- `import kotlinx.coroutines.withTimeoutOrNull`
- `import dev.ori.data.session.ResumeCoordinator`

- [ ] **Step 4: Run — expect PASS**

```
./gradlew :feature-terminal:testDebugUnitTest --tests "dev.ori.feature.terminal.ui.TerminalViewModelTest"
./gradlew :app:assembleDebug
```

Expected: tests green, Hilt graph compiles clean.

- [ ] **Step 5: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt \
        feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt
git commit -m "feat(terminal): restore-gate preloads pane state and clears on Settled or 60s timeout"
```

---

## Task 7: `TerminalViewModel` — reducer hooks on every tabs mutation

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`
- Modify: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
@Test
fun `opening second tab fills right pane when left already populated`() = runTest {
    val vm = createViewModel()
    fakeResumePrefs.setActivePaneIndex(0)
    advanceUntilIdle()
    vm.openNewTab(profileId = 1L)
    advanceUntilIdle()
    vm.openNewTab(profileId = 2L)
    advanceUntilIdle()

    assertThat(vm.state.value.leftPaneTabId).isNotNull()
    assertThat(vm.state.value.rightPaneTabId).isNotNull()
    assertThat(vm.state.value.leftPaneTabId).isNotEqualTo(vm.state.value.rightPaneTabId)
}

@Test
fun `closing tab in left-slot refills slot from remaining tabs`() = runTest {
    val vm = createViewModel()
    vm.openNewTab(profileId = 1L)
    vm.openNewTab(profileId = 2L)
    advanceUntilIdle()
    val leftId = vm.state.value.leftPaneTabId!!
    val rightId = vm.state.value.rightPaneTabId!!

    vm.closeTab(leftId)
    advanceUntilIdle()

    assertThat(vm.state.value.leftPaneTabId).isEqualTo(rightId)  // right promoted
    assertThat(vm.state.value.rightPaneTabId).isNull()
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Call `runReducer()` at the end of `openNewTab`, `closeTab`, `selectTab`, `openNewTabInternal`, `disconnectProfile`**

In each of those methods, after the state update, add:

```kotlin
runReducer()
```

The helper already exists from Task 6. Because `runReducer()` uses `_uiState.update { … }`, calling it after another `_uiState.update` block is safe — it reads the latest state.

- [ ] **Step 4: Run — expect PASS**

```
./gradlew :feature-terminal:testDebugUnitTest --tests "dev.ori.feature.terminal.ui.TerminalViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt \
        feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt
git commit -m "feat(terminal): run pane-assignment reducer after every tab-list mutation"
```

---

## Task 8: `TerminalViewModel` — `setActivePane` + `moveTabToPane`

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`
- Modify: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
@Test
fun `setActivePane 1 updates state and persists`() = runTest {
    val vm = createViewModel()
    vm.openNewTab(profileId = 1L)
    vm.openNewTab(profileId = 2L)
    advanceUntilIdle()

    vm.setActivePane(1)
    advanceTimeBy(1_001)  // debounce

    assertThat(vm.state.value.activePaneIndex).isEqualTo(1)
    assertThat(fakeResumePrefs.activePaneIndex.first()).isEqualTo(1)
}

@Test
fun `moveTabToPane is transactional - no intermediate reducer pass`() = runTest {
    val vm = createViewModel()
    vm.openNewTab(profileId = 1L)
    vm.openNewTab(profileId = 2L)
    vm.openNewTab(profileId = 3L)
    advanceUntilIdle()
    val tab3 = vm.state.value.tabs.last().id
    val oldLeft = vm.state.value.leftPaneTabId!!

    vm.moveTabToPane(tab3, pane = 0)
    advanceUntilIdle()

    assertThat(vm.state.value.leftPaneTabId).isEqualTo(tab3)
    // Old left-pane tab was displaced but never "orphan-nulled"
    assertThat(vm.state.value.tabs.map { it.id }).contains(oldLeft)
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add the methods**

In `TerminalViewModel.kt`:

```kotlin
fun setActivePane(index: Int) {
    val clamped = index.coerceIn(0, 1)
    _uiState.update { it.copy(activePaneIndex = clamped) }
    runReducer()
    scheduleTabMemoSnapshot()  // existing debounce writer from Task #8 (PR #184)
}

fun moveTabToPane(tabId: String, pane: Int) {
    val target = pane.coerceIn(0, 1)
    _uiState.update { state ->
        val currentLeft = state.leftPaneTabId
        val currentRight = state.rightPaneTabId
        val (newLeft, newRight) = when (target) {
            0 -> {
                val displaced = if (currentLeft != null && currentLeft != tabId) currentLeft else null
                val right = when {
                    currentRight == tabId -> displaced
                    currentRight == null -> displaced
                    else -> currentRight
                }
                tabId to right
            }
            else -> {
                val displaced = if (currentRight != null && currentRight != tabId) currentRight else null
                val left = when {
                    currentLeft == tabId -> displaced
                    currentLeft == null -> displaced
                    else -> currentLeft
                }
                left to tabId
            }
        }
        state.copy(
            leftPaneTabId = newLeft,
            rightPaneTabId = newRight,
            activePaneIndex = target,
        )
    }
    runReducer()  // only once, after transactional update
    scheduleTabMemoSnapshot()
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt \
        feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt
git commit -m "feat(terminal): setActivePane + transactional moveTabToPane"
```

---

## Task 9: `TerminalViewModel` — extend snapshot writer with 3 pane fields

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt`
- Modify: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `snapshot writer persists leftPaneTabId, rightPaneTabId, activePaneIndex`() = runTest {
    val vm = createViewModel()
    vm.openNewTab(profileId = 1L)
    vm.openNewTab(profileId = 2L)
    advanceUntilIdle()
    vm.setActivePane(1)
    advanceTimeBy(1_001)  // 1s debounce window

    assertThat(fakeResumePrefs.leftPaneTabId.first()).isNotNull()
    assertThat(fakeResumePrefs.rightPaneTabId.first()).isNotNull()
    assertThat(fakeResumePrefs.activePaneIndex.first()).isEqualTo(1)
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Extend `scheduleTabMemoSnapshot` body**

Find the existing `scheduleTabMemoSnapshot()` function (added in Task #8 of PR #184, writes `tabMemos` and `focusedProfileId`). Inside its `launch { delay(1_000); … }` block, after the existing writes, add:

```kotlin
sessionResumePrefs.setLeftPaneTabId(_uiState.value.leftPaneTabId)
sessionResumePrefs.setRightPaneTabId(_uiState.value.rightPaneTabId)
sessionResumePrefs.setActivePaneIndex(_uiState.value.activePaneIndex)
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt \
        feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt
git commit -m "feat(terminal): persist pane state in debounced snapshot writer"
```

---

## Task 10: `PaneContent` extraction + empty-state sheet

**Files:**
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneContent.kt`

- [ ] **Step 1: Extract pane body into a new composable**

Create `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneContent.kt`:

```kotlin
package dev.ori.feature.terminal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.icons.lucide.LayoutGrid
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Plus

/**
 * Renders a single pane's terminal body. Stateless: the focus flag +
 * active-flag are passed in; onTap requests a focus switch from the
 * caller's ViewModel.
 *
 * When [tab] is null, renders an empty-state with "Neuer Tab hier
 * öffnen" as the first row of a picker BottomSheet, then all tabs not
 * currently bound to a pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaneContent(
    tab: TerminalTab?,
    isFocused: Boolean,
    isSplitActive: Boolean,
    allTabs: List<TerminalTab>,
    leftPaneTabId: String?,
    rightPaneTabId: String?,
    contentDescription: String,
    traversalPriority: Float,
    onTap: () -> Unit,
    onPickTab: (String) -> Unit,
    onNewTabInThisSlot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isFocused) 2.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(width = borderWidth, color = borderColor)
            .clickable(onClick = onTap)
            .semantics {
                this.contentDescription = contentDescription
                traversalIndex = traversalPriority
                if (!isFocused) liveRegion = LiveRegionMode.Polite
            },
    ) {
        if (tab == null) {
            var pickerOpen by remember { mutableStateOf(false) }
            EmptyPaneBody(
                onPickTap = { pickerOpen = true },
            )
            if (pickerOpen) {
                val sheetState = rememberModalBottomSheetState()
                ModalBottomSheet(
                    onDismissRequest = { pickerOpen = false },
                    sheetState = sheetState,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = {
                            onNewTabInThisSlot()
                            pickerOpen = false
                        }) {
                            Icon(LucideIcons.Plus, contentDescription = null)
                            Text("Neuer Tab hier öffnen", modifier = Modifier.padding(start = 8.dp))
                        }
                        // Filter out tabs already visible in either pane
                        val candidates = allTabs.filter {
                            it.id != leftPaneTabId && it.id != rightPaneTabId
                        }
                        candidates.forEach { candidate ->
                            TextButton(onClick = {
                                onPickTab(candidate.id)
                                pickerOpen = false
                            }) {
                                Text(candidate.profileName)
                            }
                        }
                    }
                }
            }
        } else {
            // Wrap the existing TerminalView / TermSessionView invocation.
            // Delegate via a local slot: implementers reuse the concrete
            // session-view composable that TerminalScreen used pre-split.
            TerminalSessionView(
                tab = tab,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EmptyPaneBody(
    onPickTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onPickTap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = LucideIcons.LayoutGrid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "Tab auswählen",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
```

`TerminalSessionView` is the existing session-body composable. Grep for its exact name (likely `TerminalSessionView`, `TermSessionView`, or similar):

```
grep -rn "fun TerminalSessionView\|fun TermSessionView\|TermView" /root/OriDev/feature-terminal/src/main/kotlin/dev/ori/feature/terminal/
```

Replace the placeholder `TerminalSessionView(tab = tab, modifier = …)` with the real composable reference.

- [ ] **Step 2: Build**

```
./gradlew :feature-terminal:compileDebugKotlin
```

Expected: clean. If the existing session-view composable has different parameters (e.g. takes `sessionId` instead of `TerminalTab`), adapt the call.

- [ ] **Step 3: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneContent.kt
git commit -m "feat(terminal): extract PaneContent composable with empty-state picker"
```

---

## Task 11: `TerminalScreen` — split-or-single rendering

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt`

- [ ] **Step 1: Replace the pane body with the split-aware block**

In `TerminalScreen.kt`, inside the main content `Column`, locate where the terminal body is rendered. Replace it with:

```kotlin
val configuration = LocalConfiguration.current
val isSplitAvailable = configuration.screenWidthDp >= SPLIT_THRESHOLD_DP
val isSplitActive = isSplitAvailable && state.tabs.size >= 2

if (isSplitActive) {
    val leftTab = state.tabs.firstOrNull { it.id == state.leftPaneTabId }
    val rightTab = state.tabs.firstOrNull { it.id == state.rightPaneTabId }
    val isLeftActive = state.activePaneIndex == 0

    Row(modifier = Modifier.fillMaxSize().weight(1f)) {
        PaneContent(
            tab = leftTab,
            isFocused = isLeftActive,
            isSplitActive = true,
            allTabs = state.tabs,
            leftPaneTabId = state.leftPaneTabId,
            rightPaneTabId = state.rightPaneTabId,
            contentDescription = "Terminal links",
            traversalPriority = if (isLeftActive) 0f else 1f,
            onTap = { viewModel.setActivePane(0) },
            onPickTab = { tabId -> viewModel.moveTabToPane(tabId, pane = 0) },
            onNewTabInThisSlot = { viewModel.openNewTab(profileId = null) },
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(modifier = Modifier.width(1.dp))
        PaneContent(
            tab = rightTab,
            isFocused = !isLeftActive,
            isSplitActive = true,
            allTabs = state.tabs,
            leftPaneTabId = state.leftPaneTabId,
            rightPaneTabId = state.rightPaneTabId,
            contentDescription = "Terminal rechts",
            traversalPriority = if (isLeftActive) 1f else 0f,
            onTap = { viewModel.setActivePane(1) },
            onPickTab = { tabId -> viewModel.moveTabToPane(tabId, pane = 1) },
            onNewTabInThisSlot = { viewModel.openNewTab(profileId = null) },
            modifier = Modifier.weight(1f),
        )
    }
} else {
    val singleTab = state.tabs.getOrNull(state.activeTabIndex)
    PaneContent(
        tab = singleTab,
        isFocused = true,
        isSplitActive = false,
        allTabs = state.tabs,
        leftPaneTabId = state.leftPaneTabId,
        rightPaneTabId = state.rightPaneTabId,
        contentDescription = "Terminal",
        traversalPriority = 0f,
        onTap = {},
        onPickTab = { /* single-pane ignores picker */ },
        onNewTabInThisSlot = { viewModel.openNewTab(profileId = null) },
        modifier = Modifier.fillMaxSize().weight(1f),
    )
}
```

Add imports:
- `import androidx.compose.foundation.layout.Row`
- `import androidx.compose.foundation.layout.width`
- `import androidx.compose.material3.VerticalDivider`
- `import androidx.compose.ui.platform.LocalConfiguration`

Add a private constant to the file:
```kotlin
private const val SPLIT_THRESHOLD_DP = 600
```

- [ ] **Step 2: Build + manual smoke**

```
./gradlew :feature-terminal:assembleDebug
./gradlew :app:assembleDebug
```

Expected: clean build.

- [ ] **Step 3: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt
git commit -m "feat(terminal): split-or-single rendering at screenWidthDp >= 600"
```

---

## Task 12: `TerminalTabBar` — dual-underline + auto-scroll + long-press popup

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalTabBar.kt`

- [ ] **Step 1: Extend the TabBar signature**

Find the existing `TerminalTabBar` composable. Add the new params:

```kotlin
@Composable
internal fun TerminalTabBar(
    // ... existing ...
    leftPaneTabId: String?,
    rightPaneTabId: String?,
    activePaneIndex: Int,
    isSplitActive: Boolean,
    onMoveTabToPane: (tabId: String, pane: Int) -> Unit,
    // ...
) { ... }
```

- [ ] **Step 2: Compute per-tab underline style**

Inside the `LazyRow` item body, replace the existing underline calculation with:

```kotlin
val focusedPaneTabId = when (activePaneIndex) {
    1 -> rightPaneTabId
    else -> leftPaneTabId
}
val otherPaneTabId = when (activePaneIndex) {
    1 -> leftPaneTabId
    else -> rightPaneTabId
}

val underlineColor = when (tab.id) {
    focusedPaneTabId -> MaterialTheme.colorScheme.primary
    otherPaneTabId -> MaterialTheme.colorScheme.outlineVariant
    else -> Color.Transparent
}
val underlineWidth = when (tab.id) {
    focusedPaneTabId -> 2.dp
    otherPaneTabId -> 1.dp
    else -> 0.dp
}

Column {
    // ... existing tab label + icon row ...
    Spacer(Modifier.height(underlineWidth).fillMaxWidth().background(underlineColor))
}
```

- [ ] **Step 3: Auto-scroll to focused-pane tab**

Inject a `LazyListState`:

```kotlin
val listState = rememberLazyListState()
LaunchedEffect(focusedPaneTabId) {
    val index = tabs.indexOfFirst { it.id == focusedPaneTabId }
    if (index >= 0) listState.animateScrollToItem(index)
}
```

Use the state in the existing `LazyRow`:

```kotlin
LazyRow(state = listState, ...) { ... }
```

- [ ] **Step 4: Add long-press popup with dismiss-on-fold**

Wrap each tab item in `combinedClickable(onClick = ..., onLongClick = { popupTabId = tab.id })`.

State + popup:

```kotlin
var popupTabId by remember { mutableStateOf<String?>(null) }

LaunchedEffect(isSplitActive) {
    if (!isSplitActive) popupTabId = null
}

popupTabId?.let { tabId ->
    if (isSplitActive) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = { popupTabId = null },
        ) {
            DropdownMenuItem(
                text = { Text("In linken Pane bewegen") },
                onClick = {
                    onMoveTabToPane(tabId, 0)
                    popupTabId = null
                },
            )
            DropdownMenuItem(
                text = { Text("In rechten Pane bewegen") },
                onClick = {
                    onMoveTabToPane(tabId, 1)
                    popupTabId = null
                },
            )
            DropdownMenuItem(
                text = { Text("Schließen") },
                onClick = {
                    onCloseTab(tabId)
                    popupTabId = null
                },
            )
        }
    }
}
```

Add imports:
- `import androidx.compose.foundation.ExperimentalFoundationApi`
- `import androidx.compose.foundation.combinedClickable`
- `import androidx.compose.foundation.lazy.rememberLazyListState`
- `import androidx.compose.material3.DropdownMenu`
- `import androidx.compose.material3.DropdownMenuItem`

- [ ] **Step 5: Wire new params in `TerminalScreen.kt`**

Where `TerminalTabBar(...)` is called, pass the three new arguments:

```kotlin
TerminalTabBar(
    // ... existing args ...
    leftPaneTabId = state.leftPaneTabId,
    rightPaneTabId = state.rightPaneTabId,
    activePaneIndex = state.activePaneIndex,
    isSplitActive = isSplitActive,
    onMoveTabToPane = viewModel::moveTabToPane,
)
```

- [ ] **Step 6: Build**

```
./gradlew :feature-terminal:assembleDebug
```

- [ ] **Step 7: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalTabBar.kt \
        feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt
git commit -m "feat(terminal): tab-bar dual-underline, auto-scroll, long-press move popup"
```

---

## Task 13: `KeyboardHost` — `computeActiveTabId` routing + IME focus guard

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/KeyboardHost.kt`

- [ ] **Step 1: Route keystrokes through the helper**

Find the existing `onKey(...)` / `writeToStdin(...)` / `viewModel.sendInput(...)` path. Replace the hardcoded `activeTabIndex`-based target with:

```kotlin
val activeTabId = computeActiveTabId(state, isSplitActive)
```

Pass `activeTabId` down to wherever the current code resolves "which PTY receives this keystroke". If the VM currently has `fun sendInput(input: String)` without a tabId param, it must now read `_uiState.value` via `computeActiveTabId(state, isSplitActive)`. Add an `isSplitActive: Boolean` param to `sendInput` — the caller site in `KeyboardHost` already knows it.

- [ ] **Step 2: Add guarded IME focus re-request**

At the top of `KeyboardHost`, add:

```kotlin
val imeVisible = WindowInsets.isImeVisible
LaunchedEffect(state.activePaneIndex) {
    if (state.keyboardMode != KeyboardMode.CUSTOM && imeVisible) {
        imeFocusRequester.requestFocus()
    }
}
```

Add imports:
- `import androidx.compose.foundation.layout.WindowInsets`
- `import androidx.compose.foundation.layout.isImeVisible`
- `import androidx.compose.runtime.LaunchedEffect`

`imeFocusRequester` is the existing `FocusRequester` for the `TerminalImeAnchor` (Phase 14). Reuse it.

- [ ] **Step 3: Build**

```
./gradlew :feature-terminal:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/KeyboardHost.kt \
        feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt
git commit -m "feat(terminal): route keystrokes to computeActiveTabId, guard IME re-focus"
```

---

## Task 14: `ResizeTerminal` per-tabId debounce + 30ms fold stagger

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalView.kt` (or wherever `ResizeTerminal` debounce lives)

- [ ] **Step 1: Verify the debounce key**

Grep the resize-debounce logic:

```
grep -rn "ResizeTerminal\|resize.*debounce\|debounceResize" /root/OriDev/feature-terminal/src/main/kotlin/dev/ori/feature/terminal/
```

Confirm the debounce's cancel-key includes `tabId`. If it's keyed only by event-type (e.g. a single `resizeJob: Job?` field), split it into a `ConcurrentHashMap<String, Job>` keyed by `tabId`:

```kotlin
private val resizeJobs = ConcurrentHashMap<String, Job>()

fun scheduleResize(tabId: String, cols: Int, rows: Int) {
    resizeJobs[tabId]?.cancel()
    resizeJobs[tabId] = viewModelScope.launch {
        delay(200)
        sshClient.writeWindowChange(tabId, cols, rows)
    }
}
```

- [ ] **Step 2: Add 30ms stagger on fold-transition emissions**

Where both panes emit resize on fold (the `LaunchedEffect(isSplitActive)` block in `TerminalScreen.kt` or the VM method that fires on config change), serialize the two:

```kotlin
LaunchedEffect(isSplitActive) {
    if (isSplitActive) {
        state.leftPaneTabId?.let { viewModel.scheduleResize(it, splitCols, rows) }
        delay(30)
        state.rightPaneTabId?.let { viewModel.scheduleResize(it, splitCols, rows) }
    } else {
        val single = state.tabs.getOrNull(state.activeTabIndex)?.id
        single?.let { viewModel.scheduleResize(it, singleCols, rows) }
    }
}
```

Use `val splitCols = (screenWidthDp - 1) / 2 / CHAR_WIDTH_DP` and `singleCols = screenWidthDp / CHAR_WIDTH_DP` where `CHAR_WIDTH_DP` is the existing font-metric const.

- [ ] **Step 3: Build**

```
./gradlew :feature-terminal:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalView.kt \
        feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt \
        feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt
git commit -m "feat(terminal): per-tabId resize debounce + 30ms stagger on fold"
```

---

## Task 15: Full gate — build, tests, detekt, semgrep, manual QA

- [ ] **Step 1: Assemble + unit tests + lint**

```
cd /root/OriDev
./gradlew assembleDebug testDebugUnitTest detekt
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Semgrep (ERROR only)**

```
semgrep --config .semgrep.yml --no-git-ignore --severity=ERROR --error .
```

Expected: 0 ERROR findings. WARNING findings are pre-existing (see memory `reference_oridev_cicd_quirks.md`).

- [ ] **Step 3: Manual QA on Pixel Fold**

Install the debug APK on a Pixel Fold (or `screenWidthDp`-configured emulator):

```
./gradlew :app:installDebug
```

Smoke-check:
- 1 Tab + unfolded → single-pane (no split, no empty right-pane — `tabs.size >= 2` gate).
- 2 Tabs + unfolded → split fills both panes, focus border visible.
- Tap inactive pane → border flips, keystrokes route to it.
- Fold device mid-typing in right-pane → left-pane remains rendered with the right-pane tab (if `activePaneIndex == 1`); or stays on its own tab if `activePaneIndex == 0`. No keystroke loss.
- Long-press on tab → popup shows "In linken/rechten Pane bewegen" + "Schließen".
- Fold while popup is open → popup dismisses automatically.
- Close active-pane tab → slot refills from remaining tabs, or shows empty-state.
- Toggle auto-resume ON + open 2 tabs in 2 panes + kill app → on relaunch, layout restored.

- [ ] **Step 4: If any QA issue surfaces, fix + commit**

Each fix is a separate commit with `fix(terminal): ...` prefix.

---

## Task 16: Branch + PR + auto-merge + patch-release trigger

- [ ] **Step 1: Verify baseline + branch off master**

```
cd /root/OriDev
git fetch origin master
git checkout master
git pull --ff-only
git log --oneline -3
```

- [ ] **Step 2: Tag the pre-task baseline BEFORE starting Task 1**

(If the implementer hasn't done this yet — best practice for future cherry-picks.)

```
git tag pre-task-34-baseline master
```

- [ ] **Step 3: Create the feature branch (if you worked on a doc-branch)**

If Tasks 1-15 landed on a docs-branch, cherry-pick their commits:

```
git checkout -b feat/foldable-split-terminal
git cherry-pick pre-task-34-baseline..docs/foldable-split-terminal-design
```

Resolve any merge conflicts (expected none if master hasn't moved).

If Tasks 1-15 already ran directly on `feat/foldable-split-terminal`, skip this step.

- [ ] **Step 4: Verify commit range + author**

```
git log --format='%an <%ae> | %s' master..HEAD
```

Expected: every commit authored `CallMeTechie <ma.backes@outlook.com>`, no `Co-Authored-By` trailers (`git log --format=%B master..HEAD | grep -i Co-Authored` returns empty).

- [ ] **Step 5: Push and open PR**

```bash
git push -u origin feat/foldable-split-terminal

gh pr create --title "feat: foldable split terminal (two tabs parallel on unfolded)" --body "$(cat <<'EOF'
## Summary
- Implements task #34: on Pixel Fold unfolded (screenWidthDp >= 600) the Terminal shows two tabs side-by-side
- Auto-split at >=2 tabs; single-pane otherwise
- Single keyboard target (focused pane); tap-to-switch; border indicator
- Persistence extends Task #33's SessionResumePreferences with 3 new keys
- Cold-start restore-gate prevents silent pane-ID orphaning during asynchronous tab restoration

## Design + plan
- Spec: `docs/superpowers/specs/2026-04-24-foldable-split-terminal-design.md` (Rev 4, three devil's-advocate rounds applied)
- Plan: `docs/superpowers/plans/2026-04-24-foldable-split-terminal.md` (16 tasks)

## Test plan
- [ ] 2 tabs + unfolded → split layout renders, border indicators visible
- [ ] Tap inactive pane → keystrokes route to it
- [ ] Fold device mid-typing → no keystroke lost, active pane follows the Tab
- [ ] Long-press tab → move-to-pane popup; fold dismisses popup automatically
- [ ] Auto-resume ON + kill + reopen unfolded → layout restored
- [ ] All tests green: `./gradlew assembleDebug testDebugUnitTest detekt`
- [ ] Semgrep ERROR: 0

🔗 Tracks: task #34 (Multi-Window-Foldable-Support für parallele Terminal-Tabs)
EOF
)"
```

- [ ] **Step 6: Enable auto-merge**

```bash
gh pr merge --auto --squash
```

- [ ] **Step 7: Monitor CI**

```bash
gh pr checks --watch
```

If any check fails (e.g. `Mockup Layout Gate` androidTest), read the failure, commit a fix on the same branch, push. Auto-merge completes once all required checks pass.

- [ ] **Step 8: Trigger patch release after merge**

Orphan tag quirk from memory: `feat:` commits want minor bump → `v0.34.0` is blocked (orphan tag) → release workflow skips. Trigger a patch manually:

```bash
gh workflow run release.yml -f bump=patch --ref master
gh run watch $(gh run list --workflow release.yml --limit 1 --json databaseId --jq '.[0].databaseId')
```

Expected: `v0.33.23` (or whatever is next) lands on the Releases page.

- [ ] **Step 9: Update local task #34 to completed**

Close the PR loop in the local task list.
