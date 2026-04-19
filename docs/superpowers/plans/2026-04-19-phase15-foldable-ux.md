# Ori:Dev Phase 15: Foldable UX + Storage Access Framework

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute task-by-task.

**Goal:** Make the app actually usable on the unfolded Pixel Fold:
- The HYBRID/SYSTEM_ONLY keyboard modes ship with a 200–400px gap between the extra-keys row and the system IME — driven by a wrong layer for `imePadding()` plus `Arrangement.Bottom`. Fix the layout invariant.
- Replace the linear linksbündig CustomKeyboard with a split layout (left half / gap / right half) on `screenWidthDp >= 600` so both thumbs reach naturally without re-gripping the device.
- The Send-to-Claude FAB permanently floats over the bottom-right of the keyboard area. Drop it; the contextual selection-toolbar pathway will land in a later phase.
- Compact the Material3 NavigationBar (80dp + insets ≈ 104dp) and add a NavigationRail variant for `screenWidthDp >= 600` so vertical real estate is reclaimed.
- Tighten `FileItemRow` density.
- File access is silently broken: legacy `READ/WRITE_EXTERNAL_STORAGE` is in the manifest but ignored on API 30+, the onboarding never asked for storage, and Settings has no re-request path. Replace with **Storage Access Framework** (SAF) — Play-Store-safe, persistent grants on user-picked trees, surfaced in Settings.

**Stack:** Compose, Hilt, DocumentFile (`androidx.documentfile`), `WindowInsets.isImeVisible`, NavigationRail (`androidx.compose.material3`).

**Out of scope (deferred):**
- DB/DataStore migration repair (Phase 16).
- Send-to-Claude as a Compose `SelectionContainer`-toolbar action (own phase).
- Hardware-keyboard layout tuning.

---

## Tasks

### Task 15.1 — Drop Send-to-Claude FAB

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt`

- [ ] Remove the `floatingActionButton = { ExtendedFloatingActionButton(...) }` block from the Scaffold (around line 161).
- [ ] Drop now-unused imports (`ExtendedFloatingActionButton`, `LucideIcons.Zap` if unreferenced).
- [ ] Keep the `SendToClaudeSheet` composable wired (it's still triggered from `ToggleCodeBlocksSheet` and the codeblock detector path). Just remove the FAB entry point.
- [ ] Verify: `./gradlew :feature-terminal:test :app:assembleDebug detekt` green.

### Task 15.2 — Keyboard insets layout invariant

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/KeyboardHost.kt`
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt`
- Create: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/KeyboardHostInsetsTest.kt`

**Diagnose:** `KeyboardHost`'s HYBRID branch wraps `ExtraKeys + Anchor` in a `Column(imePadding(), Arrangement.Bottom)`. `imePadding()` reserves the IME height as bottom inset, so the 53dp content sits at the BOTTOM of a 53dp + IME-height column — i.e. 200-400px above the IME, not directly on it. SYSTEM_ONLY has the same shape and ends up showing the Scaffold background as a "grey strip".

- [ ] **Step 1** — Remove `imePadding()` from KeyboardHost's HYBRID and SYSTEM_ONLY branches. The HYBRID Column becomes a plain `Column { ExtraKeys; Anchor }` with no padding modifier; SYSTEM_ONLY just renders `TerminalImeAnchor` directly.
- [ ] **Step 2** — In `TerminalScreen.kt`, attach `Modifier.imePadding()` to the **root Column** (the one inside `Scaffold { innerPadding -> Column(...) }`). This pushes the entire terminal stack up uniformly when the IME opens. The Compose compositor reacts to IME size changes per-frame, so a Gboard one-handed-mode resize is handled automatically.
- [ ] **Step 3** — Hide ExtraKeys when IME is not visible: wrap `TerminalExtraKeys` in `if (WindowInsets.isImeVisible) { ... }` inside KeyboardHost. Use `androidx.compose.foundation.layout.WindowInsets.Companion.isImeVisible` (Compose 1.6+). When the user dismisses Gboard via back-gesture, the row vanishes too.
- [ ] **Step 4** — Tests: integration-style Compose UI test verifying the column structure no longer has imePadding (snapshot on the modifier chain), plus a unit-style test that asserts `WindowInsets.isImeVisible` is read.
- [ ] **Step 5** — Verify on real device: HYBRID mode → Gboard slides up → ExtraKeys sits flush above Gboard, no gap; toggle one-hand mode → row follows; back-gesture dismiss → row hides, terminal reclaims the space.

### Task 15.3 — Split CustomKeyboard for unfolded foldable

