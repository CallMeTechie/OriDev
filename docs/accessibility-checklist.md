# Accessibility Checklist

Scope of the Phase 10 Task 10.5 accessibility audit across Ori:Dev's primary
screens. Every interactive element on the screens listed below was annotated
with a non-null `contentDescription`, and row-style cards were wrapped in a
`semantics(mergeDescendants = true)` block that builds a spoken description
from the row's data. Section headers were marked with `semantics { heading() }`
where present. All strings are in German to match the rest of the app.

Manual TalkBack verification on a Pixel Fold is deferred until a test device
is available — see the "How to verify with TalkBack" section below.

## Screens audited

| Feature module | Screen / component | What was added |
|---|---|---|
| `feature-connections` | `ConnectionListScreen`, `AddEditConnectionScreen` | FAB and Proxmox action content descriptions, `mergeDescendants` on `ServerProfileCard`, `Role.Button` on the FAB, favourite toggle labels, `heading()` on the Authentication label, password visibility and Advanced expand/collapse descriptions. |
| `feature-filemanager` | `FileManagerScreen`, `FileListPane`, `FileItemRow` | German descriptions on the select-all, view-mode, create-folder, refresh, and delete-selection icons; `heading()` on the Local / Remote pane header; parent-directory row description; `mergeDescendants` on each `FileItemRow` with a "name, type, size" description and a selection state hint. |
| `feature-terminal` | `TerminalScreen`, `CustomKeyboard` | German descriptions on every top-bar action (clipboard history, paste, snippets, code blocks, keyboard toggle, record, export, preferences) and on the Send-to-Claude FAB. The terminal canvas gets a semantic description reporting the active row count. Every soft-keyboard key (Esc, Tab, Ctrl, Alt, Shift, Enter, function row, arrows, backspace, space) has an explicit description and `Role.Button` or `Role.Switch`. |
| `feature-transfers` | `TransferQueueScreen`, `TransferItemCard` | Empty-state icon description; each transfer card's info area is merged into a "direction filename, percent, status" description while pause/resume/cancel/retry buttons remain individually focusable with German labels. |
| `feature-editor` | `CodeEditorScreen`, `DiffViewerScreen`, `EditorTabBar`, `SearchReplaceBar` | German descriptions on save, search, close-tab, find-next, find-previous, and the diff view mode toggle. Sora editor is wrapped with a "Code-Editor, Sprache X" semantic label. Every `DiffLineRow` has a merged description that announces whether the row is added, removed, modified, or context plus the line number and content. |
| `feature-proxmox` | `ProxmoxDashboardScreen`, `NodeCard`, `VmCard` | Add-Node FAB description; `NodeCard` merged into a description reporting name, host, online state, CPU and RAM usage with `Role.Button` and selection state; VM header row merged into a "VM id name, status X" description while start / stop / restart / delete buttons keep their individual German labels. |
| `feature-settings` | `SettingsScreen` | Added `heading()` semantic to the Datenschutz and Info section headers. Everything else was already compliant from Task 10.7. |

## Known gaps (flagged for manual TalkBack follow-up)

- **Soft keyboard keys are 44dp tall**, below the 48dp touch target target. They
  still meet Android's minimum (`48dp` applies to `IconButton`-sized targets and
  is relaxed to `24dp` on dense grids), but a manual TalkBack swipe pass should
  confirm every key can be reliably focused. The larger layout rework to reach
  48dp is deferred — it would affect the terminal / keyboard split ratio.
- **Transfer queue small action buttons are 32dp** — individually focusable via
  TalkBack but below the recommended touch target. A layout refactor to reach
  48dp is out of scope for Task 10.5.
- **Editor tab close button is 24dp** — same caveat as above. Tab bar layout
  rework deferred.
- **Compose UI test coverage was not added** (Plan Step 5). The plan defers
  this to a follow-up and Task 10.5's acceptance only requires non-null
  descriptions plus lint gating via Task 10.2.
- **No TalkBack manual pass performed** — needs a Pixel Fold (or at least a
  phone emulator with TalkBack). Instructions below.

## How to verify with TalkBack

1. On a Pixel Fold (or any Android 14+ device), enable TalkBack:
   **Settings -> Accessibility -> TalkBack -> Use TalkBack**. Learn the swipe
   gesture set via the tutorial if this is your first time.
2. Install the debug build: `./gradlew :app:installDebug` and launch Ori:Dev.
3. For each primary screen (connections list, connection edit, file manager,
   terminal, transfers, editor, proxmox, settings), swipe right through every
   focusable element and confirm:
   - TalkBack speaks a meaningful German description for every focus stop.
   - Row-style cards announce their full content (name + status + details) in
     a single focus stop, not one stop per child `Text`.
   - Section headers are announced as "Überschrift" and the rotor's "Headings"
     navigation can jump between them.
   - Interactive toggles announce "Schalter" plus their on/off state.
4. Note any element that announces as "nicht bezeichnet" ("unlabeled") and file
   an issue against the relevant feature module.
