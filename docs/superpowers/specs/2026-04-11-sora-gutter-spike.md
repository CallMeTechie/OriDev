# Sora-Editor 0.23.6 Gutter Decoration API Spike

Date: 2026-04-11
Status: Decided - FALL BACK to status bar
Scope: Feasibility research for Task 6b.6 (Git Diff Gutter)

## Goal

Determine whether sora-editor 0.23.6 exposes a public API for drawing
per-line indicators in the line-number gutter so we can visualize git diff
markers (added / modified / deleted).

## What we found

Extracting `editor-0.23.6-runtime.jar` from the Gradle cache and inspecting
the class layout surfaces the following relevant symbols:

- `io.github.rosemoe.sora.lang.styling.line.LineGutterBackground`
  - Public data class: `LineGutterBackground(line: Int, color: ResolvableColor)`
  - Extends `LineAnchorStyle`
- `io.github.rosemoe.sora.lang.styling.line.LineBackground`
- `io.github.rosemoe.sora.lang.styling.line.LineSideIcon`
- `io.github.rosemoe.sora.lang.styling.Styles` has:
  - `public java.util.List<LineStyles> lineStyles`
  - `public void addLineStyle(LineAnchorStyle)`
  - `public void eraseLineStyle(int, Class<? extends LineAnchorStyle>)`
  - `public void eraseAllLineStyles()`
- `io.github.rosemoe.sora.widget.style.LineNumberTipTextProvider`
  - Single-method interface that returns a text tip for the current cursor
    line; it is **not** a per-line gutter API.

### Why gutter integration is NOT trivial

`Styles` is owned by `Language.analyze()`. The `TextMateLanguage` that we
use today recreates the `Styles` object on every highlight pass via the
incremental analyzer. Any `LineGutterBackground` we push onto it gets
cleared on the next incremental analyze. Subclassing `TextMateLanguage`
just to interpose `addLineStyle` calls would require forking the analyzer
pipeline, and there is no public hook on the language for "after analyze"
that survives language resets.

There is no public `CodeEditor.setGutterColorForLine(Int, Int)` or
equivalent, and no `EditorPaintEvent`-style hook that would let us draw
into the gutter from outside the language pipeline. The only per-line
anchor APIs live inside `Styles`, which is owned by the language.

A clean implementation would either:

1. Require a custom fork of the TextMate language class that merges our
   diff styles into each analyze pass (large scope, hard to test on JVM).
2. Require an upstream patch to sora-editor exposing an independent
   decoration channel (out of scope for Phase 6b).

### Decision

**Fall back to a status bar summary.** We will:

- Parse the per-line diff with `GitStatusParser.parseLineDiff()`.
- Render a `GitDiffStatusBar` at the bottom of `CodeEditorScreen` showing
  `+N added, -M removed` for the currently active tab's file (when it is
  in a git repo and the file is local).

This keeps the user-visible value (seeing at a glance that a file has
diffs relative to HEAD) without forking Sora or introducing brittle
reflection. The `parseLineDiff` infrastructure is still built so a future
phase can upgrade to a true gutter decorator once Sora adds a stable API
or once we invest in a custom TextMate language wrapper.

## References

- AAR path: `~/.gradle/caches/modules-2/files-2.1/io.github.Rosemoe.sora-editor/editor/0.23.6/.../editor-0.23.6.aar`
- Runtime jar: `~/.gradle/caches/8.12/transforms/.../editor-0.23.6-runtime.jar`
