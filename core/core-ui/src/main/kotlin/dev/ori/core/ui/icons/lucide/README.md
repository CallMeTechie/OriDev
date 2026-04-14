# Lucide Icons (vendored)

This directory contains 68 Lucide icons used by Ori:Dev's UI, vendored as
Kotlin `ImageVector` constants from
[`composablehorizons/compose-icons`](https://github.com/composablehorizons/compose-icons),
specifically the `icons-lucide-cmp` Compose Multiplatform module.

**Why vendored, not a Maven dependency:**
- Plan v6 §3 item 3 locked-in: vendor the source rather than pull a runtime
  dep, so we control the exact set of icons and avoid pulling 1666 unused
  icons into the APK.
- The CI grep guard (`.github/ci/check-forbidden-imports.sh` from this PR;
  wired into `pr-check.yml` in PR 3.5) blocks any
  `androidx.compose.material.icons.*` import in `feature-*` and the
  primitives directory; feature code uses `LucideIcons.X` from this package
  instead.

## Source pin

| Field | Value |
|---|---|
| Upstream repo | `composablehorizons/compose-icons` |
| Upstream tag | `2.2.1` |
| Upstream tag SHA | `81bcbb50a91ce57888c781e19e81207643df39fa` |
| Upstream module | `icons-lucide-cmp` |
| Upstream package | `com.composables.icons.lucide` |
| Tarball SHA-256 | `d4bd3328e636980f66aedaffda82bde6bedf7d187e2601ca66cc8107e1b0882c` |
| Vendor date | 2026-04-14 (Phase 11 PR 3) |

## What changed during vendoring

Each `.kt` file was copied verbatim from upstream and rewritten via `sed`:

```
package com.composables.icons.lucide
                ↓
package dev.ori.core.ui.icons.lucide

val Lucide.<Name>: ImageVector ...
                ↓
val LucideIcons.<Name>: ImageVector ...

object Lucide
                ↓
object LucideIcons
```

No path data, stroke widths, or vector geometry were modified. Each icon
remains a 24×24 viewport vector with stroke-width 2.0, round line caps +
joins, transparent fill, and a black outline (Lucide canonical defaults).

## Plan v6 ↔ Lucide 2025 name mapping

Lucide renamed several icons between Lucide 2024 (when plan v6 was written)
and Lucide 2025 (the version shipping in compose-icons 2.2.1). Feature
modules use the **2025 names**:

| Plan v6 (Lucide 2024) | Lucide 2025 (this dir) |
|---|---|
| `Filter` | `ListFilter` |
| `MoreVertical` | `EllipsisVertical` |
| `MoreHorizontal` | `Ellipsis` |
| `Edit3` | `PenLine` |
| `StopCircle` | `CircleStop` |
| `Unlock` | `LockOpen` |
| `Fingerprint` | `FingerprintPattern` |
| `AlertTriangle` | `TriangleAlert` |
| `AlertCircle` | `CircleAlert` |
| `Grid` | `Grid2x2` |

## License

ISC (Lucide upstream) + MIT (portions derived from Feather, attributable
to Cole Bemis, 2013–2023). Full text in
`core/core-ui/src/main/assets/licenses/lucide-icons-isc-mit.txt`.

The `aboutlibraries` plugin manual entry that surfaces this attribution
in the in-app Settings → About → Licenses screen lands in PR 1.2 (settings
expansion) — the JSON file at `config/aboutlibraries/libraries/lucide.json`
is created there.
