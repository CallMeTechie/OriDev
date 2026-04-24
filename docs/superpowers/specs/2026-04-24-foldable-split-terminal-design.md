# Foldable Split Terminal — Design

**Status:** Draft (Rev 3 — devil's-advocate round-2 fixes applied)
**Date:** 2026-04-24
**Scope:** Auf dem Pixel Fold unfolded (>=600dp Breite) zeigt das
Terminal zwei Tabs gleichzeitig nebeneinander. Phone und folded bleiben
Single-Pane. Schließt Task #34 aus dem OriDev-Backlog.

---

## 1. Problem

`TerminalScreen` rendert heute nur den fokussierten Tab. Auf dem
841dp-breiten unfolded Pixel Fold wird derselbe Single-Tab einfach
breiter — kein zusätzlicher Benefit aus dem größeren Display. Der
Power-Workflow „zwei Server parallel im Blick" (prod ↔ staging,
build-logs ↔ deploy-shell, app-log ↔ db-repl) ist nicht unterstützt.

Das existierende `isWideScreen`-Gate in `OriDevApp` schaltet nur die
NavigationRail ein, nicht das Content-Layout. Diese Spec erweitert das
Gate auf den Terminal-Content.

---

## 2. Design decisions (confirmed with Marc)

1. **Scope:** Nur Terminal. Andere Tabs (Files, Connections, Settings)
   bleiben Single-Pane auf unfolded. Keine cross-feature split UI.
2. **Aktivierung:** Auto-Split bei unfolded + ≥2 Tabs. Keine
   User-Interaktion für den Default-Workflow. Single Tab oder folded →
   Single-Pane wie heute.
3. **Input-Fokus:** Ein Keyboard-Target pro Zeit. Fokussierter Pane
   bekommt Keystrokes. Tap auf inaktiven Pane → Fokus wechselt, Keyboard
   folgt.
4. **Architektur:** Pane-Assignment-State lebt in `TerminalViewModel`
   (bestehendes VM erweitert um 3 Felder + Reducer). Kein separater
   Controller.

---

## 3. Data Model

`TerminalUiState` erweitert um drei Felder:

```kotlin
data class TerminalUiState(
    // existing: tabs, activeTabIndex, modifierState, keyboardMode, ...
    val leftPaneTabId: String? = null,    // null = empty slot
    val rightPaneTabId: String? = null,   // null = empty slot
    val activePaneIndex: Int = 0,         // 0 = left, 1 = right
)
```

**Invariants:**
- `leftPaneTabId` und `rightPaneTabId` referenzieren Tab-IDs aus `tabs`
  (oder sind `null`). Ein Reducer-Pass nach jeder `tabs`-Mutation stellt
  das sicher.
- `activePaneIndex ∈ {0, 1}`. Fallback auf 0 falls der zugehörige Slot
  leer ist.
- Bei folded / `screenWidthDp < 600` ignoriert `TerminalScreen` die
  Pane-Felder. Nur `activeTabIndex` bestimmt was gerendert wird.

---

## 4. Auto-Assignment Reducer

Pure function in `:feature-terminal/ui/PaneAssignment.kt`:

```kotlin
data class PaneAssignment(
    val leftPaneTabId: String?,
    val rightPaneTabId: String?,
    val activePaneIndex: Int,
)

fun resolvePaneAssignments(
    tabs: List<TerminalTab>,
    leftPaneTabId: String?,
    rightPaneTabId: String?,
    activePaneIndex: Int,
): PaneAssignment
```

**Regeln (in order):**

1. **Orphan cleanup:** Wenn `leftPaneTabId` oder `rightPaneTabId` auf
   einen Tab zeigen, der nicht mehr in `tabs` existiert → Slot auf
   `null`. **Suspendiert während Cold-Start-Restore** (siehe §7 —
   `isRestoringPanes` Gate).
2. **Duplicate cleanup:** Wenn beide Slots auf den gleichen Tab zeigen
   → **der non-active Slot wird geleert, nie der aktive**. Tiebreaker:
   bei `activePaneIndex == 0` wird rightPaneTabId umgestellt / genullt,
   bei `activePaneIndex == 1` der leftPaneTabId. So kann ein
   duplicate-cleanup nie einen Fokus-Sprung auslösen.
