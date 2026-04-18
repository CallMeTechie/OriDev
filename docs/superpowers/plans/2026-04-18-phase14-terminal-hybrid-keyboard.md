# Ori:Dev Phase 14: Terminal Hybrid Keyboard (System IME + Extra-Keys Row)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bottom-half slot currently occupied by `CustomKeyboard` with a hybrid layout that lets the user type with the Android system IME (Gboard, SwiftKey, Hacker's Keyboard, etc.) while keeping the essential terminal modifier keys (Esc, Tab, Ctrl, Alt, arrow cluster, Fn-row, `|` `/` `~` `\``) available as a thin sticky toolbar pinned to the top of the IME. The existing `CustomKeyboard` remains available as an opt-in preference for users who prefer it.

**Motivation:** On the Pixel Fold, users already rely on Gboard for swipe-typing, voice input, emoji, theming, and on-device corrections. Today the custom soft keyboard forces them out of that muscle memory. Major Android SSH apps (Termux, JuiceSSH, Termius) all use the hybrid pattern because pure system IMEs don't expose Ctrl/Alt and terminal work collapses without them.

**Architecture:**
- `TerminalScreen` keeps its current `Column(Terminal / Divider / Input)` shape on wide screens.
- The old `CustomKeyboard` slot becomes a **KeyboardHost** composable that renders one of three modes based on a new `KeyboardMode` preference: `CUSTOM` (today's behaviour), `HYBRID` (system IME + extra-keys row, new default), `SYSTEM_ONLY` (IME only, no extra row — power-user escape hatch).
- **IME anchor**: an invisible `BasicTextField` with `showSoftInputOnFocus = true`, `singleLine = true`, and `visualTransformation = VisualTransformation.None`. It receives focus whenever the terminal pane is tapped. An `onValueChange` callback converts each added character into a `TerminalEvent.SendText` (or raw bytes) and clears the field back to empty. This treats the IME as a one-shot character source rather than a text editor.
- **Modifier state**: a small in-memory `ModifierState(ctrl: Boolean, alt: Boolean)` held in the ViewModel. When `ctrl` is latched, the next character coming from the IME is translated to the corresponding control byte (`c & 0x1F`). `alt` prepends `ESC` (0x1B). The modifier resets after one keystroke unless the user long-pressed to "sticky" it. This mirrors Termux behaviour.
- **Extra-keys row**: a new `TerminalExtraKeys` composable, ~52dp tall, rendered *above* the IME via `Modifier.imePadding()` + `Modifier.navigationBarsPadding()`. Shows a horizontally-scrollable row of: Esc, Tab, Ctrl, Alt, ↑, ↓, ←, →, Fn, `|`, `/`, `~`, `\``, Home, End, PgUp, PgDn. Ctrl/Alt are toggle buttons with a clear visual latched-state.
- **Window insets**: the whole terminal `Column` uses `Modifier.imePadding()` on its root so the terminal pane gets the remaining space after the IME opens. The old `splitRatio` no longer applies in `HYBRID` mode (the IME height is not ours to pick). `splitRatio` stays in state and is applied only in `CUSTOM` mode.

**Tech stack:** Compose, `BasicTextField`, `FocusRequester`, `WindowInsets.ime`, existing ConnectBot termlib input path, DataStore Preferences (`AppPreferences`) for `KeyboardMode`. No new third-party deps.

**Depends on:** Phase 4 (terminal) + Phase 11 P1.2 (`AppPreferences` DataStore) both completed.

**Out of scope:**
- Hardware-keyboard (Bluetooth) layout tuning — a separate follow-up.
- Per-session keyboard-mode overrides — global preference only.
- Custom extra-keys layouts — ships with one fixed set.
- Migrating existing users from CUSTOM to HYBRID. Default stays CUSTOM (security posture) — HYBRID is opt-in.

**Review history:**
- 2026-04-18: devil's-advocate pass surfaced 7 concerns (3 Critical/High). All merged into the plan:
  - R1 Composing-text flooding → Task 14.2 Step 3
  - R2 IME dictionary leakage → Task 14.2 Step 1 + Task 14.6 Step 2
  - R3 Resize storm on IME height change → Task 14.5 Step 5
  - R4 Ctrl+non-letter mapping → Task 14.3 Step 4
  - R5 Double modifier state → Task 14.3 Step 3
  - R6 Tab-switch focus/modifier semantics → Task 14.3 Step 2 + Task 14.5 Step 4
  - R7 Definition-of-Done IME matrix → DoD section

---

## File Structure

```
feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/
├── KeyboardHost.kt              (NEW: switches between the three modes)
├── TerminalExtraKeys.kt         (NEW: sticky toolbar over the IME)
├── TerminalImeAnchor.kt         (NEW: invisible BasicTextField + focus)
├── TerminalScreen.kt            (modify: replace CustomKeyboard slot with KeyboardHost)
├── TerminalUiState.kt           (modify: add keyboardMode, modifierState)
├── TerminalViewModel.kt         (modify: latch logic, SendText translator)
├── CustomKeyboard.kt            (unchanged — still used by CUSTOM mode)

domain/src/main/kotlin/dev/ori/domain/model/
├── KeyboardMode.kt              (NEW: sealed class CUSTOM/HYBRID/SYSTEM_ONLY)

core/core-common/src/main/kotlin/dev/ori/core/common/prefs/
├── AppPreferences.kt            (modify: add keyboardModeFlow + setter)

feature-settings/src/main/kotlin/dev/ori/feature/settings/sections/
├── TerminalSection.kt           (modify: add Keyboard Mode selector)

feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/
├── TerminalImeAnchorTest.kt     (NEW)
├── ModifierStateTest.kt         (NEW)
├── TerminalExtraKeysTest.kt     (NEW, Robolectric for the toolbar)
```

---

## Tasks

### Task 14.1 — Domain + Preference plumbing

**Files:**
- Create: `domain/src/main/kotlin/dev/ori/domain/model/KeyboardMode.kt`
- Modify: `core/core-common/src/main/kotlin/dev/ori/core/common/prefs/AppPreferences.kt`
- Test: `core/core-common/src/test/kotlin/dev/ori/core/common/prefs/AppPreferencesTest.kt`

- [ ] **Step 1** — Add `KeyboardMode` enum (CUSTOM, HYBRID, SYSTEM_ONLY) in domain.
- [ ] **Step 2** — Extend `AppPreferences` with `keyboardModeFlow: Flow<KeyboardMode>` and `setKeyboardMode(mode)`. **Default: CUSTOM** for users who already have the app installed (honours existing muscle memory and avoids a surprise IME-dictionary-learning event on first launch post-upgrade). New installs after Phase 14 ships can be migrated to HYBRID in a later release via a one-line default change.
- [ ] **Step 3** — Add DAO-style test to confirm round-trip + default.

### Task 14.2 — IME anchor + text → bytes bridge

**Files:**
- Create: `feature-terminal/.../ui/TerminalImeAnchor.kt`
- Create: `feature-terminal/src/test/.../TerminalImeAnchorTest.kt`

**Design constraints surfaced by the devil's-advocate review:**

1. **Composing-text awareness (non-negotiable).** Swipe-typing on Gboard/SwiftKey fires `onValueChange` many times per second while the underlined composition string evolves (`"h"` → `"he"` → `"hel"` …). A naive diff would flood the shell with every composing frame. The anchor MUST track `TextFieldValue.composition` and emit bytes only for committed text.

2. **IME dictionary / autocorrect must be suppressed.** SSH passwords and hostnames typed in the terminal must not leak into Gboard's personal dictionary, Gboard Cloud Sync, or SwiftKey's learning engine. The anchor MUST declare `KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)` — `Password` is the Android signal that disables dictionary learning across all major IMEs. This mirrors the project's existing security posture (Android Keystore, CharArray passwords, clipboard-sensitive flag).

- [ ] **Step 1** — Build `TerminalImeAnchor` as an invisible 1×1 `BasicTextField` wrapped in a `Box(Modifier.size(1.dp).alpha(0f))`. Consumes focus only; visually absent. Declare `KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.None)` to suppress dictionary learning. Set `semantics { invisibleToUser() }` so TalkBack ignores it.
- [ ] **Step 2** — Use `rememberFocusRequester()` and expose a `requestFocus()` lambda so taps on the terminal pane can grab the IME.
- [ ] **Step 3** — Use `TextFieldValue` (not raw `String`) as the state type. In `onValueChange`, **ignore updates while `composition != null`** — that is composing preview, not committed text. When the composition clears (user lifts finger or hits space), compute the newly committed substring and emit it as UTF-8 bytes via `onInput: (ByteArray) -> Unit`. Reset the field back to `TextFieldValue("")` after emit. Document the invariant in a KDoc on the composable.
- [ ] **Step 4** — `onKeyEvent` shortcut: Enter → `0x0D`, Backspace → `0x7F`. Space arrives through the normal commit path.
- [ ] **Step 5** — Unit tests:
    - Single-char commit → single byte out
    - Composing updates (`"h"` composition, `"he"` composition, `"he "` commit) → exactly one emit of `"he "` bytes
    - Swipe-typing simulation: 8 composing frames collapsing to one `"hello"` commit → exactly 5 bytes
    - UTF-8 round-trip: Umlaut `ö` commits as 2 bytes (`0xC3 0xB6`)
    - Enter/Backspace direct-key mapping
    - `KeyboardOptions` configured (snapshot assertion on the composable's options)

### Task 14.3 — Modifier-state machine (single source of truth)

**Files:**
- Modify: `feature-terminal/.../ui/TerminalUiState.kt`
- Modify: `feature-terminal/.../ui/TerminalViewModel.kt`
- Modify: `feature-terminal/.../ui/CustomKeyboard.kt` (refactor local `ctrlActive` away)
- Create: `feature-terminal/src/test/.../ModifierStateTest.kt`

**Design constraints surfaced by the devil's-advocate review:**

1. **One modifier state for the whole terminal, not two.** `CustomKeyboard.kt:81` today holds its own `ctrlActive by remember { mutableStateOf(false) }`. The new `ModifierState` in `TerminalUiState` MUST replace that local state, not live alongside it. A latched Ctrl should survive a mode switch between CUSTOM/HYBRID and must be the same flag both UIs read and write. Two parallel sources of truth will drift and double the test surface.
2. **Ctrl-mapping covers more than A-Z.** Power-user terminal work depends on `Ctrl+[` (ESC), `Ctrl+]` (telnet-escape / tmux-prefix-alt), `Ctrl+\` (SIGQUIT), `Ctrl+^` / `Ctrl+_` (readline incremental-search / undo), `Ctrl+Space` (NUL, tmux-prefix), `Ctrl+?` (DEL, some bash setups). The translator MUST handle these explicitly — not silently drop them.
3. **Tab switch resets modifiers.** A latched Ctrl on tab A must not bleed into tab B when the user switches. Emit a modifier reset on `SwitchTab`.

- [ ] **Step 1** — Add `data class ModifierState(val ctrl: Boolean = false, val alt: Boolean = false, val sticky: Boolean = false)` to `TerminalUiState`. Treat as the single source of truth consumed by both `CustomKeyboard` and `TerminalExtraKeys`.
- [ ] **Step 2** — Add events: `ToggleCtrl`, `ToggleAlt`, `ToggleStickyModifier`, and extend `SwitchTab` handler to clear modifiers.
- [ ] **Step 3** — Refactor `CustomKeyboard.kt`: remove local `ctrlActive`, accept `modifierState: ModifierState` and `onEvent: (TerminalEvent) -> Unit` as parameters. No behaviour change for the user; just moves the state up.
- [ ] **Step 4** — In the ViewModel's `SendText` handler, translate according to this table (apply Alt prefix **after** Ctrl mapping):

    | Input char (Ctrl latched) | Emitted byte |
    |---|---|
    | `a`–`z` or `A`–`Z` | `c.code and 0x1F` (e.g. Ctrl+C → 0x03) |
    | `@` or space (0x20) | `0x00` (NUL) |
    | `[` | `0x1B` (ESC) |
    | `\` | `0x1C` (FS) |
    | `]` | `0x1D` (GS) |
    | `^` | `0x1E` (RS) |
    | `_` | `0x1F` (US) |
    | `?` | `0x7F` (DEL) |
    | anything else | pass through as-is and clear Ctrl (no-op latch) |

    Alt: if latched, prepend `0x1B` to the resulting bytes. After emit, clear `ctrl` and `alt` unless `sticky == true`.
- [ ] **Step 5** — Unit tests: full Ctrl-table round-trip (one test per row), Alt+x → `ESC x`, Ctrl+Alt+c → `ESC 0x03`, sticky Ctrl stays across two chars then clears on untoggle, `SwitchTab` clears modifiers, Ctrl+random-emoji does not crash (falls through pass-through branch).

### Task 14.4 — Extra-Keys row composable

**Files:**
- Create: `feature-terminal/.../ui/TerminalExtraKeys.kt`
- Create: `feature-terminal/src/test/.../TerminalExtraKeysTest.kt`

- [ ] **Step 1** — Compose layout: `Row(Modifier.horizontalScroll().height(52.dp).fillMaxWidth())` with equal-weight key cells. Each key dispatches via `onEvent: (ExtraKey) -> Unit`.
- [ ] **Step 2** — Key set: Esc, Tab, Ctrl*, Alt*, ↑, ↓, ←, →, Fn, `|`, `/`, `~`, `` ` ``, Home, End, PgUp, PgDn. Ctrl/Alt marked with a visible latched-state (MaterialTheme.colorScheme.primaryContainer background when on).
- [ ] **Step 3** — Long-press on Ctrl/Alt toggles sticky. Show a subtle dot indicator when sticky is on.
- [ ] **Step 4** — Use Lucide icons where available (ArrowUp/Down/Left/Right), text labels otherwise. Follow the no-Material-icons rule enforced by `.semgrep.yml`.
- [ ] **Step 5** — Robolectric test: tap Ctrl emits `ToggleCtrl`, tap ↑ emits `SendInput(ESC[A)`, long-press Alt flips sticky flag.

### Task 14.5 — KeyboardHost switcher + TerminalScreen wiring

**Files:**
- Create: `feature-terminal/.../ui/KeyboardHost.kt`
- Modify: `feature-terminal/.../ui/TerminalScreen.kt`
- Modify: `feature-terminal/.../ui/TerminalViewModel.kt` (resize debounce)
- Create: `feature-terminal/src/test/.../ResizeDebounceTest.kt`

**Design constraints surfaced by the devil's-advocate review:**

1. **Resize debounce is required in HYBRID/SYSTEM_ONLY.** Opening the emoji sheet, switching symbol layouts, or toggling voice input each resizes the available terminal area. Without debouncing, each transient height fires `TerminalViewModel.resizeTerminal` (`TerminalViewModel.kt:402-405`), which propagates to `shellHandle.onResize` and sends an SSH `window-change` packet. On slow links this causes TUI flicker (vim/less/htop full-redraw) and burst traffic. Debounce window-change by 200 ms of height stability before dispatching.
2. **Minimum-row floor.** If the IME leaves fewer than 5 rows for the terminal pane, skip the resize and let the terminal scroll instead — a resize to 2 rows would clobber running TUIs worse than no resize.
3. **Anchor focus persists across tab switches.** When the user switches between SSH-session tabs with the IME open, the anchor's focus must transfer to the newly-active tab without the IME dismissing. Use a single top-level `TerminalImeAnchor` in `KeyboardHost` (not one per tab) so focus state is stable.

- [ ] **Step 1** — `KeyboardHost(mode, onInput, modifierState, onEvent)` composable:
  - `CUSTOM` → current `CustomKeyboard` body (now consuming `modifierState` from props instead of local state — see Task 14.3 Step 3).
  - `HYBRID` → `Column(TerminalExtraKeys + TerminalImeAnchor)` with `Modifier.imePadding()` so the IME pushes the row up.
  - `SYSTEM_ONLY` → just `TerminalImeAnchor`, no extra-keys row.
- [ ] **Step 2** — Replace the existing `CustomKeyboard(...)` call in `TerminalScreen.kt:176-181` and `:196-199` with `KeyboardHost(...)`.
- [ ] **Step 3** — In `HYBRID` and `SYSTEM_ONLY`, skip the weighted layout for the keyboard half: terminal pane uses `weight(1f)` + `imePadding()` modifier, and the IME provides its own height. The drag-divider is hidden in these modes (it only makes sense when both halves are ours). `splitRatio` stays persisted in state but is only applied in `CUSTOM`.
- [ ] **Step 4** — Tap on the terminal pane calls the anchor's `requestFocus()` to pop the IME. A secondary tap on the toolbar's keyboard-icon button can hide the IME (`LocalSoftwareKeyboardController.current?.hide()`). The anchor is rendered once in `KeyboardHost`, not per tab — this preserves focus across `SwitchTab` events.
- [ ] **Step 5** — In `TerminalViewModel`, wrap the `ResizeTerminal` event dispatch in a `debounce(200.milliseconds)` flow operator: collect height/col changes into a `MutableSharedFlow`, debounce, then call `resizeTerminal(cols, rows)`. Drop the event entirely if `rows < 5`.
- [ ] **Step 6** — Tests:
    - Resize debounce test: emit 5 height changes within 100 ms → exactly 1 resize call after 200 ms.
    - Row-floor test: emit a 2-row height → zero resize calls.
    - Tab-switch-focus test (Robolectric): open IME in tab A, switch to tab B → anchor still holds focus, IME still up.

### Task 14.6 — Settings UI

**Files:**
- Modify: `feature-settings/.../sections/TerminalSection.kt`

- [ ] **Step 1** — Add a `ListPreference`-style row: "Keyboard style" with three options: Built-in keyboard (CUSTOM, recommended for passwords) / System keyboard with shortcut row (HYBRID) / System keyboard only (SYSTEM_ONLY). Store via `AppPreferences.setKeyboardMode`.
- [ ] **Step 2** — Show a confirm-dialog when the user picks HYBRID or SYSTEM_ONLY:
    *"Your system keyboard (Gboard, SwiftKey, …) may learn and sync the text you type. Avoid typing passwords with the system keyboard, or use a dedicated 'private mode' IME. Built-in keyboard never shares input."*
    The dialog has `Continue` / `Cancel`. Decline returns to CUSTOM.
- [ ] **Step 3** — Mockup diff: update `Mockups/settings.html` to show the new row + dialog, regenerate `.github/mockup-hash.txt`, and run `bash .github/ci/check-mockup-hash.sh` locally before commit.

### Task 14.7 — Documentation & release notes

**Files:**
- Modify: `CLAUDE.md` (keyboard section)
- Modify: `OriDev-Prompt.md` (terminal feature description)

- [ ] **Step 1** — Update the terminal section in `CLAUDE.md` to describe the three keyboard modes and that HYBRID is the new default.
- [ ] **Step 2** — Add a short "Migration" line in `OriDev-Prompt.md` explaining that existing users keep their layout (opt into CUSTOM via Settings) — or flip it: default stays CUSTOM until v1.0 to avoid surprising existing users. Decide at implementation time.

---

## Risks & rollback

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Swipe-typing floods the shell.** Gboard/SwiftKey fire `onValueChange` per composing frame. Naive diff would send `"h"`, `"he"`, `"hel"` … for a single swiped `"hello"`. | **Critical** | Task 14.2 Step 3 enforces `TextFieldValue.composition`-aware gating — emits only committed text. Test explicitly asserts 1 byte-batch per swipe. |
| R2 | **Passwords / hostnames leak into IME dictionary.** Gboard Cloud Sync + SwiftKey personal-dictionary learn every keystroke by default, cross-device-syncable. A terminal that feeds the system IME raw by default breaks the project's security posture. | **Critical** | Task 14.2 Step 1 sets `KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)` on the anchor. Settings UI in Task 14.6 shows an explicit warning when the user switches to HYBRID/SYSTEM_ONLY. |
| R3 | **IME-height flicker causes SSH resize storm.** Emoji sheet, symbol-layout switch, voice-input overlay all resize the terminal pane. Each resize sends an SSH `window-change` packet; on slow links TUI apps flicker. | **High** | Task 14.5 Step 5 adds 200 ms debounce + 5-row floor on `resizeTerminal`. Tested with emoji-open/close yielding ≤1 resize. |
| R4 | **Ctrl+non-letter characters (`[`, `]`, `\`, `^`, `_`, space, `?`) silently dropped.** Power-user readline/tmux bindings break. | High | Task 14.3 Step 4 ships a complete control-code table + one test per row. |
| R5 | **Parallel Ctrl state in CustomKeyboard vs ViewModel drifts on mode switch.** Latched Ctrl disappears when switching CUSTOM ↔ HYBRID; doubles test surface. | High | Task 14.3 Step 3 refactors `CustomKeyboard.kt:81` to consume `modifierState` from ViewModel instead of holding local state. |
| R6 | **Tab switch drops IME focus or bleeds modifiers.** User in tab A, latches Ctrl, switches to tab B, types `echo foo` → sends `\x03 echo foo`. | Medium | Task 14.3 Step 2 clears modifiers on `SwitchTab`; Task 14.5 Step 4 keeps a single top-level anchor so focus persists. |
| R7 | **ConnectBot termlib expects raw bytes; UTF-8 round-trip for non-ASCII.** | Low | Already handles bytes; Task 14.2 Step 5 adds UTF-8 Umlaut test (`ö` → 0xC3 0xB6). |
| R8 | **Screen-reader announces both anchor and terminal view.** | Low | Task 14.2 Step 1 sets `semantics { invisibleToUser() }` on anchor. |
| R9 | **Regressing existing users relying on CustomKeyboard.** | Low | Default is CUSTOM (Task 14.1 Step 2). HYBRID is opt-in via Settings. |

**Rollback path:** if HYBRID or SYSTEM_ONLY ship buggy, nothing to revert — CUSTOM is already the default, HYBRID is opt-in. The new code can stay in place; the Settings entry can be hidden behind a feature flag if needed.

---

## Definition of Done

**Functional — HYBRID mode on a real device (not emulator), verified against each IME in the matrix:**

- [ ] IME matrix: **Gboard + SwiftKey + Samsung Keyboard + Hacker's Keyboard** — typing `echo hello` arrives as exactly the 11 bytes at the shell (no duplication, no composing-leak) on **all four**.
- [ ] Swipe-typing `hello` on Gboard → exactly 5 bytes at the shell (Concern R1).
- [ ] Umlaut `ö` typed → 2 bytes at the shell (0xC3 0xB6, UTF-8 round-trip).
- [ ] `Ctrl+C` in `vim` interrupts; `Ctrl+D` closes a Python REPL; `Ctrl+[` acts as ESC; arrow keys navigate bash history.
- [ ] Emoji sheet open → close → open within 1 s produces ≤1 SSH `window-change` packet (verified via `ssh -v` or Wireshark).
- [ ] Terminal remaining height below 5 rows: no resize dispatched, terminal scrolls.
- [ ] Tab-switch with IME open: focus preserved, modifiers cleared, typing lands in new tab.

**Security (IME-Dictionary-Leakage — Concern R2):**

- [ ] `KeyboardOptions.keyboardType == KeyboardType.Password` verified on the `TerminalImeAnchor` via test snapshot.
- [ ] Manual check on Gboard (Settings → Dictionary → Personal): after typing `verysecretpassword` in the terminal, the string does NOT appear in the personal-dictionary suggestions list. Repeat on SwiftKey.
- [ ] Settings dialog warning shown on switch to HYBRID / SYSTEM_ONLY.

**Regression:**

- [ ] CustomKeyboard mode pixel-identical to pre-Phase-14 (Mockup Layout Gate still matches the Phase 11 hash).
- [ ] Existing Terminal unit/instrumentation tests still green.

**Build gates:**

- [ ] `./gradlew :feature-terminal:test :domain:test :core:core-common:test` green.
- [ ] `./gradlew :feature-terminal:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.ori.feature.terminal.ui.TerminalImeAnchorInstrumentationTest` green.
- [ ] `./gradlew detekt lint` green.
- [ ] Semgrep gate green (no Material icons imports, no clipboard-bypass).
- [ ] Release notes entry drafted for the next minor bump.