**Files:**
- Modify: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/CustomKeyboard.kt`
- Create: `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/CustomKeyboardLayout.kt`
- Modify: `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/CustomKeyboardTest.kt` (if it exists; otherwise create)

- [ ] **Step 1** — Read `CustomKeyboard.kt` and identify the existing horizontal rows. Map each key to a "side": left (Q–G, Esc, Tab, Ctrl, Alt, function row left-half) or right (H–M + numbers/symbols, arrows, Backspace, Enter).
- [ ] **Step 2** — New file `CustomKeyboardLayout.kt`: extract the row definitions (already in `CustomKeyboard.kt` as `KeyButton(...)` calls) into pure data — `internal data class KeyboardRow(val left: List<KeySpec>, val right: List<KeySpec>)`. The left+right split is fixed per-row; mid-row gap is implicit.
- [ ] **Step 3** — In `CustomKeyboard`, branch on `LocalConfiguration.current.screenWidthDp >= 600`:
  - `< 600`: render the existing single-block layout untouched (Phone path).
  - `>= 600`: `Row { LeftHalf weight 1; Spacer(80.dp); RightHalf weight 1 }`. Each half is a `Column` of horizontally-arranged keys.
- [ ] **Step 4** — Visual check: latched-state visuals (Ctrl/Alt highlight) must still work because they share the same `modifierState`. Ensure both halves observe the same state.
- [ ] **Step 5** — Mockup diff: update `Mockups/terminal.html` to show the split layout. Regenerate `.github/mockup-hash.txt`.
- [ ] **Step 6** — Tests: Robolectric test under `Configuration.screenWidthDp = 800` confirms the split renders both halves; phone-width test confirms the legacy path is unchanged.

### Task 15.4 — NavigationBar compact + NavigationRail for foldable

**Files:**
- Modify: `app/src/main/kotlin/dev/ori/app/ui/OriDevApp.kt`

- [ ] **Step 1** — Read OriDevApp.kt: identify the `NavigationBar { NavigationBarItem ... }` setup. Currently five items (Connect / Files / Terminal / Transfers / Settings).
- [ ] **Step 2** — On `screenWidthDp < 600`: keep `NavigationBar` but constrain its height to ~64dp via a wrapper `Box(Modifier.height(64.dp))` and pass `windowInsets = NavigationBarDefaults.windowInsets`. Drop the inner padding contributing to the 80dp default. Labels stay (small).
- [ ] **Step 3** — On `screenWidthDp >= 600`: replace with `NavigationRail` on the LEFT side. The Scaffold structure changes from `Column(content, BottomBar)` to `Row(NavigationRail, Scaffold(content))`. Test with the unfolded Pixel Fold mockup width (~770dp).
- [ ] **Step 4** — NavigationRail items use the same icon set (Lucide), same routes, same active-state semantics. Item header optional (could show app name).
- [ ] **Step 5** — Verify: rotation between phone-width and unfolded preserves selected route.
- [ ] **Step 6** — Mockup: not strictly required (NavigationBar is a system-frame component, not in mockups), but if `Mockups/index.html` shows the bar visually, update.

### Task 15.5 — Compact FileItemRow

**Files:**
- Modify: `feature-filemanager/src/main/kotlin/dev/ori/feature/filemanager/ui/FileItemRow.kt`

- [ ] Reduce vertical padding from `8.dp` to `4.dp` (line ~90).
- [ ] Reduce icon container from default 48dp to 32dp.
- [ ] Keep `Modifier.minimumInteractiveComponentSize()` on the click target so accessibility touch-target (48dp) is preserved invisibly.
- [ ] Verify: visual density matches a power-user file manager (~40dp row height).
- [ ] No tests required — pure layout tweak, covered by mockup gate.

### Task 15.6 — Storage Access Framework integration

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/repository/StorageAccessRepository.kt` (interface)
- Create: `data/src/main/kotlin/dev/ori/data/repository/StorageAccessRepositoryImpl.kt`
- Create: `data/src/main/kotlin/dev/ori/data/storage/PersistedTreeStore.kt` (DataStore-backed list of granted tree URIs)
- Create: `data/src/main/kotlin/dev/ori/data/di/StorageAccessModule.kt`
- Modify: `feature-filemanager/.../ui/FileManagerViewModel.kt` (route local-FS reads through SAF DocumentFile)
- Modify: `feature-filemanager/.../ui/FileManagerScreen.kt` (`ActivityResultContracts.OpenDocumentTree` launcher)
- Create: `feature-settings/.../sections/StorageAccessSection.kt` (Settings entry: list granted trees + add/remove)
- Modify: `app/src/main/AndroidManifest.xml` (drop deprecated `READ/WRITE_EXTERNAL_STORAGE`; SAF needs no permission)
- Modify: `feature-onboarding/.../ui/PermissionsScreen.kt` (add an explainer card with a "Pick storage folder" button)
- Tests: round-trip on `PersistedTreeStore`, repository fake for ViewModel tests.

