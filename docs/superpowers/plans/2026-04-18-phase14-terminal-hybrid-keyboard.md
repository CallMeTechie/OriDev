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
- [ ] **Step 2** — Extend `AppPreferences` with `keyboardModeFlow: Flow<KeyboardMode>` and `setKeyboardMode(mode)`. Default HYBRID.
- [ ] **Step 3** — Add DAO-style test to confirm round-trip + default.

### Task 14.2 — IME anchor + text → bytes bridge

**Files:**
- Create: `feature-terminal/.../ui/TerminalImeAnchor.kt`
- Create: `feature-terminal/src/test/.../TerminalImeAnchorTest.kt`

- [ ] **Step 1** — Build `TerminalImeAnchor` as an invisible 1×1 `BasicTextField` wrapped in a `Box(Modifier.size(1.dp).alpha(0f))`. Consumes focus only; visually absent.
- [ ] **Step 2** — Use `rememberFocusRequester()` and expose a `requestFocus()` lambda so taps on the terminal pane can grab the IME.
- [ ] **Step 3** — On every non-empty `onValueChange`, emit the newly added characters to `onInput: (ByteArray) -> Unit`, then clear the field back to empty. Handle multi-char IME commits (e.g. autocorrect replacements) by diffing before/after.
- [ ] **Step 4** — `onKeyEvent` shortcut: Enter → `0x0D`, Backspace → `0x7F`, Space already handled by `onValueChange` diff.
- [ ] **Step 5** — Unit tests: single-char append, multi-char autocorrect replacement, Enter/Backspace mapping, focus request idempotency.

### Task 14.3 — Modifier-state machine

**Files:**
- Modify: `feature-terminal/.../ui/TerminalUiState.kt`
- Modify: `feature-terminal/.../ui/TerminalViewModel.kt`
- Create: `feature-terminal/src/test/.../ModifierStateTest.kt`

- [ ] **Step 1** — Add `data class ModifierState(val ctrl: Boolean = false, val alt: Boolean = false, val sticky: Boolean = false)` to `TerminalUiState`.
- [ ] **Step 2** — Add events: `ToggleCtrl`, `ToggleAlt`, `ToggleStickyModifier`.
- [ ] **Step 3** — In the ViewModel's `SendText` handler, consume modifier state: if Ctrl latched and char is `a..z` or `A..Z` → emit `(c.code and 0x1F).toByte()`. If Alt → prepend `0x1B` to output. After emit, clear modifiers unless `sticky`.
- [ ] **Step 4** — Unit tests: Ctrl+C → 0x03, Ctrl+D → 0x04, Alt+x → ESC x, sticky Ctrl stays across two chars, toggling off an active latch.

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

- [ ] **Step 1** — `KeyboardHost(mode, onInput, modifierState, onEvent)` composable:
  - `CUSTOM` → current `CustomKeyboard` body (unchanged).
  - `HYBRID` → `Column(TerminalExtraKeys + TerminalImeAnchor)` with `Modifier.imePadding()` so the IME pushes the row up.
  - `SYSTEM_ONLY` → just `TerminalImeAnchor`, no extra-keys row.
- [ ] **Step 2** — Replace the existing `CustomKeyboard(...)` call in `TerminalScreen.kt:176-181` and `:196-199` with `KeyboardHost(...)`.
- [ ] **Step 3** — In `HYBRID` and `SYSTEM_ONLY`, skip the weighted layout for the keyboard half: terminal pane uses `weight(1f)` + `imePadding()` modifier, and the IME provides its own height. The drag-divider is hidden in these modes (it only makes sense when both halves are ours).
- [ ] **Step 4** — Tap on the terminal pane calls the anchor's `requestFocus()` to pop the IME. A secondary tap on the toolbar's keyboard-icon button can hide the IME (`LocalSoftwareKeyboardController.current?.hide()`).

### Task 14.6 — Settings UI

**Files:**
- Modify: `feature-settings/.../sections/TerminalSection.kt`

- [ ] **Step 1** — Add a `ListPreference`-style row: "Keyboard style" with three options: System keyboard with shortcut row (HYBRID) / System keyboard only (SYSTEM_ONLY) / Built-in keyboard (CUSTOM). Store via `AppPreferences.setKeyboardMode`.
- [ ] **Step 2** — Mockup diff: update `Mockups/settings.html` if the visible section changes, regenerate `.github/mockup-hash.txt`, and run `bash .github/ci/check-mockup-hash.sh` locally before commit.

### Task 14.7 — Documentation & release notes

**Files:**
- Modify: `CLAUDE.md` (keyboard section)
- Modify: `OriDev-Prompt.md` (terminal feature description)

- [ ] **Step 1** — Update the terminal section in `CLAUDE.md` to describe the three keyboard modes and that HYBRID is the new default.
- [ ] **Step 2** — Add a short "Migration" line in `OriDev-Prompt.md` explaining that existing users keep their layout (opt into CUSTOM via Settings) — or flip it: default stays CUSTOM until v1.0 to avoid surprising existing users. Decide at implementation time.

---

## Risks & rollback

| Risk | Likelihood | Mitigation |
|---|---|---|
| Some IMEs send composing text (underlined preview) that the diff-based anchor can't attribute cleanly | Medium | Use `TextFieldValue` + `composition` tracking; only emit bytes on non-composing commits. Fall back to `onImeAction` for Enter. |
| ConnectBot termlib expects raw bytes on its `SendInput` path; multi-byte UTF-8 from IME needs to round-trip | Low | Already handles bytes; just ensure we encode as UTF-8 before emit. |
| Screen-reader announces both the invisible anchor and the terminal view | Medium | Mark the anchor `contentDescription = null` and `semantics { invisibleToUser() }`. Terminal view stays focusable for TalkBack. |
| Ctrl+C to interrupt must reach the remote process, not be intercepted by Compose | Low | Emit directly as 0x03 byte to the shell session; do not pass through system key handling. |
| Regressing existing users who rely on `CustomKeyboard` | Medium | Keep CUSTOM mode and the `CustomKeyboard.kt` file untouched; HYBRID is just a new option. Default choice is a product call — see Task 14.7 Step 2. |

**Rollback path:** if HYBRID ships buggy, set the default `KeyboardMode` back to `CUSTOM` via a one-line change in `AppPreferences` without removing the new code. No data migration needed.

---

## Definition of Done

- [ ] Three keyboard modes selectable in Settings and persisted across app restart.
- [ ] In HYBRID: typing via Gboard hits the remote shell (verified with a real `ssh` session, `echo hello` → server receives `hello`).
- [ ] In HYBRID: Ctrl+C in vim interrupts the running process; Ctrl+D closes a Python REPL; arrow keys navigate through bash history.
- [ ] In HYBRID: IME slides up over the terminal pane, extra-keys row stays sticky directly above the IME keys.
- [ ] CustomKeyboard mode still behaves identically to pre-phase-14 (regression check: existing screenshot from Phase 11 mockup gate still matches).
- [ ] `./gradlew :feature-terminal:test :domain:test :core:core-common:test` green.
- [ ] `./gradlew detekt lint` green.
- [ ] Semgrep gate green (no Material icons imports, no clipboard-bypass).
- [ ] Release notes entry drafted for the next minor bump.