3. **Left-slot fill:** Wenn `leftPaneTabId == null` und `tabs`
   nicht leer → `leftPaneTabId = tabs[activeTabIndex].id` (oder
   `tabs.first().id` falls activeTabIndex out of bounds).
4. **Right-slot fill:** Wenn `rightPaneTabId == null` und `tabs.size >= 2`
   → `rightPaneTabId = tabs.firstOrNull { it.id != leftPaneTabId }?.id`.
5. **ActivePaneIndex fallback:** Wenn der aktive Slot leer ist (nach
   Regel 1-4 noch `null`) → `activePaneIndex = 0` (left wird zum Default).

**Reducer-Exit: zweistufige Safety.**

Debug-only `check()` ist nicht ausreichend — R8 strippt sie in Release,
und ein Throw im Release-Build ist nur ein Crash-Outcome statt eines
sanen Recovery-Pfads. Der Reducer tut deshalb **zwei Dinge:**

**1. Sanitization-Clamp (läuft immer, release + debug):**

```kotlin
// Force-fix any invariant violation with a safe fallback:
val sanitizedActivePane = when {
    activePaneIndex == 1 && rightPaneTabId == null -> 0     // fallback to left
    activePaneIndex == 0 && leftPaneTabId == null && rightPaneTabId != null -> 1
    else -> activePaneIndex.coerceIn(0, 1)
}
val sanitizedLeft = leftPaneTabId ?: tabs.firstOrNull()?.id
    .takeIf { tabs.isNotEmpty() && sanitizedActivePane == 0 }
    ?: leftPaneTabId
```

So kann eine halb-wiederhergestellte Persistence oder eine Race in
Release nie zu einem „active pane points to null slot"-Zustand führen,
der dann Keystrokes auf eine null-Tab-ID routet. Worst Case: User sieht
den linken Pane fokussiert, obwohl er auf den rechten geklickt hatte —
visuell erkennbar, nicht destruktiv.

**2. Debug-only `check()` (loud signal in dev):**

```kotlin
if (BuildConfig.DEBUG) {
    check(sanitizedActivePane in 0..1)
    check(sanitizedActivePane != 1 || sanitizedRight != null) {
        "Reducer exit invariant broken: activePane=1 but rightPaneTabId=null"
    }
    check(sanitizedActivePane != 0 || sanitizedLeft != null || tabs.isEmpty()) {
        "Reducer exit invariant broken: activePane=0 + tabs non-empty but leftPaneTabId=null"
    }
}
```

In Debug-Builds knallt der Check sofort, wenn Regel 1-5 nicht ausreichen
und die Sanitization angreifen musste — das macht die Regel-Logik
testbar, während die Sanitization Release-Builds schützt.

### VM methods

```kotlin
fun setActivePane(index: Int)
fun moveTabToPane(tabId: String, pane: Int)  // from long-press menu
```