**Steps:**

- [ ] **Step 1 — Repository contract.** `StorageAccessRepository` exposes `Flow<List<GrantedTree>>`, `suspend fun grant(uri: Uri)`, `suspend fun revoke(uri: Uri)`. `GrantedTree` data class: `uri`, `displayName`, `documentId`. Keep in `:domain` (pure Kotlin — `Uri` lives in `:core-common` as a `UriRef` value-class wrapping a String, so domain doesn't import `android.net.Uri` directly).
- [ ] **Step 2 — Persistence.** `PersistedTreeStore` in `:data` uses its own DataStore file `ori_storage_trees.preferences_pb` with a `stringSetPreferencesKey` of URI strings. On `grant()`, also call `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ | FLAG_GRANT_WRITE)`. On `revoke()`, `releasePersistableUriPermission(uri, ...)` and remove from the set.
- [ ] **Step 3 — FileManagerViewModel rewrite for local FS.** Currently the local pane uses `java.io.File`. Replace with `androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)`. The ViewModel takes `StorageAccessRepository`, exposes `state.grantedTrees`, and shows a top-level "Choose a folder" CTA when the list is empty. Selected tree → DocumentFile listing.
- [ ] **Step 4 — FileManagerScreen launcher.** Use `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())` to call SAF's system picker. On result, dispatch `FileManagerEvent.GrantTree(uri)`.
- [ ] **Step 5 — Settings section.** New `StorageAccessSection` listed in `SettingsScreen`. Renders the granted-trees list with `displayName`, `Remove` button per row, and an `Add folder…` button at the bottom (re-uses the same SAF launcher).
- [ ] **Step 6 — Manifest cleanup.** Remove `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` from `app/src/main/AndroidManifest.xml`. SAF needs no manifest permission.
- [ ] **Step 7 — Onboarding.** Replace the silent storage step with an explanatory card: "Ori:Dev needs you to pick which folder(s) it can read/write. Tap to choose." Tapping triggers the SAF picker; result is persisted same as the Settings flow.
- [ ] **Step 8 — Tests:** `PersistedTreeStoreTest` round-trip + uniqueness; `StorageAccessRepositoryImplTest` with a fake ContentResolver verifying `takePersistableUriPermission` is called; `FileManagerViewModelTest` for the empty-state + tree-listing path with a fake repository.
- [ ] **Step 9 — Documentation.** Update `CLAUDE.md` Security section: "Local file access uses SAF DocumentFile only; no `MANAGE_EXTERNAL_STORAGE`. Granted trees persist via `takePersistableUriPermission` and are surfaced + removable in Settings → Storage Access."

---

## Definition of Done

**Functional (manual on the Pixel Fold, unfolded):**
- [ ] HYBRID mode: ExtraKeys row sits flush above Gboard, no gap. Toggling Gboard one-hand mode → row follows. Back-gesture dismiss → row hides, terminal reclaims space.
- [ ] SYSTEM_ONLY mode: no grey strip, terminal flush above IME.
- [ ] CustomKeyboard split: thumbs reach left half + right half without re-gripping; mid-gap visible.
- [ ] No FAB visible in the terminal screen.
- [ ] NavigationRail on the left when unfolded; compact NavigationBar on phone.
- [ ] FileItemRow density: ~40dp rows (was ~64dp).
- [ ] FileManager on first launch: empty state → "Choose folder" → SAF picker → tree appears in list. Settings shows the granted tree, Remove works.
- [ ] After app restart: the granted tree is still listed and accessible (persistent permission).

**Build gates:**
- [ ] `./gradlew test detekt :app:assembleDebug` green across all touched modules.
- [ ] Mockup-hash check green after Tasks 15.3 + 15.5 update mockups.
- [ ] Semgrep gate green.

**Out of scope, tracked for follow-up:**
- DB / DataStore migration audit (Phase 16).
- Send-to-Claude as a SelectionContainer toolbar action.

---

## Review-pass checklist (devil's-advocate to run after Tasks 15.2 and 15.6 land)

- 15.2: Does removing imePadding from KeyboardHost break the CUSTOM mode? (CustomKeyboard is rendered by KeyboardHost too — but its layout is weight-based via the parent, no IME-padding involvement.)
- 15.6: What happens when the user revokes a tree externally (System Settings → App → Storage)? The persisted URI becomes invalid; we MUST detect this on next read and re-prompt instead of crashing.