`setActivePane` schreibt + triggert `scheduleTabMemoSnapshot()` (Task
#33).

`moveTabToPane` ist **transactional** — in einem einzigen
`_uiState.update { … }`-Block wird die Final-Assignment berechnet, der
Reducer wird **nach** dem update einmal gelaufen (nicht zwischen den
Slot-Writes). Wenn der Ziel-Slot bereits belegt ist, wandert der
bisherige Tab atomisch in den anderen Slot. So kann die Reducer-Rule-2
„duplicate cleanup" nie mitten im Move-Operation zuschlagen und den
Fokus in einen genullten Slot leiten.

### Tab-Lifecycle-Hooks

- **`openNewTab`:**
  - Wenn active-Pane-Slot leer → neuer Tab füllt diesen Slot + wird
    fokussiert.
  - Wenn active-Pane belegt + anderer Slot leer + Split-Mode aktiv →
    neuer Tab füllt den leeren Slot + `activePaneIndex` wechselt dorthin.
  - Wenn beide belegt → neuer Tab ersetzt den Tab im active-Pane-Slot
    (alter Tab bleibt in `tabs`, via Tab-Bar weiter erreichbar).

- **`closeTab`:**
  - Reducer räumt auf, wählt nächsten verfügbaren Tab für den freien
    Slot (nicht-slot-belegten Tab bevorzugt).

---

## 5. UI-Architektur

**Detection:** `LocalConfiguration.current.screenWidthDp >= 600`
triggert den Split-Mode. Wert wird als `isSplitAvailable: Boolean`
lokal im `TerminalScreen` berechnet. VM kennt keine Display-Metrics.

**Rendering:**

```kotlin
val isSplitActive = isSplitAvailable && state.tabs.size >= 2

Column(modifier = Modifier.fillMaxSize()) {
    TerminalTabBar(state = state, onAction = viewModel::onTabBarAction)

    if (isSplitActive) {
        Row(modifier = Modifier.weight(1f)) {
            PaneContent(
                tab = state.tabs.firstOrNull { it.id == state.leftPaneTabId },
                isFocused = state.activePaneIndex == 0,
                onTap = { viewModel.setActivePane(0) },
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(modifier = Modifier.width(1.dp))
            PaneContent(
                tab = state.tabs.firstOrNull { it.id == state.rightPaneTabId },
                isFocused = state.activePaneIndex == 1,
                onTap = { viewModel.setActivePane(1) },
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        PaneContent(
            tab = state.tabs.getOrNull(state.activeTabIndex),
            isFocused = true,
            onTap = {},
            modifier = Modifier.weight(1f),
        )
    }

    KeyboardHost(
        activeTabId = computeActiveTabId(state, isSplitActive),
        keyboardMode = state.keyboardMode,
    )
}
```

**`PaneContent`** (neu, extracted aus heutigem TerminalScreen-Body):
- Enthält `TermSessionView` (ConnectBot) + Scroll-State + Shell-Handle-Binding
- Stateless bis auf `isFocused`-Flag, der den Border steuert
- Empty-State wenn `tab == null`: Lucide-Icon + „Tab auswählen"-Hint +
  Tap öffnet einen Tab-Picker-Sheet mit folgender Struktur:
  - **Erster Row:** „Neuen Tab hier öffnen" (öffnet `openNewTab()` +
    landet im empty slot). Das gibt User immer eine Fluchtoption.
  - **Darunter:** Liste der existierenden Tabs **minus** `leftPaneTabId`
    und `rightPaneTabId` — der Picker zeigt nie Tabs an, die bereits
    sichtbar sind. Keine Duplicate-Loop mit Reducer-Rule-2.
  - Sheet ist modal (BottomSheet); `tap-outside` dismissed zurück zum
    Empty-State.
  - Wenn nach Filter keine Tabs mehr übrig: nur „Neuen Tab hier öffnen"
    wird gezeigt (häufiger Fall: 2 Tabs beide in Panes, User schließt
    einen, Empty-State erscheint, Picker zeigt nur „Neu").

**Focus-Indicator:**
- Aktiver Pane: 2dp Border mit `MaterialTheme.colorScheme.primary`
- Inaktiver Pane: 1dp Border mit `outlineVariant`
- Animation: `animateColorAsState(tween(120))` um Tap-to-Switch responsive
  zu halten

**Tab-Bar** unverändert — EINE Leiste über beiden Panes. Beide
Pane-Tabs bekommen einen Underline, differenziert durch Gewicht:
- **Fokussierter Pane-Tab:** 2dp primary-Underline
- **Non-active Pane-Tab:** 1dp outlineVariant-Underline
- **Non-Pane-Tabs (Tab 3, 4, 5...):** kein Underline, neutral

Grund: bei ≥3 Tabs ist die Tab-Bar horizontal scrollbar. Ohne Marker
auf dem non-active Pane-Tab verliert User den „welche Tabs sind live
auf meinen Panes"-Überblick, sobald die Bar gescrollt wird. Der
1dp-Underline ist visuell leichter als ein Dot, bleibt aber sichtbar
genug um den "mounted on a pane"-Zustand zu kommunizieren. Zusätzlich
scrollt die Tab-Bar auf `setActivePane` zum fokussierten Tab, damit er
immer sichtbar ist.

**Long-Press-Popup (Split-Mode only):** Long-Press auf Tab-Bar-Item
zeigt:
- „In linken Pane bewegen" / „In rechten Pane bewegen"
- „Schließen"

**Config-Change Survival:** Wenn User mid-Popup faltet (Fold-Event
lässt `isSplitActive` kippen), dismissed das Popup sich automatisch via
`LaunchedEffect(isSplitActive) { popupState.dismiss() }`. Ohne den
Effect würde das Popup mit split-only Optionen sichtbar bleiben
(„In rechten Pane bewegen" macht in Single-Pane-Mode keinen Sinn) oder
die Tab-ID aus dem Popup-State verlieren. Explicit dismiss vermeidet
beide Bugs.

---

## 6. Keyboard + Input-Routing

**Ein `KeyboardHost` unter beiden Panes** (full-width). Kein dual-IME,
kein dual-extra-keys-row.

**Keystroke-Target:**

```kotlin
fun computeActiveTabId(state: TerminalUiState, isSplitActive: Boolean): String? =
    if (isSplitActive) {
        when (state.activePaneIndex) {
            1 -> state.rightPaneTabId
            else -> state.leftPaneTabId
        }
    } else {
        state.tabs.getOrNull(state.activeTabIndex)?.id
    }
```

Alle `onKey` + `writeToStdin` routen über `computeActiveTabId`.

**Focus-Transfer bei `setActivePane`:**

```kotlin
val imeVisible = WindowInsets.isImeVisible
LaunchedEffect(state.activePaneIndex) {
    if (state.keyboardMode != KeyboardMode.CUSTOM && imeVisible) {
        imeFocusRequester.requestFocus()
    }
}
```

Der `imeVisible`-Guard verhindert, dass ein Tap-to-Switch die
System-Tastatur ungefragt öffnet, wenn sie vorher dismissed war
(HYBRID-Mode häufig, wenn User Ausgabe liest statt tippt).

CUSTOM-Mode braucht den LaunchedEffect nicht — CustomKeyboard's
`onClick` ruft `viewModel.onKey(...)`, welches `computeActiveTabId`
konsultiert. Ein CUSTOM-mode Unit-Test stellt sicher, dass
`setActivePane(1)` + `onKey('x')` auf den richtigen Tab routet.

**Inaktiver Pane — Output-only:**
- SSHJ shell pipe bleibt offen, `stdout`/`stderr` rendern live
- Text-Selection funktioniert (Compose-intrinsic)
- Aktivitäts-Indikator: 6dp-Circle in der Pane-Ecke blinkt wenn Output
  seit letztem Focus-Switch kam (analog zu IRC-Client Unread-Badge)

**Per-Pane-Resize:** Beim Umschalten in Split-Mode feuert
`ResizeTerminal` für beide PTYs (841dp → ~420dp pro Pane = ~52 Spalten).

**Debounce-Schlüssel muss tabId sein**, nicht Event-Type — sonst
coalescet die zweite `ResizeTerminal(ptyB, …)`-Emission die erste
`ResizeTerminal(ptyA, …)`-Emission innerhalb des 200ms-Fensters, und
ptyA rendert mit veralteten Dimensionen bis zum nächsten Keystroke. Der
existierende Phase-14-Code wird darauf geprüft; falls das Key-Schema
heute nur Event-Type ist, wird es auf `"resize/$tabId"` erweitert.

**Staggered emission:** Beim Fold-Transition werden die beiden
`ResizeTerminal`-Events um 30ms gestaggert statt parallel
emittiert — verhindert SSHJ-Channel-Serialization-Stalls beim
gleichzeitigen `window-change`-Write auf demselben Transport.

Fold → Single-Pane reverse-resize analog, mit gleichem Debounce-Key
und Stagger.

---

## 7. Persistence + Fold/Unfold

### Erweiterung `SessionResumePreferences` (Task #33)

```kotlin
val leftPaneTabId: Flow<String?>
val rightPaneTabId: Flow<String?>
val activePaneIndex: Flow<Int>

suspend fun setLeftPaneTabId(tabId: String?)
suspend fun setRightPaneTabId(tabId: String?)
suspend fun setActivePaneIndex(index: Int)
```

`clearResumeSubset()` wipet diese drei neuen Keys mit (zusammen mit den
4 von Task #33: profileIds, tabMemos, focusedProfileId, remotePaths).

### Snapshot-Writer-Erweiterung (`TerminalViewModel`)

Der existierende `scheduleTabMemoSnapshot()` aus Task #8 (Task #33)
bekommt drei zusätzliche writes:

```kotlin
sessionResumePrefs.setLeftPaneTabId(_uiState.value.leftPaneTabId)
sessionResumePrefs.setRightPaneTabId(_uiState.value.rightPaneTabId)
sessionResumePrefs.setActivePaneIndex(_uiState.value.activePaneIndex)
```

Gleicher 1s-Debounce, gleiche Trigger-Pfade (jede Tab-Mutation +
`setActivePane`).

### Cold-Start Restore

Der `combine(openSessions, tabMemos)`-Observer aus Task #8 wird erweitert.

**Kritisch: Restore-Gate gegen Reducer-Orphan-Race.**

Der Reducer aus §4 würde jeden gerade geladenen `leftPaneTabId` sofort
auf `null` setzen, solange der zugehörige Tab noch nicht aus
`openSessions` gelandet ist — Rule 1 (orphan-cleanup) matched ihn. Bei
asynchroner Session-Reconnect-Reihenfolge heißt das: die persistierte
Pane-Assignment wird still ausradiert, bevor die Tabs tatsächlich
wiederhergestellt sind. User sieht die falsche Layout.

**Lösung:** `TerminalViewModel` hält ein `private val isRestoringPanes = AtomicBoolean(false)`-Latch. Restore-Sequenz:

```kotlin
init {
    viewModelScope.launch {
        try {
            // Phase 1: Pane IDs vorab laden — vor Session-Reconnect
            val persistedLeft = sessionResumePrefs.leftPaneTabId.first()
            val persistedRight = sessionResumePrefs.rightPaneTabId.first()
            val persistedActive = sessionResumePrefs.activePaneIndex.first()

            _uiState.update {
                it.copy(
                    leftPaneTabId = persistedLeft,
                    rightPaneTabId = persistedRight,
                    activePaneIndex = persistedActive,
                )
            }
            isRestoringPanes.set(true)

            // Phase 2: existing Task #8 restore observer läuft.
            // Während diese Phase läuft, unterdrückt der Reducer Rule 1
            // (orphan-cleanup) — "Tab existiert noch nicht in tabs" wird
            // als "pending" behandelt, nicht als "orphan".

            // Phase 3: Warte auf terminal-Signal (allSettled — auch bei
            // Fail), bounded auf RESTORE_TIMEOUT_MS. Timeout-Fallback
            // verhindert, dass ein hängendes Restore den Latch ewig
            // offen lässt.
            withTimeoutOrNull(RESTORE_TIMEOUT_MS) {
                awaitRestoreComplete()  // emits on allSettled, not allSuccess
            }
        } finally {
            // Unconditionally — auch bei Exception, Cancellation,
            // Timeout, Total-Failure (alle Reconnects fallen durch).
            isRestoringPanes.set(false)
            runReducer()  // full rules, jetzt auch orphan-cleanup
        }
    }
}

private companion object {
    const val RESTORE_TIMEOUT_MS = 10_000L
}
```

**Semantik von `awaitRestoreComplete`:** feuert **nach dem letzten**
Connect-Attempt aus Task #11's `ResumeCoordinator.runResume()` —
egal ob success oder failure. Implementiert als `ResumeCoordinator`
expose `val restoreState: StateFlow<RestoreState>` mit
`Idle | InProgress | Settled`; der VM collected bis `Settled` und
returned. Wenn Coordinator gar nicht läuft (auto-resume off), ist
RestoreState ewig `Idle` — der Timeout-Fallback räumt auf.

Bei Total-Failure (alle Reconnects gefailt, 0 Tabs gelandet) räumt
der Reducer-Exit-Lauf die Pane-IDs via Orphan-Cleanup auf, Slots werden
null, Empty-State wird gerendert. User sieht keine Corruption, sondern
den Standard-Empty-Path — genau der Workflow aus Task #33's
FailedResumeBanner.

Der Reducer empfängt den Latch-State als Parameter:

```kotlin
fun resolvePaneAssignments(
    tabs: List<TerminalTab>,
    leftPaneTabId: String?,
    rightPaneTabId: String?,
    activePaneIndex: Int,
    isRestoringPanes: Boolean,  // if true, Rule 1 is skipped
): PaneAssignment
```

Tests müssen abdecken: (a) Restore mit Tab-Delay — Pane-ID bleibt
erhalten bis Tab landet. (b) Restore-Completion löst vollen Reducer-Lauf
aus. (c) Nach Latch-Open werden echte Orphans (z.B. aus dem Plan
entfernte Tabs) aufgeräumt.

Per-Profile-Latch aus Task #8 bleibt orthogonal der Gate (verhindert
Doppel-Restore pro Profile).

### Fold/Unfold-Events

- **folded → unfolded:** `screenWidthDp >= 600` wird true. Reducer
  läuft → füllt eventuell noch-leere Slots. `TerminalScreen` rendert
  Split.
- **unfolded → folded:** `screenWidthDp < 600` → Single-Pane wird
  gerendert. **Sequenz kritisch:** zuerst Reducer laufen (garantiert
  per Reducer-Exit-Invariante aus §4, dass der aktive Pane non-null
  ist), **dann** `activeTabIndex = tabs.indexOfFirst { it.id == computeActiveTabId(...) }`.
  So kann der User den Tab, in den er gerade tippte, nie durch einen
  Fold verlieren.
- **Mid-typing fold:** `viewModelScope` überlebt Recomposition, VM ist
  activity-scoped. In-flight stdin-writes gehen nicht verloren. Das oben
  beschriebene Sequencing stellt sicher, dass der nächste Keystroke nach
  dem Fold in denselben Shell geht.
- **Persistence half-updated state:** Wenn Prozess zwischen
  `setLeftPaneTabId` und `setRightPaneTabId` Writes stirbt, liest der
  nächste Cold-Start einen inkonsistenten Zustand (z.B. beide Slots
  zeigen auf denselben Tab). Die Reducer-Exit-Invariante + Regel 2
  (duplicate cleanup) räumen das beim ersten Full-Reducer-Lauf nach
  Restore-Completion auf. Niemals Crash, im worst case „leerer
  rechter Pane" bis User einen Tab auswählt.

---

## 8. Architecture (files)

### New

| Path | Responsibility |
|------|----------------|
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneAssignment.kt` | Pure `resolvePaneAssignments` reducer + `PaneAssignment` data class. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/PaneContent.kt` | Extracted pane composable with focus border + empty-state. |
| `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/PaneAssignmentTest.kt` | Unit tests for reducer. |

### Modified

| Path | Change |
|------|--------|
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalUiState.kt` | Add `leftPaneTabId`, `rightPaneTabId`, `activePaneIndex`. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalViewModel.kt` | Add `setActivePane`, `moveTabToPane`; hook reducer into tab-lifecycle methods; extend snapshot-writer; extend cold-start restore observer. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalScreen.kt` | Split-or-single rendering per `screenWidthDp`; wire `TabBar` + `PaneContent` + `KeyboardHost`. |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/TerminalTabBar.kt` | Long-press popup with "In Pane X bewegen" options (split-mode only). |
| `feature-terminal/src/main/kotlin/dev/ori/feature/terminal/ui/KeyboardHost.kt` | `LaunchedEffect(activePaneIndex)` re-requests IME focus; `onKey` routes via `computeActiveTabId`. |
| `domain/src/main/kotlin/dev/ori/domain/preferences/SessionResumePreferences.kt` | Add 3 flow/setter pairs. |
| `data/src/main/kotlin/dev/ori/data/session/SessionPersistencePreferences.kt` | Implement 3 new keys; extend `clearResumeSubset()`. |
| `data/src/test/kotlin/dev/ori/data/session/SessionPersistencePreferencesTest.kt` | Round-trip tests for 3 new keys + clearResumeSubset coverage. |
| `feature-terminal/src/test/kotlin/dev/ori/feature/terminal/ui/TerminalViewModelTest.kt` | Tests for setActivePane, moveTabToPane, tab-lifecycle-hooks with pane assignment. |

### Tests

- **Reducer:** 10+ cases — orphan cleanup, duplicate cleanup, left-fill,
  right-fill, activePaneIndex-fallback, tab-open empty slot, tab-open
  both-full, close active-pane tab, close non-active-pane tab.
- **VM:** `setActivePane` persists + snapshot writes; `moveTabToPane`
  swap; tab-open routes to empty slot; close-tab slot cleanup.
- **Persistence:** round-trip + clearResumeSubset + corrupt-blob for
  each new key.
- **UI (Compose):** Split rendering at 841dp, single at 400dp; tap on
  inactive pane triggers `setActivePane`; border indicator color
  correct; long-press popup shows correct options.

---

## 9. Risks

- **ResizeTerminal-Storm bei Fold-Transition.** Beide PTYs resizen
  simultan. Pro-PTY-Debounce bleibt, Fold-Event sendet beide in 50ms →
  einmalige window-change SSH-packets. Akzeptabel.
- **Focus-Indicator-Flicker.** 2dp-Border color animation muss < 150ms
  sein. `animateColorAsState(tween(120))` steuert.
- **Empty-Slot verbraucht Screen-Real-Estate.** User geht von 2 → 1 Tab,
  rechter Pane wird empty-state. Accepted — informativ, nicht ärgerlich.
- **Drag-Gesture-Kollision.** Drag im TerminalView würde mit
  Text-Selection kollidieren. Deshalb Long-Press-Popup statt Drag-to-Pane.
- **TalkBack.** Jeder Pane-Container bekommt
  `contentDescription = "Terminal links"` / `"Terminal rechts"`. Zusätzlich
  lenkt `Modifier.semantics { traversalIndex = if (this == activePane) 0f else 1f }`
  die TalkBack-Reading-Order — der aktive Pane wird zuerst gelesen,
  egal ob links oder rechts. Output aus dem inaktiven Pane wird als
  `LiveRegionMode.Polite` ausgezeichnet (nicht Assertive), damit
  Screen-Reader den Fokus nicht unterbrechen wenn der User im aktiven
  Pane gerade tippt.
- **DataStore atomicity.** `leftPaneTabId` + `rightPaneTabId` sind
  separate Keys, nicht atomar. Wenn Prozess zwischen den Writes stirbt:
  Orphan-Cleanup aus Reducer räumt auf. Graceful degradation.

---

## 10. PR plan

Single PR auf `feat/foldable-split-terminal` off master. Default-on
(kein Feature-Toggle) — natural degradation durch `screenWidthDp`.

**Task-Dekomposition (writing-plans ist nächste Phase):**

1. `PaneAssignment` reducer + Tests
2. `TerminalUiState` erweitert
3. `TerminalViewModel` methods + tab-lifecycle-hooks + Tests
4. `SessionResumePreferences` erweitert + Impl + Tests
5. `PaneContent` extraction + empty-state
6. `TerminalScreen` split-or-single rendering
7. Focus-Indicator + `setActivePane` wiring
8. Long-press-popup + `moveTabToPane`
9. `KeyboardHost` re-focus on activePaneIndex + `computeActiveTabId`
   routing
10. Full build + detekt + semgrep + manual QA on Pixel Fold
11. Branch + PR + auto-merge + patch-Release trigger

Nach Merge:
- Task #34 → `completed`
- Backlog für Terminal-Feature leer (offene Tasks fokussieren andere
  Features)
