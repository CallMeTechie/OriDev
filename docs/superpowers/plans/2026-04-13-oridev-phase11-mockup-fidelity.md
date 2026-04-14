# Phase 11 — Mockup Fidelity

**Status:** Draft v6 (executable — scoped review cycle 4 findings applied)
**Created:** 2026-04-13 (v1), revised 2026-04-13 (v2, v3, v4, v5, v6)
**Author:** Implementation planning, Ori:Dev
**Supersedes:** Visual polish aspects of Phase 10

---

## Changelog

### v5 → v6 (applied after scoped review cycle 4 on §P0.10)

**Blockers fixed:**
- **`mockup-layout-tests` moved from PR 3.5 → PR 4a.** `feature-settings/src/` currently has only `main/` — no `androidTest/`. PR 3.5 could not run `connectedDebugAndroidTest` green because no test exists yet. Restructure: PR 3.5 is now purely **non-instrumented** CI work (static checks, Gradle tasks, weekly canary, shell-script extraction, act docs). PR 4a gains the androidTest scaffolding + `SettingsScreenLayoutTest` creation + the `mockup-layout-tests` emulator job. Sora spike androidTest (P0.8) moves to **PR 4b** since that PR already carries the spike. `feature-editor/src/androidTest/` setup is a sub-task of PR 4b.
- **`check-mockup-hash.sh` now fully specified.** v5 referenced the script name but never defined it. v6 provides the complete script body in P0.10.8, including the `--self-test` path that runs against `.github/fixtures/mockups-fixture/` (a minimal known-hash directory).

**Highs fixed:**
- **Shell-script `set -euo pipefail` + grep trap.** `grep` returns exit 1 when nothing matches (happy path for a no-violation check) which under `pipefail` kills the script. Fix: each grep invocation is wrapped in `|| true` with explicit exit-code capture via `${PIPESTATUS[0]}`, `fail` is initialized to `0` at the top, and grep exit 2 (regex/IO error) is distinguished from exit 1 (no matches) via `rc=$?; [ $rc -le 1 ] || exit 3`. Full rewritten script appears in P0.10.9.
- **`xargs` null-delimited.** All `xargs -r` calls become `xargs -r0` fed by `git diff --name-only -z`. Handles filenames with spaces or newlines.
- **Gradle `resolvedConfiguration` → `incoming.resolutionResult`.** Both `checkCoreFontsLeakage` and `checkWearLeakage` rewritten to use the non-deprecated API. `configurations.named(...).get().incoming.resolutionResult.allComponents.map { it.id.displayName }`. This also avoids early-resolution warnings under Gradle 8.x.
- **`mockup-layout-tests needs: [build]` → `needs: [code-quality, unit-tests]`.** Parallelizes with `build` instead of serializing after it. Saves ~5 min per PR.
- **`DeviceConfigurationOverride` claim dropped.** v5 claimed the test simulates fold/unfold widths via `DeviceConfigurationOverride`; review correctly pointed out this override affects only `LocalConfiguration` inside the composition, NOT `currentWindowAdaptiveInfo()` which reads from the Activity's real `WindowMetricsCalculator`. v6 replaces the claim with a narrower, honest scope: **the PoC test asserts the topbar-to-content offset invariant on a single size class (Pixel 6, compact)**. Fold-branch layout correctness is handled by **manual mockup diff in P2's per-screen PRs** (folded + unfolded emulator runs), not by this test. The gate test is about "no padding leak", which is size-class-independent.
- **AVD caching added** to the `mockup-layout-tests` job. `actions/cache@v4` keyed on `avd-pixel6-api34-google_apis-v1`, caching `~/.android/avd/*` and `~/.android/adb*`. Reduces cold-boot from ~10 min to ~2 min on cache hit. Realistic job time revised from 5–8 min to **5–10 min warm / 12–15 min cold**.

**Mediums fixed:**
- **`checkComposePreviews` Gradle task dropped as a no-op.** v5 admitted `-P compose.preview.strict=true` is not a stable flag and the fallback is a plain compile (already covered by `compileDebugKotlin`). A no-op green checkmark is worse than no gate. Preview-rendering regressions are caught implicitly by `mockup-layout-tests` (which instantiates real composables via `composeTestRule.setContent`). Explicit Compose Preview validation can come back later via Paparazzi or Showkase screenshot tests if the deferred spike reveals they work in this project.
- **`checkCoreFontsLeakage` no longer claims to run via `./gradlew test`.** v5 said "covered by `./gradlew test` flows" which is wrong — `test` runs unit tests, not `check`. v6 wires the task to `check` only, and PR 3.5 explicitly adds `./gradlew :core:core-fonts:checkCoreFontsLeakage :wear:checkWearLeakage :core:core-fonts:checkFontBudget` as separate steps in the `code-quality` job of `pr-check.yml` + same in `build.yml:test`. No implicit aggregation.

**Low/Nits fixed:**
- **Flake mitigation for `mockup-layout-tests`:** v6 adds a bash retry wrapper `for i in 1 2; do ./gradlew … && break; sleep 5; done; exit $?` inside the emulator script. Two attempts before the job fails. Empirical flake rate of ~5–15% drops to <1%.
- **`bad-imports.kt.sample` IDE noise:** renamed to `bad-imports.kt.txt` (non-`.kt` extension) so IntelliJ doesn't try to index it as Kotlin source. The `.txt` still contains Kotlin-looking content — grep doesn't care about file extension.

**PR topology after v6:**
- PR 3.5 = non-instrumented CI (static-import-checks, Gradle leak/budget tasks, weekly canary, act docs, script extraction, fetch-depth fixes)
- PR 4a = primitives + androidTest scaffolding for `feature-settings` + `SettingsScreenLayoutTest` + `mockup-layout-tests` CI job (instrumented) + PoC bail-out gate
- PR 4b = Sora spike + androidTest scaffolding for `feature-editor` + `SoraThemingSpike` + `@MockupPreviews` + mockup-hash guard + `OriDevTopBar` deprecation

### v4 → v5 (applied after user-raised concern "did you think about CI pipeline tests?")

v4 had CI self-tests only for the grep fixture. User correctly pointed out that multiple other CI pieces (mockup hash guard, dependency-leak check, font-size budget, `@MockupPreviews` render check, instrumented Settings PoC test) were specified as acceptance criteria but not wired into actual CI jobs, and that the diff-scoped grep had an unacknowledged shallow-clone bug.

**Key additions in v5:**
- **New §P0.10 — CI Pipeline Self-Tests & Reliability** (10 sub-tasks, ~80 new lines of plan)
- **New PR 3.5** stacked between PR 3 and PR 4a, dedicated to CI wiring (so PR 4a's bail-out gate isn't contaminated with CI work)
- Existing workflows (`.github/workflows/build.yml`, `pr-check.yml`, `release.yml`, `baseline-profile.yml`, `security.yml`) audited and referenced by actual file name / line — prior versions wrote "in `.github/workflows/build.yml`" without checking whether that's the right file for each check.

**Concrete bugs fixed:**
- **Shallow-clone bug:** `actions/checkout@v4` default is `fetch-depth: 1`. The diff-scoped grep command `git diff origin/main...HEAD` fails with `unknown revision` on a shallow clone — meaning v4's grep check would have been stuck in a broken state the first time anyone pushed a non-PR commit. All checkout actions in the new jobs use `fetch-depth: 0`.
- **Tag-build skip:** `release.yml` runs on tags. Diff against `main` does not exist there. New grep job adds `if: github.ref_type != 'tag'` skip guard.
- **Opt-in UI tests in pr-check.yml:197-230** use `if: contains(labels, 'run-ui-tests')` — this means `SettingsScreenLayoutTest` and the Sora spike would never run by default on PRs. New job `mockup-layout-tests` runs unconditionally on every PR (not label-gated).
- **Pixel Fold emulator availability is uncertain.** Instead of relying on an emulator profile that may not exist in `reactivecircus/android-emulator-runner`'s image catalog, the layout test uses Pixel 6 API 34 (profile already validated in the existing UI-tests job) and passes a `Configuration` override in the test setup to simulate unfolded/folded widths via `DeviceConfigurationOverride`.

**CI jobs specified (all running in pr-check.yml, mirrored in build.yml where applicable):**
1. `static-import-checks` — diff-scoped grep (P0.4 content), with `fetch-depth: 0`, with tag-skip, with positive-control fixture self-test
2. `check-core-fonts-leakage` — Gradle task that asserts `:core:core-fonts:dependencies` has no `material3` entries
3. `check-font-budget` — Gradle task that sums `core/core-fonts/src/main/res/font/*.ttf` sizes and fails at > 1.5 MB
4. `check-wear-leakage` — Gradle task `:wear:dependencies` with same `material3` assertion (catches regressions after P3 lands)
5. `mockup-layout-tests` — instrumented job using the existing `reactivecircus/android-emulator-runner@v2` setup, runs `SettingsScreenLayoutTest` + `SoraThemingSpike` unconditionally
6. `check-compose-previews` — compiles `core-ui` and `feature-settings` with a special flag that treats Compose Preview render warnings as errors
7. `mockup-hash-check` — unchanged logic from v3, but now has a **self-test** in `ci-self-test.yml`
8. `ci-self-test.yml` — a new weekly workflow (cron schedule) that runs every custom check above against *fixtures* (known-good and known-bad inputs) to verify the checks themselves still behave as designed. Canary against silent regression.

**`act` (nektos/act) documentation added** in the `docs/superpowers/plans/mockup-fidelity-ci-playbook.md` appendix (new file) so developers can iterate on workflow YAML locally without push-wait-retry cycles.

**Impact on PR count:** +1 (new PR 3.5) → **23 PRs total** (was 22 in v4).

### v3 → v4 (applied after review cycle 3: final devil's-advocate pass)

**Blockers fixed:**
- **P3.3 `wearTiny` references eliminated.** `ConnectionListScreen` ping uses `OriTypography.wearLabel` (Roboto Flex). `CommandOutputScreen` uses `OriTypography.wearTerminalTiny` (JetBrains Mono). The symbol `wearTiny` does not exist in v4 — any remaining reference would fail compilation.
- **P2.2 duplicate Sora-spike paragraph removed.** The spike lives only in P0.8 now. P2.2 simply "applies the theming approach validated in P0.8".
- **`configurations.all { exclude(material3) }` replaced with surgical per-dependency exclusions.** `implementation("androidx.compose.ui:ui-text") { exclude(group = "androidx.compose.material3") }` — per-dep only, does not affect test / androidTest / lint classpaths. Safer under Gradle 8.x eager evaluation.

**Highs fixed:**
- **CI grep is diff-scoped.** Instead of scanning all of `feature-*/src`, the grep job computes `git diff --name-only origin/main...HEAD -- 'feature-*/src/*.kt'` and runs its regex only on changed files. This means P1.1 (which touches `ConnectionListScreen.kt` for navigation wiring) does not have to simultaneously complete the `material.icons.*` migration in the same PR — only *new* violations fail. Pre-existing violations are migrated incrementally by P2's per-screen PRs. Errors are active from PR 3 merge onward on diff scope.
- **`aboutlibraries` ownership moved to `app/` with a navigation hand-off.** `AboutLibrariesScreen` is registered as a **route in `app/src/main/kotlin/.../OriDevNavHost.kt`**, not as a composable imported by `feature-settings`. The Settings "Lizenzen" row calls `navController.navigate("licenses")`, which lands on the app-module destination. `feature-settings` never imports the plugin's generated `Libs` class — it only knows the route string. This resolves the cross-module namespace problem.
- **`core-fonts` Compose dep has one canonical description.** The old v1→v2 changelog line stating "no Compose dependency" is explicitly struck and annotated as superseded. The single source of truth is P0.1: **"`core-fonts` depends on `androidx.compose.ui:ui-text` and `androidx.compose.ui:ui-unit` only, with `material3` excluded via surgical per-dependency exclusions."** Any remaining reference that contradicts this has been updated.

**Mediums fixed:**
- **Font subsetting Unicode ranges specified.** PR 1 runs `pyftsubset` with a specific codepoint set:
  - All fonts: `U+0020-007E` (Basic Latin), `U+00A0-00FF` (Latin-1 Supplement — covers äöüÄÖÜß), `U+2013-2014` (en/em dash), `U+2018-201D` (curly quotes), `U+2026` (ellipsis)
  - JetBrains Mono **additionally**: `U+2500-257F` (box drawing — required for terminal output rendering)
- **Lucide commit hash is a P0.3 spike step.** A ~30-minute sub-task at the start of PR 3: `curl https://api.github.com/repos/DevSrSouza/compose-icons/commits/main`, record the 40-char hash, commit it in the README comment block of `LucideIcons.kt`. The spike output goes into the PR description.
- **PR 4 is split into PR 4a and PR 4b.**
  - **PR 4a** (bail-out gate): Primitives (all 19) + Settings PoC + `SettingsScreenLayoutTest` instrumented test. This is the gate — if it fails, the gate triggers *only* on primitive/PoC concerns.
  - **PR 4b** (stacked on 4a, merges only after 4a passes): Sora-Editor spike (P0.8) + `@MockupPreviews` annotation (P0.9) + mockup-hash guard (P0.7) + `OriDevTopBar.kt` `@Deprecated` annotation.
  The Sora spike failing now blocks PR 4b, not PR 4a. Primitives stay mergeable.
- **Settings PoC test handles edge-to-edge status bar.** The test uses a relative-offset assertion instead of absolute `bounds.top`:
  ```kotlin
  val topBarBottom = onNodeWithTag("ori_top_bar").fetchSemanticsNode().boundsInWindow.bottom
  val contentTop   = onNodeWithTag("settings_content").fetchSemanticsNode().boundsInWindow.top
  assertThat(contentTop - topBarBottom).isWithin(1f).of(0f)
  ```
  The TopBar carries `testTag("ori_top_bar")`. The assertion is independent of status-bar inset or edge-to-edge mode.
- **Mockup-hash file bootstrapping atomic.** P0.7 explicitly requires the YAML job and `.github/mockup-hash.txt` to ship in the **same commit** within PR 4b. No intermediate state where the job runs against a missing file.
- **Sora spike moved to `androidTest/`.** The file is `feature-editor/src/androidTest/kotlin/.../SoraThemingSpike.kt`. Sora's `CodeEditor` extends `View`, which cannot run on a JVM-only unit test — instrumented runner is required.
- **Version catalog additions listed explicitly.** New `P0.0 — Version catalog additions` sub-section lists exact versions and pre-existing vs net-new status.

**Lows/Nits fixed:**
- **PR count corrected to 22.** PR 1, 2, 3, 4a, 4b (5) + P1 (2) + P2 (6) + P3 (2) + P4 (7 — merging P4.1 with P2.5 was considered but kept separate per v2 scope clean-up) = 22.
- **`@MockupPreviews` annotation body** now explicitly annotated with `@Retention(AnnotationRetention.BINARY)` and `@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)` so IDE and CLI preview tooling both pick it up.
- **v1→v2 changelog "PR 0 as its own small PR" entry struck** as superseded by v3.
- **Risks table "PR 0 audits" wording** updated to "PR 2 Commit 0 audits".

### v2 → v3 (applied after review cycle 2: feature-dev code-reviewer)

**Blockers fixed:**
- **minSdk** is `34` (verified at `app/build.gradle.kts:49` and `wear/build.gradle.kts:46`), not 21. All references updated. `currentWindowAdaptiveInfo()` requires API 21+, which is satisfied.
- **`core-fonts` transitive-leak really fixed.** Build file now *explicitly* declares only `androidx.compose.ui:ui-text` and `androidx.compose.ui:ui-unit` — no other Compose, no Material. A new P3 exit criterion runs `./gradlew :core:core-fonts:dependencies` and asserts no `material3` entries appear. The "tiny Compose dep" sentence in P0.1 is clarified.
- **PR 0 (Surface/Background swap) is merged into PR 2** as its first commit, not standalone. v2's claim that PR 0 could land independently was wrong — `onSurface` semantics depend on `surface`, and the audit would be done against an incomplete token set. PR 0 is now "Commit 0 of PR 2".

**Highs fixed:**
- **CI grep checks use two patterns**: (a) import-line grep for `^import androidx\.compose\.material3\.(TopAppBar|MediumTopAppBar|LargeTopAppBar|CenterAlignedTopAppBar)(\s*as\s*\w+)?$` and (b) wildcard-import guard `import androidx\.compose\.material3\.\*`. Same for `androidx.compose.material.icons`. Aliased imports are caught by the `as \w+` branch. Added unit tests that `.github/workflows/build.yml` passes on a synthetic "bad" file during CI self-test.
- **Sora-Editor theming spike pulled into P0 as new task P0.8.** Must succeed before PR 4 (primitives + PoC) merges. If it fails, Phase 11 pauses for separate investigation rather than merging P0 on an unproven editor strategy.
- **`@MockupPreviews` annotation is now defined** in P0.11 with the correct Pixel Fold dp device spec:
  `@Preview(name = "Unfolded", device = "spec:width=2208dp,height=1840dp,dpi=408,isRound=false")` for inner and `@Preview(name = "Folded", device = "spec:width=1080dp,height=2092dp,dpi=420,isRound=false")` for cover. (Note: Compose Preview DSL accepts **dp** only, not px — v2 was wrong on this.)
- **`aboutlibraries-plugin` moved to `app/build.gradle.kts`** (library-module application was wrong — the plugin aggregates across deps and only runs meaningfully from the app module). A **manual Lucide entry** is added via the plugin's `registerAndroidTasks` with a hand-written `licenses/lucide_mit.json` describing Lucide MIT + compose-icons Apache 2.0 since the vendored source has no Maven POM.
- **Lucide dual-license attribution.** Vendored source files carry both attributions: `core/core-ui/src/main/assets/licenses/lucide-mit.txt` (upstream Lucide, MIT/ISC, lucide.dev) **and** `core/core-ui/src/main/assets/licenses/compose-icons-apache2.txt` (wrapper source, Apache 2.0, devsrsouza/compose-icons). A README comment block in `LucideIcons.kt` links both upstreams with commit hashes.
- **`OriTopBar` background is a named token**, not a hex literal. New `Color.kt` export: `val TopBarBackground = Color.White.copy(alpha = 0.92f)` — single source of truth, satisfies CLAUDE.md "no hardcoded colors" rule.

**Mediums fixed:**
- `core-fonts` build file explicitly uses **both** `com.android.library` and `org.jetbrains.kotlin.android` plugins. The "no kotlin-android needed" sentence was a typo in v2.
- **Settings PoC gate is machine-verifiable.** Added an instrumented test `SettingsScreenLayoutTest` that composes the screen, queries `onNodeWithTag("settings_content").fetchSemanticsNode().boundsInWindow.top` and asserts `topPx == 56.dp.toPx()` (no inset leak). `testTag("settings_content")` added to the root `LazyColumn`.
- **Scaffold elevation for `OriTopBar`**: P0.5 explicitly states the Scaffold's `topBar` slot must receive a `Surface(tonalElevation = 0.dp, shadowElevation = 0.dp)` wrapper or call `OriTopBar` directly without `Surface` — Compose's default `Scaffold` does NOT add elevation under the `topBar` slot, but we assert this explicitly so future Compose version bumps don't silently introduce a shadow.
- **`OriEmptyState` moved into P0.5 primitive list.** No more ad-hoc introduction in P2.4. Corrects the self-contradiction with the "all primitives in P0" rule.
- **`OriCheckbox` added definitively to P0.5 primitive list** — 16 × 16 dp custom, since `file-manager.html` clearly shows a checkbox column and there is no point leaving it as "if needed".
- **Mockup hash moved to `.github/mockup-hash.txt`** (close to workflow that consumes it). Graceful fallback: `if [ ! -f .github/mockup-hash.txt ]; then echo "::warning::Hash file missing"; exit 0; fi`.
- **`OriTypography.wearTiny` renamed to `wearTerminalTiny`** (for command output only, JetBrains Mono). A new `OriTypography.wearLabel` (Roboto Flex, 11 sp / 500) is added for general Wear labels like ping times and server names. Respects the "Roboto Flex on Wear" lock.

**Lows/Nits fixed:**
- **Font subsetting threshold is 1.5 MB before PR 1 merges**, not after. If raw TTFs exceed 1.5 MB combined, `pyftsubset` is run as part of PR 1. The measurement step is now a blocker for PR 1 sign-off.
- **Existing `OriDevTopBar.kt` is deprecated in PR 4** with `@Deprecated(message = "Replaced by OriTopBar", replaceWith = ReplaceWith("OriTopBar"))` and removed entirely when the last feature screen migrates (tracked in P2 exit criterion).
- **Icons added to P0.3 vendor list**: `User`, `Clock`, `RefreshCw`, `Square`, `Layers`, `Pencil` (confirmed present in `settings.html` and `proxmox.html`).
- **`aboutlibraries-plugin` applied to `app/build.gradle.kts`** per point above.

**New blocker sub-tasks:**
- **P0.8 — Sora-Editor theming spike** (moved from P2.2 start into P0).

### v1 → v2 (applied after review cycle 1: devils-advocate + code-architect)

**Structural reversals:**
- **REMOVED**: centralized app-level Scaffold + `ScreenTopBarSpec` sealed class + `LocalSnackbarHostState` CompositionLocal + centralized FAB slot (both reviewers unanimously flagged this as the plan's biggest risk). Each screen **keeps its own Scaffold**; only the inner `TopAppBar` is swapped for `OriTopBar`. Dynamic titles (tab hostname, filename, selected-count) keep working. No backchannel needed.
- **SPLIT**: P0 into four stacked PRs (was single monster PR).
- **ADDED**: Bail-out gate — migrate Settings screen as proof-of-concept **before** merging the foundation PRs. If primitives don't feel right, revise pre-merge.

**Module changes:**
- **NEW**: `core-fonts` Android-library module. Pure `res/font/*.ttf` + `FontFamily` factories. ~~No Compose dependency.~~ **(Superseded by v3/v4: depends on `androidx.compose.ui:ui-text` + `ui-unit` only, `material3` surgically excluded — see P0.1.)** Both phone `core-ui` and `wear` depend on it. Avoids the transitive Phone-Compose leak into Wear that the architect identified.
- Wear primitives (`WearProgressRing`, `WearGauge`, etc.) stay in the `wear/` module, not in `core-ui`. Prevents Wear-only code in phone APK.
- TextMate grammars move to `feature-editor/src/main/assets/textmate/` (not `core-ui/assets/`). Module boundary fix.

**Decisions locked in by user:**
1. **Lucide icons vendored** from `br.com.devsrsouza.compose.icons:lucide` — source files copied into `core/core-ui/src/main/kotlin/dev/ori/core/ui/icons/lucide/` with MIT-license attribution. Not a runtime dependency, but we don't hand-port SVGs. Error rate 0.
2. **Wear font is Roboto Flex, not Inter.** Inter at 8–12 sp on round OLED displays is known illegible. Roboto Flex is Google's recommendation for Wear.
3. **Premium paywall deferred to a future Phase 12** (Monetarisierung). P1.2 shows a plain row "Premium — bald verfügbar" with no bottom sheet, no pricing tiles, no `FeatureGateManager` dependency. `FeatureGateManager` existence is NOT assumed (Phase 9 was skipped).

**Tooling de-risked:**
- Detekt custom rules downgraded to **simple CI grep checks** in `.github/workflows/build.yml` (no `buildSrc` module creation, no `detekt-api`). Rules: forbidden imports of `androidx.compose.material.icons.*`, `androidx.compose.material3.TopAppBar`/`MediumTopAppBar`/`LargeTopAppBar`/`CenterAlignedTopAppBar`, `androidx.compose.material3.Scaffold` (scoped to `feature-*`). The "no hardcoded dp" rule is dropped entirely — component-intrinsic heights like 44/52/60 dp live in the component itself, not in a spacing scale.
- Paparazzi screenshot tests are now **opt-in, post-P0-merge**. Primitives and screens get `@MockupPreviews` (folded + unfolded), reviewers diff visually. A Paparazzi spike happens in a separate sidebranch on exactly one primitive (`OriPillButton`) before any golden test is added to the main CI.
- `Modifier.blur(12.dp)` dropped — Compose has no backdrop-filter equivalent. `OriTopBar` uses solid 92 % white.
- `OriIconButton` is visually 32 × 32 dp but uses `Modifier.minimumInteractiveComponentSize()` so the touch target is 48 dp. Accessibility scanners pass.

**File-path corrections (from architect):**
- Existing `OriDevTopBar.kt` lives at `core/core-ui/src/main/kotlin/dev/ori/core/ui/component/OriDevTopBar.kt` (singular `component/`). v1 wrongly said "in `app/`". New file goes to the same directory — we replace the existing one in place rather than create a new plural `components/` directory.
- `OriDevApp.kt:51` already contains a top-level Scaffold with `bottomBar` — we do NOT add one, we just keep it unchanged. No topBar slot added there.
- `OriDevTheme.kt:38` references `OriDevShapes`. We rename the existing `Shape.kt` content to incorporate the new tokens and keep the `OriDevShapes` name so no call-site update is needed. (v1 introduced `OriShapes` which would have been inert.)
- `OriDevTheme.kt` is now explicitly listed as a modified file in P0.4 + P0.3 (Surface/Background swap).
- `wear/build.gradle.kts:84-100` does NOT depend on `core-ui`. We add `implementation(project(":core:core-fonts"))` instead. Phone's `core-ui` also depends on `:core:core-fonts`.

**`OriTypography` access pattern clarified:**
- Custom styles like `terminalBody`, `editorBody`, `hostMono`, `wearLogo` live on a Kotlin `object OriTypography` and are accessed directly: `OriTypography.terminalBody`. The M3 `Typography` object receives the phone's 15-entry scale (displayLarge … overline) from the mockup and is installed into `MaterialTheme` as usual. M3 components (dialogs, dropdowns) read `MaterialTheme.typography.*` and get the overridden phone scale automatically. No `CompositionLocal` needed.
- A single `@file:JvmName("OriType")` file exposes both: `val OriType = Typography(...)` for M3 and `object OriTypography { val terminalBody: TextStyle = ... }` for custom slots. Feature modules import whichever they need.

**Existing translations:**
- Settings screen stays in German (`"Einstellungen"`, `"Datenschutz"`, etc.) — confirmed. All new section labels, row titles, subtitles in P1.2 are written in German from the start. Later i18n (English, etc.) is a separate concern out of this phase.

**Scope clean-up:**
- Rename/Chmod/Mkdir dialogs are NOT pulled into P2.5 quietly. They stay in P4 (functional gap closure). P2 is visual-alignment only.
- `OriDropdown`, `OriInput`, `OriSegmentedControl` previously introduced ad-hoc in P1/P2 are now listed in P0.9 where all primitives live.

**Stroke width / Lucide defaults:**
- Lucide standard stroke-width is 2.0 (not 1.75 as v1 claimed). The vendored files use 2.0 natively — we match.

**Responsive layout:**
- Settings content width: instead of hard `max-width = 640 dp`, use `LazyColumn` filling available width with internal 24 dp horizontal padding, capped at 720 dp max when `WindowWidthSizeClass.Expanded`. Unfolded inner display no longer wastes ~960 dp. Same pattern for Transfer Queue.

**Fold detection:**
- Use `currentWindowAdaptiveInfo()` from `androidx.compose.material3.adaptive` (dependency `androidx.compose.material3.adaptive:adaptive`). Replaces both `screenWidthDp` checks and direct `WindowLayoutInfo` usage.

**Licenses:**
- Use `com.mikepenz:aboutlibraries-plugin` to auto-collect license info at build time. Custom `OssLicensesActivity` dropped from v1. Attribution files (Inter OFL, JetBrains Mono Apache-2, Roboto Flex Apache-2, Lucide MIT) still bundled under `core/core-fonts/src/main/assets/licenses/` as fallback.

**Surface=White as atomic PR:**
- ~~The `surface = Color.White`, `background = Gray50` swap is its **own small PR** (PR 0 of the foundation stack), with a visual audit of every screen that currently references `colorScheme.surface` or `colorScheme.background`. Merged before P0 PR 1.~~ **Superseded by v3/v4:** the swap is now Commit 0 of PR 2 (the token rewrite PR), not its own PR — `onSurface` semantics depend on `surface` and the audit needs the full token set visible in a single diff.

**Dark terminal confirmation:**
- Tokyo-Night (`#1A1B26`) inside a light-only app was flagged by the reviewer. This is mockup-driven — `terminal.html` shows a dark terminal body even on the light-theme mockup. Staying with Tokyo-Night. Noted explicitly.

**Architect-requested additions:**
- `OriDevTheme.kt` call-site for shapes + typography updated explicitly (A1).
- `buildSrc` creation removed (tooling de-risked above — no buildSrc needed).
- Paparazzi setup deferred to post-P0 spike (A5 addressed by deferral).
- `ScreenTopBarSpec` dropped (A6 moot via structural reversal).
- Wear font module: see `core-fonts` decision (A7).
- German strings: see translations note (A8).
- `FeatureGateManager`: dropped (A9).
- FAB migration: moot — FABs stay in their per-screen Scaffolds (D9, A3 moot).
- `SnackbarHost` migration: moot — stays in per-screen Scaffolds (D4, A2 moot).

**Mockup drift guard (F30):**
- New CI job `mockup-hash-check` hashes `/root/OriDev/Mockups/*.html` and warns on PRs that change them, prompting a Phase 11 revisit.

**Review workflow (§13) agent names corrected:**
- Round 2 reviewer: `superpowers:requesting-code-review` skill (calls `code-review:code-review`).
- Round 3 reviewer: `devils-advocate` skill with an explicit "since v2" diff prompt.

---

## 1. Goal

Bring the entire Ori:Dev Compose implementation (phone + Wear) to **1:1 pixel fidelity** with the HTML mockups in `/root/OriDev/Mockups/`. Fix the systemic root causes — Inter font missing, Material 3 TopAppBar/icon defaults, theme token drift — before touching individual screens, so that screen work does not have to be redone.

Outcome: every screen matches its mockup in typography, spacing, colors, icons, and component structure. No more "too much space at the top" complaints. Also: close the P0 functional blockers (connection navigation stubs, destructive dialogs, missing rename/chmod/mkdir UIs, editor undo/redo) while we are already inside those files.

## 2. Non-Goals (Out of Scope)

- **Dark mode** — user confirmed light-only (memory: `feedback_design_light.md`).
- **Backend / network / domain changes** — no touching `core-network`, `core-security`, `data`, `domain` layers except for Hilt wiring of new primitives.
- **Premium paywall / Play Billing** — deferred to a future Phase 12 (Monetarisierung).
- **Proxmox noVNC / SPICE console** — separately tracked.
- **LXC container management** — out of scope.
- **Large-file editor streaming** — P4 only as stretch.
- **i18n beyond German** — staying in German.
- **Dark terminal → light terminal migration** — Tokyo-Night body stays per mockup.

## 3. Locked-In Decisions (confirmed by user)

1. **Inter + JetBrains Mono as static TTF assets** under `core/core-fonts/src/main/res/font/`. No Google Fonts Downloadable Fonts.
2. **Roboto Flex for Wear** (at sizes ≤ 16 sp). Inter is phone-only.
3. **Lucide icons vendored** from `br.com.devsrsouza.compose.icons:lucide` — source files copied into `core/core-ui/src/main/kotlin/dev/ori/core/ui/icons/lucide/`. MIT license attribution.
4. **Scaffold stays per-screen.** Only `TopAppBar → OriTopBar` is the structural swap.
5. **Reviews before implementation:** 3 cycles before any code is written.
6. **Settings screen stays German.**
7. **`core-fonts` as new module** — shared by phone `core-ui` and `wear`.

## 4. Phase Overview

| Phase | Theme | Blocking | Approx. scope |
|---|---|---|---|
| **P0** | Foundation — fonts, tokens, primitives, OriTopBar, icons, surface fix | everything downstream | ~14 tasks, 4 stacked PRs |
| **P1** | Connection navigation P0-unblock + Settings full build | app usability | ~9 tasks |
| **P2** | Screen alignment to mockups (Terminal, Editor, Filemanager, Proxmox, Connections, Transfers) | visual parity | ~24 tasks |
| **P3** | Wear OS OLED theme + ring/gauge/panic components | Wear parity | ~9 tasks |
| **P4** | Functional gap closure (destructive dialogs, rename/chmod/mkdir, TextMate grammars, editor undo/redo, onboarding permissions) | release readiness | ~11 tasks |

Total: ~67 tracked tasks. Each phase ends with a `./gradlew detekt test` green gate, the CI grep checks green, and a manual visual diff against the mockup in both folded and unfolded configurations.

---

## 5. Phase 0 — Foundation

**PR stack (bottom-up):**
- **PR 1** — `core-fonts` module + Inter + JetBrains Mono + Roboto Flex + `FontFamily` factories + font-size audit (1.5 MB threshold)
- **PR 2** — Commit 0: Surface/Background swap with screen audit + screenshots → Commits 1..N: Color/Shape/Spacing tokens, Typography rewrite, `OriDevTheme` rewire. Single PR because `onSurface` semantics depend on `surface` and the swap can't be audited against an incomplete token set.
- **PR 3** — Lucide icon pack (vendored with dual-license attribution) + CI grep checks (import-line + wildcard-import + aliased-import patterns)
- **PR 4** — Sora-Editor theming spike (P0.8) + Primitives (`OriTopBar`, `OriCard`, `OriToggle`, `OriChip`, `OriPillButton`, `OriStatusBadge`, `OriProgressBar`, `OriStatusDot`, `OriSectionLabel`, `OriSearchField`, `OriIconButton`, `OriFab`, `OriInput`, `OriDropdown`, `OriSegmentedControl`, `OriConfirmDialog`, `OriMiniBar`, `OriCheckbox`, `OriEmptyState`) + **Bail-out gate: migrate `SettingsScreen.kt` as PoC with instrumented `SettingsScreenLayoutTest`**

Each PR stacked on the previous. PR 4 does not merge until (a) the Sora-Editor spike succeeds, (b) the instrumented PoC test is green, (c) visual diff vs `settings.html` matches.

### P0.0 — Surface / Background swap (Commit 0 of PR 2)
**File:** `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/OriDevTheme.kt:19`
Change `surface = Gray50` to `surface = Color.White`, and confirm `background = Gray50`.

**Why this is now inside PR 2 instead of its own PR:** `onSurface` and `onBackground` are semantically tied to `surface`/`background`. Reviewing the swap in isolation against the v1 (buggy, pre-token-rewrite) color set would be misleading — text contrast tokens that get added/tweaked in P0.2 would only make sense after both commits. Making this the first commit of PR 2 gives reviewers the full before/after picture in one diff.

**Audit steps (as the first commit of PR 2):**
1. `rg 'colorScheme\.surface|colorScheme\.background|MaterialTheme\.colorScheme\.surface' --type kt` across the repo.
2. For each hit, note whether the composable is a card-like element (should sit on white now) or a page background (should sit on gray).
3. Capture before/after screenshots of each affected screen on a Pixel Fold emulator (folded + unfolded) using the Settings, Connections and Transfers screens as the three canonical diff targets.
4. Attach to the PR description.

**Acceptance:** no visual regressions; cards clearly separate from page background; all hits reviewed.

### P0.1 — `core-fonts` module creation (PR 1)

**New module:** `core/core-fonts/`

**Files:**
- `core/core-fonts/build.gradle.kts` — applies **both** `com.android.library` and `org.jetbrains.kotlin.android` plugins (the v2 phrasing "no kotlin-android needed" was a typo — Kotlin source files require the plugin). `compileSdk` and `minSdk` inherit from the project catalog (**minSdk 34**). Dependencies: **only** `androidx.compose.ui:ui-text` and `androidx.compose.ui:ui-unit` from Compose — no `material3`, no `foundation`. The `material3` exclusion is surgical (per-dependency), not global:

```kotlin
dependencies {
    implementation("androidx.compose.ui:ui-text") {
        exclude(group = "androidx.compose.material3")
    }
    implementation("androidx.compose.ui:ui-unit") {
        exclude(group = "androidx.compose.material3")
    }
}
```

This scope-limits the exclusion to `implementation` only and does not affect `testImplementation` or `androidTestImplementation` classpaths (which legitimately need Compose test libraries that transitively depend on `material3`). v3's `configurations.all { exclude(...) }` was unsafe under Gradle 8.x eager container evaluation and was replaced.
- `core/core-fonts/src/main/AndroidManifest.xml`
- `core/core-fonts/src/main/res/font/inter_regular.ttf` (W400)
- `core/core-fonts/src/main/res/font/inter_medium.ttf` (W500)
- `core/core-fonts/src/main/res/font/inter_semibold.ttf` (W600)
- `core/core-fonts/src/main/res/font/inter_bold.ttf` (W700)
- `core/core-fonts/src/main/res/font/inter_black.ttf` (W900)
- `core/core-fonts/src/main/res/font/jetbrains_mono_regular.ttf` (W400)
- `core/core-fonts/src/main/res/font/jetbrains_mono_medium.ttf` (W500)
- `core/core-fonts/src/main/res/font/jetbrains_mono_bold.ttf` (W700)
- `core/core-fonts/src/main/res/font/roboto_flex_regular.ttf` (W400) — Wear
- `core/core-fonts/src/main/res/font/roboto_flex_medium.ttf` (W500) — Wear
- `core/core-fonts/src/main/res/font/roboto_flex_bold.ttf` (W700) — Wear
- `core/core-fonts/src/main/res/font/inter.xml` (font family descriptor)
- `core/core-fonts/src/main/res/font/jetbrains_mono.xml`
- `core/core-fonts/src/main/res/font/roboto_flex.xml`
- `core/core-fonts/src/main/kotlin/dev/ori/core/fonts/OriFonts.kt` — exposes `Inter`, `JetBrainsMono`, `RobotoFlex` as `FontFamily` singletons. This file **does** depend on `androidx.compose.ui.text`, so `core-fonts` has a tiny Compose runtime dep. That's unavoidable for `FontFamily`, but critically it does NOT depend on `material3` — which is what caused the Wear transitive leak.
- `core/core-fonts/src/main/assets/licenses/inter-ofl.txt`
- `core/core-fonts/src/main/assets/licenses/jetbrains-mono-apache2.txt`
- `core/core-fonts/src/main/assets/licenses/roboto-flex-apache2.txt`

**Settings-gradle changes:**
- `/root/OriDev/settings.gradle.kts` — add `include(":core:core-fonts")`

**Dependency wiring:**
- `core/core-ui/build.gradle.kts` — add `implementation(project(":core:core-fonts"))`
- `wear/build.gradle.kts` (currently at `wear/build.gradle.kts:84-100` without `core-ui`) — add `implementation(project(":core:core-fonts"))`. Do NOT add `core-ui`.

**Font-subset decision:** **Measured before PR 1 merges** — threshold is **1.5 MB** combined TTF size. If the raw files exceed that, run `pyftsubset` within PR 1.

**Unicode ranges kept (all fonts):**
- `U+0020-007E` Basic Latin
- `U+00A0-00FF` Latin-1 Supplement (äöüÄÖÜß and all German accented chars)
- `U+2013-2014` en dash / em dash
- `U+2018-201D` curly quotes
- `U+2026` horizontal ellipsis

**JetBrains Mono additionally keeps:**
- `U+2500-257F` Box Drawing (required for terminal output rendering via `top-level-box`, `tree`, etc.)

PR 1 sign-off requires the measurement step and, if subsetting was applied, the exact `pyftsubset` command line in the PR description.

**Dependency-leak verification (part of PR 1 exit criteria):** run `./gradlew :core:core-fonts:dependencies` and grep the output — no lines containing `androidx.compose.material3` may appear. The same check is repeated as P3 exit criterion to catch regressions introduced by Wear work.

**Acceptance:** `Inter`, `JetBrainsMono`, `RobotoFlex` importable from `dev.ori.core.fonts.OriFonts`. A harness composable rendering "äöüßAaGg012" in each family works on phone and wear. Dependency grep clean. Font size within budget.

### P0.2 — Typography, Color, Shape, Spacing tokens (PR 2)

#### Color tokens
**File:** `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/Color.kt` (add, don't rename existing exports)

Add the missing tokens listed in v1 section P0.3 (BorderLight, IndigoSubtle, IndigoLight, IndigoBg, SkyBg, SkyText, YellowBg, YellowText, RedBg, RedText, GreenBg, GreenText, OledBlack, OledSurface, Syntax palette).

Keep existing `Gray50`, `Gray200`, `Indigo500`, etc.

#### Shape tokens
**File:** `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/Shape.kt`

**Keep the existing name `OriDevShapes`** so `OriDevTheme.kt:38` needs no update. Rewrite its content:

```kotlin
val OriDevShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(10.dp),
    large      = RoundedCornerShape(14.dp),   // was 16
    extraLarge = RoundedCornerShape(20.dp),
)

object OriExtraShapes {
    val pill      = RoundedCornerShape(percent = 50)
    val modalTop  = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val badge     = RoundedCornerShape(percent = 50)
    val progress  = RoundedCornerShape(percent = 50)
}
```

#### Spacing tokens
**File (new):** `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/Spacing.kt`

```kotlin
object OriSpacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val s   = 8.dp
    val m   = 12.dp
    val ml  = 16.dp
    val l   = 20.dp
    val xl  = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val xxxxl = 64.dp
}
```

Used for **semantic gaps** only. Component-intrinsic heights (44/52/60 dp etc.) stay inside the component.

#### Typography rewrite
**File:** `core/core-ui/src/main/kotlin/dev/ori/core/ui/theme/Type.kt`

Rewrite:
```kotlin
import dev.ori.core.fonts.OriFonts

val OriDevTypography = Typography(
    displayLarge   = TextStyle(fontFamily = OriFonts.Inter, fontSize = 48.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.035).em, lineHeight = 1.1.em),
    displayMedium  = TextStyle(fontFamily = OriFonts.Inter, fontSize = 32.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.030).em, lineHeight = 1.15.em),
    headlineLarge  = TextStyle(fontFamily = OriFonts.Inter, fontSize = 24.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.025).em, lineHeight = 1.2.em),
    headlineMedium = TextStyle(fontFamily = OriFonts.Inter, fontSize = 22.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.020).em, lineHeight = 1.25.em),
    headlineSmall  = TextStyle(fontFamily = OriFonts.Inter, fontSize = 20.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.020).em, lineHeight = 1.3.em),
    titleLarge     = TextStyle(fontFamily = OriFonts.Inter, fontSize = 18.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.015).em, lineHeight = 1.35.em),
    titleMedium    = TextStyle(fontFamily = OriFonts.Inter, fontSize = 15.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.010).em, lineHeight = 1.4.em),
    titleSmall     = TextStyle(fontFamily = OriFonts.Inter, fontSize = 13.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.010).em, lineHeight = 1.4.em),
    bodyLarge      = TextStyle(fontFamily = OriFonts.Inter, fontSize = 15.sp, fontWeight = FontWeight.W400, lineHeight = 1.5.em),
    bodyMedium     = TextStyle(fontFamily = OriFonts.Inter, fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 1.5.em),
    bodySmall      = TextStyle(fontFamily = OriFonts.Inter, fontSize = 13.sp, fontWeight = FontWeight.W400, lineHeight = 1.45.em),
    labelLarge     = TextStyle(fontFamily = OriFonts.Inter, fontSize = 13.sp, fontWeight = FontWeight.W500, lineHeight = 1.3.em),
    labelMedium    = TextStyle(fontFamily = OriFonts.Inter, fontSize = 12.sp, fontWeight = FontWeight.W500, letterSpacing = 0.01.em, lineHeight = 1.3.em),
    labelSmall     = TextStyle(fontFamily = OriFonts.Inter, fontSize = 11.sp, fontWeight = FontWeight.W500, letterSpacing = 0.05.em, lineHeight = 1.25.em),
)

object OriTypography {
    val overline     = TextStyle(fontFamily = OriFonts.Inter, fontSize = 11.sp, fontWeight = FontWeight.W600, letterSpacing = 0.08.em, lineHeight = 1.2.em)
    val terminalBody = TextStyle(fontFamily = OriFonts.JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.W400, lineHeight = 1.6.em)
    val editorBody   = TextStyle(fontFamily = OriFonts.JetBrainsMono, fontSize = 13.sp, fontWeight = FontWeight.W400, lineHeight = (20f / 13f).em)
    val hostMono     = TextStyle(fontFamily = OriFonts.JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.W400, letterSpacing = (-0.01).em)
    // Wear-specific (used by wear module only) — Roboto Flex for readable labels, JetBrains Mono ONLY for terminal output
    val wearLogo         = TextStyle(fontFamily = OriFonts.RobotoFlex, fontSize = 38.sp, fontWeight = FontWeight.W900)
    val wearCompact      = TextStyle(fontFamily = OriFonts.RobotoFlex, fontSize = 11.sp, fontWeight = FontWeight.W600, letterSpacing = 0.2.em)
    val wearLabel        = TextStyle(fontFamily = OriFonts.RobotoFlex, fontSize = 11.sp, fontWeight = FontWeight.W500)          // general labels (ping, server names)
    val wearTerminalTiny = TextStyle(fontFamily = OriFonts.JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.W400)        // command output only
}
```

`OriDevTheme.kt` already does `typography = OriDevTypography` — no call-site update needed.

**Access pattern:**
- M3 scale → `MaterialTheme.typography.titleSmall` (works in dialogs/dropdowns/sheets).
- Custom styles → `OriTypography.terminalBody` (direct object access).

### P0.3 — Lucide icon pack (vendored, PR 3)

**Upstream sources (dual license):**
- **Lucide icons** — `lucide.dev`, **ISC/MIT** license, original SVG path data
- **compose-icons wrapper** — `github.com/DevSrSouza/compose-icons`, **Apache 2.0** license, generated `ImageVector` Kotlin source

We vendor the *generated Kotlin source* from compose-icons (because hand-porting SVG path data carries a high error rate). Because that source is Apache 2.0 and the underlying icons are ISC/MIT, **both** attributions must ship.

**Process:**
1. **Hash-pin spike (~30 min, first task of PR 3):** `curl https://api.github.com/repos/DevSrSouza/compose-icons/commits/main` → record the 40-character commit hash. Commit the hash into the `LucideIcons.kt` README comment block. Goes into the PR description as proof-of-pin.
2. Download compose-icons source jar at that pinned commit.
3. Extract the `.kt` files under `br.com.devsrsouza.compose.icons.lucide`.
3. Copy the needed files into `core/core-ui/src/main/kotlin/dev/ori/core/ui/icons/lucide/` under the package `dev.ori.core.ui.icons.lucide`.
4. Rewrite the top-level receiver object to `dev.ori.core.ui.icons.lucide.LucideIcons`.
5. **Two attribution files** added:
   - `core/core-ui/src/main/assets/licenses/lucide-mit.txt` (upstream Lucide, ISC/MIT, lucide.dev)
   - `core/core-ui/src/main/assets/licenses/compose-icons-apache2.txt` (wrapper source, Apache 2.0, devsrsouza/compose-icons)
6. Add a README comment block in `LucideIcons.kt` linking **both** upstreams with their licenses and commit hashes.
7. Manual `aboutlibraries` entry added as a JSON file under `config/aboutlibraries/libraries/lucide.json` describing both licenses — the plugin's `registerAndroidTasks` path picks this up because vendored source has no Maven POM to auto-detect.

**Icons vendored (first pass, ~68 icons, add more as P2 needs them):**
ChevronLeft, ChevronRight, ChevronDown, ChevronUp, X, Check, Plus, Minus, Search, Filter, MoreVertical, MoreHorizontal, Settings, Star, Trash2, Edit3, Pencil, Copy, Clipboard, Folder, FolderOpen, File, FileText, FileCode, Image, Download, Upload, ArrowLeftRight, Pause, Play, Square, StopCircle, RotateCcw, RefreshCw, Terminal, Server, Database, HardDrive, Cpu, Wifi, WifiOff, Link2, Lock, Unlock, Eye, EyeOff, Key, Fingerprint, Smartphone, Watch, Code, AlertTriangle, AlertCircle, Info, Bookmark, Zap, Crown, Bell, Moon, Sun, Grid, List, Menu, Globe, ExternalLink, User, Clock, Layers

(Added vs. v2: `Pencil`, `Square`, `RefreshCw`, `User`, `Clock`, `Layers`. Confirmed present in `settings.html` and `proxmox.html`.)

**Acceptance:** `LucideIcons.ChevronLeft` etc. importable. Both license files present. `aboutlibraries` configuration produces a "Lucide" entry in the generated metadata. CI grep check verifies no `androidx.compose.material.icons.*` imports in `feature-*` or new primitives.

### P0.4 — CI grep checks concept (PR 3, wiring in PR 3.5)

**Concept:** diff-scoped grep that blocks new `Material.icons.*` and `Material3.TopAppBar` family imports in `feature-*` and new primitives. Patterns must catch direct imports, aliased imports (`as M3Bar`), and wildcard imports as three distinct regexes.

**Script lives at:** `.github/ci/check-forbidden-imports.sh` — authoritative full body in §P0.10.9 (written with robust shell semantics: `set -u` only, NO `pipefail`; `fail=0` explicit; null-delimited `xargs -r0`; `--self-test` mode against `.github/fixtures/bad-imports.kt.txt` fixture).

**Diff scope rationale:** existing feature screens still import `material.icons.*` and `material3.TopAppBar`. If the grep were full-tree, any unrelated PR touching a feature file would fail until the entire P2 migration completes — blocking P1.1 (nav unblock) and Phase 11 progress. By scoping to `git diff --name-only origin/<base>...HEAD`, only **new** violations in changed files fail. Pre-existing violations are cleaned up naturally by each P2 sub-phase's per-screen PR (which changes that very file, bringing it back into the diff scope).

**PR split:**
- **PR 3** (this task) introduces the vendored Lucide icons and creates the shell script file and the fixture. The script is executable from the command line but not yet wired into any workflow YAML.
- **PR 3.5** (see §P0.10) adds the YAML job that invokes the script inside `pr-check.yml` and `build.yml`, plus the `fetch-depth: 0` checkout configuration, plus the positive-control self-test step.

Splitting PR 3 from PR 3.5 keeps review scope manageable: PR 3 reviewers verify the icons and script logic, PR 3.5 reviewers verify the workflow integration.

### P0.5 — Primitive components (PR 4)

**Directory:** `core/core-ui/src/main/kotlin/dev/ori/core/ui/components/` (new plural directory — the existing singular `component/` stays until PR 4, when `OriDevTopBar.kt` is deprecated; the old file is removed after the last feature screen migrates in P2).

**Color-token prerequisite:** A new named token `TopBarBackground = Color.White.copy(alpha = 0.92f)` is added to `Color.kt` in PR 2. The v2 hex literal `Color(0xEBFFFFFF)` was a CLAUDE.md violation ("always use `MaterialTheme.colorScheme.*`. No hardcoded colors"). All subsequent references point to the named token.

**OriTopBar** — `OriTopBar.kt`
```kotlin
@Composable
fun OriTopBar(
    title: String,
    modifier: Modifier = Modifier,
    height: Dp = OriTopBarDefaults.Height,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    hairline: Boolean = true,
)

object OriTopBarDefaults {
    val Height         = 56.dp   // most screens
    val HeightCompact  = 44.dp   // terminal
    val HeightDense    = 40.dp   // editor
    val HorizontalPadding = 16.dp
}
```

Implementation: a plain `Row` inside a `Surface(tonalElevation = 0.dp, shadowElevation = 0.dp, color = TopBarBackground)` — the explicit 0-dp elevations guarantee that when this composable is placed inside `Scaffold { topBar = { OriTopBar(...) } }`, Compose's internal Scaffold layout won't introduce a shadow underneath (current Compose does not, but we assert it so future version bumps don't silently regress). Title rendered with `MaterialTheme.typography.titleSmall` in `TextPrimary`. `navigationIcon` and `actions` slots use `OriIconButton` wrapping Lucide icons 20 dp. 1-dp `Border`-colored hairline drawn via `drawBehind` at the bottom when `hairline = true`.

**Used via screens' existing Scaffold:**
```kotlin
Scaffold(
    topBar = { OriTopBar(title = "Einstellungen", navigationIcon = { OriIconButton(LucideIcons.ChevronLeft, onClick = onBack) }) },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = { /* existing per-screen FAB stays */ },
) { innerPadding -> ... }
```

No Material 3 `TopAppBar` reference anywhere. Snackbar + FAB remain per-screen. No nested Scaffold issue because Scaffold was never nested in the first place — the bug was using `TopAppBar` with its 64 dp default inside the existing per-screen Scaffold. Fixing `TopAppBar → OriTopBar` fixes it.

**Other primitives** (all as described in v1 P0.9, adding `OriInput`, `OriDropdown`, `OriSegmentedControl`, `OriConfirmDialog`, `OriMiniBar`):

- `OriCard` — `Surface(shape = MaterialTheme.shapes.large, color = Color.White, border = BorderStroke(1.dp, Border))` — shape picked up from `OriDevShapes.large = 14 dp`.
- `OriToggle` — custom 44 × 26 dp track / 20 dp thumb, `#D1D5DB` off / `#6366F1` on, animated.
- `OriChip` — pill shape, 28 dp height, `labelLarge`, solid `Indigo500` bg when selected (not alpha), count badge slot for `TransferFilterChips`.
- `OriPillButton` — text + optional icon, 11.5 sp / 500, padding 4 × 12 dp, border 1 dp, radius 8 dp, variants `default` / `danger` / `primary`.
- `OriStatusBadge` — pill, 11 sp / 600, padding 3 × 10 dp, enum-driven bg/text pairs.
- `OriProgressBar` — `Canvas`-drawn, 5/6/4 dp height variants, `OriExtraShapes.progress`.
- `OriStatusDot` — 8 × 8 dp circle, optional pulse glow for Wear.
- `OriSectionLabel` — overline style, 4 dp left padding, 8 dp bottom margin.
- `OriSearchField` — 40 dp height, 10 × 14 dp padding, leading `Search` icon 16 dp, focus border `Indigo500`.
- `OriIconButton` — visually 32 × 32 dp, internal `Modifier.minimumInteractiveComponentSize()` for 48 dp touch target. Icon 16–18 dp.
- `OriFab` — 52 × 52 dp, radius 16 dp, `Indigo500`, shadow `0 6 dp 16 dp rgba(99,102,241,0.3)`. Used inside screens' own `Scaffold { floatingActionButton = { OriFab(...) } }`.
- `OriInput` — outlined text field, 40 dp height, 10 × 14 dp padding, label 12 sp / 600 above input with 6 dp gap, focus border `Indigo500`. Wraps `BasicTextField`.
- `OriDropdown` — exposed-dropdown equivalent using `OriInput` as anchor + custom popup with `OriCard` content.
- `OriSegmentedControl` — pill container with equal-width button segments, selected segment gets `Indigo500` bg.
- `OriConfirmDialog` — `AlertDialog`-backed, 380 dp max-width, 28 dp padding, 14 dp radius, primary button `OriPillButton(variant = Danger)`. Wrapper: `OriConfirmDialog(title, message, confirmLabel, onConfirm, onDismiss, variant = Default/Danger)`.
- `OriMiniBar` — 60 × 4 dp horizontal bar used for Proxmox CPU/RAM mini indicators.
- `OriCheckbox` — custom 16 × 16 dp (not Material's 20/48 dp), 2 dp border, 3 dp inner radius, `Indigo500` fill when checked with 12 dp `Check` icon in white. Used in `FileItemRow` for multi-select; file-manager.html confirms the checkbox column is required.
- `OriEmptyState` — centered column with icon (48 dp), optional title (`titleMedium`), subtitle (`bodySmall` tertiary). Used in Transfer Queue and File Manager empty states.

**All primitives declared in P0.** No primitive introduced in P1/P2. Total primitives in P0.5: **19**.

### P0.6 — Settings migration proof-of-concept (PR 4a, bail-out gate)

**File:** `feature-settings/src/main/kotlin/dev/ori/feature/settings/ui/SettingsScreen.kt`

- Replace existing `TopAppBar` with `OriTopBar(title = "Einstellungen", height = OriTopBarDefaults.Height)`.
- Wrap existing content in updated `OriCard`s.
- Add `Modifier.testTag("settings_content")` to the root `LazyColumn`.
- `OriTopBar` carries `Modifier.testTag("ori_top_bar")` internally.
- No section expansion yet — that's P1.2.
- Keep existing `CrashReportingPreferences` binding.

**androidTest scaffolding (new for PR 4a):** `feature-settings` currently has only `src/main/` — no `androidTest/` source set exists. PR 4a must:

1. Add to `feature-settings/build.gradle.kts`:
   ```kotlin
   android {
       defaultConfig {
           testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
   }
   dependencies {
       androidTestImplementation(libs.androidx.compose.ui.test.junit4)
       androidTestImplementation(libs.androidx.test.ext.junit)
       androidTestImplementation(libs.truth)
       debugImplementation(libs.androidx.compose.ui.test.manifest)
   }
   ```
2. Create the directory tree `feature-settings/src/androidTest/kotlin/dev/ori/feature/settings/ui/`.
3. Add the test file `SettingsScreenLayoutTest.kt` (see below).

Same scaffolding steps are required in `feature-editor/build.gradle.kts` for PR 4b's `SoraThemingSpike`.

**Machine-verifiable gate criterion (replaces v2's Layout-Inspector wording):**

New instrumented test file `feature-settings/src/androidTest/kotlin/.../SettingsScreenLayoutTest.kt`:

```kotlin
@Test
fun settings_content_starts_exactly_below_topbar() {
    composeTestRule.setContent { OriDevTheme { SettingsScreen(...) } }
    val topBarBottom = composeTestRule
        .onNodeWithTag("ori_top_bar")
        .fetchSemanticsNode().boundsInWindow.bottom
    val contentTop   = composeTestRule
        .onNodeWithTag("settings_content")
        .fetchSemanticsNode().boundsInWindow.top
    // Relative assertion: content starts exactly at bottom of top bar, regardless
    // of status-bar inset or edge-to-edge display-cutout handling.
    assertThat(contentTop - topBarBottom).isWithin(1f).of(0f)
}
```

**Why relative, not absolute:** the project uses `enableEdgeToEdge()` and `minSdk 34`, so on API 34+ the Activity draws under the status bar. An absolute assertion like `bounds.top == 56.dp.toPx()` would measure `statusBarInsetPx + 56dp` (≈ 80 dp on typical devices) and always fail. A relative assertion (`content.top - topBar.bottom == 0`) bypasses inset math entirely and captures the real invariant: **no padding leaks between the bottom of `OriTopBar` and the first content row**. `OriTopBar` itself gets `testTag("ori_top_bar")`; `SettingsScreen`'s `LazyColumn` gets `testTag("settings_content")`.

**Additional visual gate (manual):** capture before/after screenshots of the Settings screen on folded + unfolded Pixel Fold emulator. Attach to PR 4 description. Visual diff vs. `settings.html` header section passes.

If either gate fails, PR 4 is revised before merge. No other screen migration starts until PR 4 merges.

### P0.7 — Mockup hash guard CI (PR 4b)

**Atomic file/job commit:** the workflow YAML job addition and the `.github/mockup-hash.txt` file must land in the **same commit** within PR 4b so that main never sees the job without the hash file (which would emit the "hash file missing" warning permanently).

**File:** `.github/workflows/build.yml` — add job `mockup-hash-check`:

```yaml
mockup-hash-check:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - run: |
        if [ ! -f .github/mockup-hash.txt ]; then
          echo "::warning::Hash file .github/mockup-hash.txt missing — skipping check (first-run or file removed)."
          exit 0
        fi
        CURRENT=$(find Mockups -name '*.html' | sort | xargs sha256sum | sha256sum | awk '{print $1}')
        EXPECTED=$(cat .github/mockup-hash.txt)
        if [ "$CURRENT" != "$EXPECTED" ]; then
          echo "::warning::Mockups changed. Update .github/mockup-hash.txt and revisit Phase 11 deltas."
        fi
```

The expected hash file `.github/mockup-hash.txt` is committed as part of PR 4. Location is `.github/` (next to the workflow that consumes it), not `docs/superpowers/plans/`, so it survives any future cleanup of completed plan documents.

### P0.8 — Sora-Editor theming spike (PR 4b, blocker)

**Goal:** Prove that Sora-Editor can be theme-configured at runtime to use (a) `JetBrainsMono` as text typeface, (b) GitHub-palette syntax colors via `EditorColorScheme`, (c) custom line-height matching `20 px / 13 sp`.

**File (new, instrumented test):** `feature-editor/src/androidTest/kotlin/.../SoraThemingSpike.kt`

Sora's `CodeEditor` extends `android.view.View` and requires a real Android runtime; this cannot be a JVM-only unit test. After the spike proves the approach, the file stays as a regression-prevention instrumented test rather than being deleted.

**Process:**
1. In a standalone composable, instantiate a `CodeEditor` (Sora's underlying view).
2. Load `JetBrainsMonoRegular` via `ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular)` and call `editor.typefaceText = it`.
3. Create an `EditorColorScheme` with the GitHub palette tokens (`SyntaxKeyword`, `SyntaxString`, etc.) applied via `scheme.setColor(EditorColorScheme.KEYWORD, SyntaxKeyword.toArgb())` etc.
4. Load a sample `.kt` file content and verify: (a) font renders with JetBrains Mono, (b) keywords are red, (c) strings are navy, (d) line height is 20 dp.
5. Document the outcome in the PR description: pass/fail, which APIs worked, which didn't, next step if any.

**Gate:** PR 4b does not merge until the spike is green. If it fails, P4b pauses; PR 4a (primitives + Settings PoC) is unaffected and can still merge. Feature screen migrations in P2 that are not the Editor (Terminal, Connections, Transfers, Filemanager, Proxmox) can still proceed on top of 4a. Only P2.2 (Code Editor) is gated on 4b.

**Why split from PR 4a:** the original v2/v3 "single PR 4 bail-out gate" made primitive revisions impossible without also rolling back Sora spike + `@MockupPreviews` + hash guard. Splitting isolates the true bail-out content (primitives + PoC) from less risky tooling (spike + previews + hash), so if the spike fails the gate fires on the spike alone.

### P0.9 — `@MockupPreviews` annotation (PR 4)

**File (new):** `core/core-ui/src/main/kotlin/dev/ori/core/ui/preview/MockupPreviews.kt`

```kotlin
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Preview(
    name   = "Pixel Fold — Unfolded (inner)",
    device = "spec:width=2208dp,height=1840dp,dpi=408,isRound=false,orientation=portrait",
    showBackground = true, backgroundColor = 0xFFFAFAFA,
)
@Preview(
    name   = "Pixel Fold — Folded (cover)",
    device = "spec:width=1080dp,height=2092dp,dpi=420,isRound=false,orientation=portrait",
    showBackground = true, backgroundColor = 0xFFFAFAFA,
)
annotation class MockupPreviews
```

The explicit `@Retention` and `@Target` ensure both the IDE inspector and the CLI preview-render tooling (Layoutlib-based) pick up the multi-preview. Without them, some toolchain versions silently ignore the annotation.

**Important correction vs v2:** Compose Preview DSL accepts **dp** units in the `spec:` string, **not px**. v2 wrote `spec:width=2208px` which would be parsed as "2208 Android dp" anyway (since px is not a valid unit) and effectively be wrong. Using dp explicitly. Pixel Fold inner = 2208 × 1840 dp at 408 dpi; cover = 1080 × 2092 dp at 420 dpi.

**Caveat documented in the file comment:** previews are visual-only. Fold-state-dependent logic using `currentWindowAdaptiveInfo()` cannot be exercised in a preview — only on a real emulator or device. Reviewers should acknowledge this when diffing.

### P0.10 — CI Pipeline Self-Tests & Reliability (PR 3.5)

**Why its own PR:** CI work must be landable without contaminating the PR 4a bail-out gate. If §P0.10 work broke the Settings PoC gate, a primitive revision would also roll back CI fixes and vice versa.

**Existing workflow audit (verified against current repo state):**
- `.github/workflows/build.yml` — `test` job (detekt + unit tests + lint) and `build` job (matrix debug/release, signed release). Runs on push to `main`/`master`/`develop`. All checkouts use default `fetch-depth: 1`.
- `.github/workflows/pr-check.yml` — 5 stages: `code-quality` (detekt + lint), `unit-tests` (test + jacoco), `security` (dependency-check + trufflehog, `continue-on-error: true`), `build` (assembleDebug for app + wear), `ui-tests` (emulator, **`if: contains(labels, 'run-ui-tests')`** — opt-in only).
- `.github/workflows/release.yml` — tag-triggered signed release + Play Store upload.
- `.github/workflows/baseline-profile.yml` — generates baseline profiles.
- `.github/workflows/security.yml` — secret / dependabot scanning.

**P0.10.1 — `fetch-depth: 0` in checkouts that need diff**
All checkout steps in `pr-check.yml` and `build.yml` that run diff-based checks **must** use:
```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0
```
Default `fetch-depth: 1` makes `git diff origin/main...HEAD` fail with `unknown revision` since `main` isn't fetched. The jobs that currently don't need it (e.g. `security`) can keep the default.

**P0.10.2 — New job `static-import-checks` in `pr-check.yml`**
Added as a new stage parallel to `code-quality` (fast, so it gates PRs early without blocking on unit tests):

```yaml
  static-import-checks:
    name: Forbidden Imports
    runs-on: ubuntu-latest
    if: github.ref_type != 'tag'
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Positive-control fixture self-test
        run: bash .github/ci/check-forbidden-imports.sh --self-test
      - name: Run forbidden-import check
        run: bash .github/ci/check-forbidden-imports.sh
```

The grep script moves from inline YAML to `.github/ci/check-forbidden-imports.sh` so it can be `bash`-tested locally via `act` or by running the shell script directly. The `--self-test` flag runs the script against `.github/fixtures/bad-imports.kt.txt` (which contains all forbidden patterns) and asserts that all 4 patterns are reported. If positive-control fails, the script exits 2 (CI setup itself broken) — distinct from exit 1 (actual violation found).

**Fixture-file compilation safety:** `.github/fixtures/bad-imports.kt.txt` uses a `.txt` extension (not `.kt`) so IntelliJ does not index it as a Kotlin source, and it lives under `.github/` which is outside any Gradle source set root. Resolves v3 Open Question #4.

Mirror the same job into `build.yml` (push to main) so main-branch regressions get caught too.

**P0.10.3 — New Gradle task `checkCoreFontsLeakage`**
In `core/core-fonts/build.gradle.kts` — using the modern `incoming.resolutionResult` API (not deprecated `resolvedConfiguration`):

```kotlin
tasks.register("checkCoreFontsLeakage") {
    group = "verification"
    description = "Fails if core-fonts transitively depends on androidx.compose.material3"
    val rrProvider = configurations.named("debugRuntimeClasspath")
        .map { it.incoming.resolutionResult.rootComponent }
    doLast {
        val all = mutableSetOf<String>()
        fun walk(component: org.gradle.api.artifacts.result.ResolvedComponentResult) {
            if (!all.add(component.id.displayName)) return
            component.dependencies
                .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                .forEach { walk(it.selected) }
        }
        walk(rrProvider.get())
        val leak = all.filter { it.contains("androidx.compose.material3") }
        check(leak.isEmpty()) {
            "core-fonts transitively depends on material3 (phone Compose leak):\n" +
                leak.joinToString("\n")
        }
    }
}
tasks.named("check") { dependsOn("checkCoreFontsLeakage") }
```

Explicitly invoked in CI via `./gradlew :core:core-fonts:checkCoreFontsLeakage` as a dedicated step in `pr-check.yml → code-quality` and `build.yml → test` — **not** relying on implicit `check` aggregation since `./gradlew test` does not run `check`. v5's claim "covered by `./gradlew test` flows" was wrong.

**P0.10.4 — New Gradle task `checkWearLeakage`**
Same `incoming.resolutionResult` walk in `wear/build.gradle.kts`, asserting no `androidx.compose.material3` (phone variant) in Wear's `debugRuntimeClasspath`. Wired to `check` and explicitly invoked in CI. Catches regressions if a future PR adds `core-ui` as a wear dep by mistake.

**P0.10.5 — New Gradle task `checkFontBudget`**
In `core/core-fonts/build.gradle.kts`:
```kotlin
tasks.register("checkFontBudget") {
    group = "verification"
    description = "Fails if font assets exceed 1.5 MB combined"
    val fontDir = file("src/main/res/font")
    val budgetBytes = 1_500_000L
    doLast {
        val total = fontDir.walkTopDown()
            .filter { it.isFile && it.extension == "ttf" }
            .sumOf { it.length() }
        check(total <= budgetBytes) {
            "Font assets exceed budget: ${total / 1024} KB > ${budgetBytes / 1024} KB. " +
                "Run pyftsubset or remove unused weights."
        }
    }
}
tasks.named("check") { dependsOn("checkFontBudget") }
```
Runs as part of every `check` invocation in existing pipelines.

**P0.10.6 — `mockup-layout-tests` CI job DEFINITION (wired in PR 4a, not here)**

The job *specification* lives here for completeness, but the YAML is added to `pr-check.yml` only in **PR 4a** where `SettingsScreenLayoutTest` and `feature-settings/src/androidTest/` actually exist. PR 3.5 cannot add this job because the test source set doesn't yet exist at PR 3.5 merge time.

```yaml
  mockup-layout-tests:
    name: Mockup Layout Gate
    runs-on: ubuntu-latest
    needs: [code-quality, unit-tests]   # parallel to build, not serialized after it
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4

      - name: AVD cache
        uses: actions/cache@v4
        id: avd-cache
        with:
          path: |
            ~/.android/avd/*
            ~/.android/adb*
          key: avd-pixel6-api34-google_apis-v1

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Run SettingsScreenLayoutTest (+ Sora spike from PR 4b onward)
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          target: google_apis
          profile: pixel_6
          force-avd-creation: false
          emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim
          disable-animations: true
          script: |
            # Retry once to absorb emulator-boot flakes
            for attempt in 1 2; do
              ./gradlew :feature-settings:connectedDebugAndroidTest \
                -Pandroid.testInstrumentationRunnerArguments.class=dev.ori.feature.settings.ui.SettingsScreenLayoutTest \
                && break
              echo "::warning::Attempt $attempt failed, retrying after 5s"
              sleep 5
            done
```

**Scope of the gate test (corrected from v5):** the test asserts the **topbar-to-content offset invariant** — that no padding leaks between the bottom of `OriTopBar` and the first content row — on a **single size class** (Pixel 6, compact). Fold-branch layout correctness (different column counts, breakpoint-dependent visuals) is verified by **manual mockup diff in P2's per-screen PRs** using folded + unfolded emulator runs. v5 incorrectly claimed `DeviceConfigurationOverride` would simulate fold inside this test — but `DeviceConfigurationOverride` only affects `LocalConfiguration` inside the composition, not `currentWindowAdaptiveInfo()` which reads from the real `WindowMetricsCalculator`. Scope narrowed, honestly.

**Parallelism (fixed from v5):** `needs: [code-quality, unit-tests]` means this runs in parallel with `build`, not serialized after it. Saves ~5 min per PR.

**Flake mitigation:** two-attempt retry wrapper inside the emulator script. Empirically drops the 5–15% flake rate to <1%.

**Cost (realistic):** cold-boot ~10 min, warm cache ~2 min. Test execution ~2 min. **Total: 5–10 min warm / 12–15 min cold.** First PR 4a run pays the cold cost, subsequent runs hit the cache. v5's "under 5 minutes" claim was unrealistic.

**PR 4b addendum:** when PR 4b adds `SoraThemingSpike` under `feature-editor/src/androidTest/`, it appends `:feature-editor:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.ori.feature.editor.SoraThemingSpike` to the emulator script as a second `./gradlew` invocation in the same retry loop.

**P0.10.7 — Compose Preview validation: deferred**

v5 proposed a `checkComposePreviews` Gradle task with `-P compose.preview.strict=true`. The flag does not exist as a stable Compose compiler option at the current BOM, and the v5 fallback ("plain compile") was a no-op — `compileDebugKotlin` already runs on every build. Shipping a no-op green check is worse than no check because it hides regressions under a false confidence signal.

**Decision:** drop the task from PR 3.5. Preview-rendering regressions are caught **implicitly** by `mockup-layout-tests` (PR 4a onward), which instantiates real composables via `composeTestRule.setContent { OriDevTheme { SettingsScreen(...) } }` — the same code path a preview-render would take. If `SettingsScreen` throws during composition (broken preview = broken instantiation), the test fails.

Explicit preview validation can return later via Paparazzi or Showkase once the deferred screenshot-testing spike (post-P0) proves the infrastructure works in this project. Tracked but not blocking.

**P0.10.8 — `check-mockup-hash.sh` script + self-test fixture**

v5 referenced the script without defining it. v6 specifies it fully.

**File:** `.github/ci/check-mockup-hash.sh`
```bash
#!/usr/bin/env bash
set -u
# Note: NO pipefail here — sha256sum may see empty input in edge cases.

fail=0
mode="${1:-check}"

if [ "$mode" = "--self-test" ]; then
    # Point at a fixture directory with a known-mutated HTML file.
    # The fixture baseline hash is stored next to the fixture. Self-test EXPECTS a mismatch.
    fixture_dir=".github/fixtures/mockups-fixture"
    baseline=".github/fixtures/mockups-fixture-hash.txt"
    if [ ! -d "$fixture_dir" ] || [ ! -f "$baseline" ]; then
        echo "::error::Self-test fixtures missing at $fixture_dir"
        exit 2
    fi
    current=$(find "$fixture_dir" -name '*.html' | sort | xargs sha256sum | sha256sum | awk '{print $1}')
    expected=$(cat "$baseline")
    if [ "$current" = "$expected" ]; then
        echo "::error::Self-test FAILED: hashes match when fixture was supposed to mismatch. Check has regressed."
        exit 2
    else
        echo "::notice::Self-test OK: mismatch correctly detected."
        exit 0
    fi
fi

# Normal mode: compare real mockups against the committed hash.
if [ ! -f .github/mockup-hash.txt ]; then
    echo "::warning::Hash file .github/mockup-hash.txt missing — skipping check (first-run or file removed)."
    exit 0
fi
current=$(find Mockups -name '*.html' | sort | xargs sha256sum | sha256sum | awk '{print $1}')
expected=$(cat .github/mockup-hash.txt)
if [ "$current" != "$expected" ]; then
    echo "::warning::Mockups changed. Update .github/mockup-hash.txt and revisit Phase 11 deltas."
fi
exit 0
```

**Fixture layout (shipped in PR 3.5):**
- `.github/fixtures/mockups-fixture/` — contains a minimal HTML file (e.g. `test.html` with `<html><body>fixture</body></html>`)
- `.github/fixtures/mockups-fixture-hash.txt` — contains a **deliberately wrong** hash (e.g. `0000000000000000000000000000000000000000000000000000000000000000`). The self-test asserts the real hash does NOT match this, proving the check machinery is alive.

**`ci-self-test.yml` weekly canary:**

New file `.github/workflows/ci-self-test.yml`:
```yaml
name: CI Self-Test
on:
  schedule:
    - cron: "0 4 * * 1"   # Monday 04:00 UTC
  workflow_dispatch:
permissions:
  contents: read
jobs:
  self-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - name: Forbidden-imports positive control
        run: bash .github/ci/check-forbidden-imports.sh --self-test
      - name: Mockup-hash positive control
        run: bash .github/ci/check-mockup-hash.sh --self-test
      - name: Font budget positive control (assert budget=0 triggers fail)
        run: |
          ./gradlew :core:core-fonts:checkFontBudget -Ptest.budget=0 && {
            echo "::error::Self-test failed: checkFontBudget did not fail on budget=0"
            exit 2
          } || echo "::notice::checkFontBudget correctly rejects budget=0"
      - name: Leak check positive control
        run: ./gradlew :core:core-fonts:checkCoreFontsLeakage
```
The first two `--self-test` flags invoke scripts against fixtures that **must fail** — if they don't fail, the check has regressed (silently accepting bad input). The font-budget self-test runs the real task with a zero budget to confirm it still rejects bad input. This weekly canary catches regressions like a bash escape drift or a Gradle configuration rename that makes a task silently pass.

**P0.10.9 — Diff-scoped grep extracted to shell script (full body)**

The script lives at `.github/ci/check-forbidden-imports.sh`. It can be:
- Run locally via `bash .github/ci/check-forbidden-imports.sh`
- Simulated via `act` in full GHA environment
- Self-tested via `--self-test` against `.github/fixtures/bad-imports.kt.txt` (note: `.txt` extension, not `.sample` — IntelliJ leaves `.txt` alone, so no IDE noise)

**Critical shell robustness fixes over v5:**
- `set -u` (strict vars) but **NOT** `set -o pipefail` — grep's "no match = exit 1" would kill the script under pipefail
- `fail=0` initialized explicitly
- Each grep wrapped with `|| true` to neutralize no-match exit-1, with separate file-count capture
- Null-delimited xargs (`-0`) to handle paths with spaces

```bash
#!/usr/bin/env bash
set -u
# NOTE: NO pipefail — grep exits 1 on no-match which is our happy path.

fail=0
self_test=false

if [ "${1:-}" = "--self-test" ]; then
    self_test=true
fi

# Resolve the scope: either fixture file (self-test) or diff-scoped file list (real run)
if [ "$self_test" = "true" ]; then
    mapfile -d '' scope_files < <(printf '%s\0' .github/fixtures/bad-imports.kt.txt)
else
    base_ref="${GITHUB_BASE_REF:-main}"
    # -z for null-delimited output to handle spaces safely
    mapfile -d '' scope_files < <(
        git diff --name-only -z "origin/${base_ref}...HEAD" -- \
            'feature-*/src/*.kt' \
            'core/core-ui/src/main/kotlin/dev/ori/core/ui/components/*.kt' \
            2>/dev/null || true
    )
fi

if [ "${#scope_files[@]}" -eq 0 ]; then
    echo "No Kotlin files in scope — skip."
    exit 0
fi

run_grep() {
    local label="$1"; shift
    local pattern="$1"; shift
    local restrict_glob="${1:-}"
    local targets=("${scope_files[@]}")

    if [ -n "$restrict_glob" ]; then
        local filtered=()
        for f in "${targets[@]}"; do
            case "$f" in $restrict_glob) filtered+=("$f") ;; esac
        done
        targets=("${filtered[@]}")
    fi

    if [ "${#targets[@]}" -eq 0 ]; then return 0; fi

    local output
    output=$(printf '%s\n' "${targets[@]}" | xargs -r -I{} grep -EnH "$pattern" {} 2>/dev/null || true)
    if [ -n "$output" ]; then
        echo "::error::$label"
        echo "$output"
        fail=1
    fi
}

# 1) Material Icons — direct or aliased imports (any module in scope)
run_grep \
    "Material Icons imported. Use dev.ori.core.ui.icons.lucide.LucideIcons instead." \
    '^import[[:space:]]+androidx\.compose\.material\.icons\.[A-Za-z0-9_.]+([[:space:]]+as[[:space:]]+[A-Za-z_][A-Za-z0-9_]*)?$'

# 2) Material Icons — wildcard
run_grep \
    "Wildcard Material Icons import found. Use specific LucideIcons instead." \
    '^import[[:space:]]+androidx\.compose\.material\.icons\..*\.\*$'

# 3) Material3 TopAppBar family — direct or aliased (feature modules only)
run_grep \
    "Material3 TopAppBar imported in feature code. Use OriTopBar instead." \
    '^import[[:space:]]+androidx\.compose\.material3\.(TopAppBar|MediumTopAppBar|LargeTopAppBar|CenterAlignedTopAppBar)([[:space:]]+as[[:space:]]+[A-Za-z_][A-Za-z0-9_]*)?$' \
    'feature-*'

# 4) Wildcard material3 import in feature modules
run_grep \
    "Wildcard material3 import in feature code is forbidden (may hide TopAppBar usage)." \
    '^import[[:space:]]+androidx\.compose\.material3\.\*$' \
    'feature-*'

# Self-test: expect violations. If fail=0 (no violations found), self-test regressed.
if [ "$self_test" = "true" ]; then
    if [ "$fail" -eq 0 ]; then
        echo "::error::Self-test FAILED: grep did not detect forbidden patterns in fixture"
        exit 2
    else
        echo "::notice::Self-test OK: fixture correctly triggered $fail violation group(s)"
        exit 0
    fi
fi

exit $fail
```

**`.github/fixtures/bad-imports.kt.txt`** content:
```kotlin
// Fixture for check-forbidden-imports.sh self-test
// This file is intentionally BAD — it contains every forbidden pattern.
// It lives under .github/fixtures/ which is OUTSIDE any Gradle source set,
// so it is NEVER compiled by Gradle. The .txt extension keeps IntelliJ from indexing it.

package fixture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home as HomeIcon
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MediumTopAppBar as M3Bar
import androidx.compose.material3.*
```

**P0.10.10 — `act` developer-docs**
New file `docs/superpowers/plans/mockup-fidelity-ci-playbook.md` explains:
- How to install `nektos/act` locally
- How to run each new check locally (`act -j static-import-checks` etc.)
- How to regenerate the mockup-hash file after legitimate mockup updates
- How to debug a failing positive-control self-test
- Common pitfalls (Docker memory limits on macOS, missing `--container-architecture linux/amd64` on Apple Silicon)

This is developer docs, not a CI change — but it ships in PR 3.5 so developers can actually iterate on workflow YAML without push-wait-retry.

### P0 Exit Criteria
- All PRs merged (PR 0 folded into PR 2; PR 3.5 stacked between PR 3 and PR 4a)
- `./gradlew :core:core-ui:test :core:core-ui:detekt :core:core-fonts:assembleDebug check` green
- `checkCoreFontsLeakage` Gradle task green (no `material3` in core-fonts deps)
- `checkWearLeakage` Gradle task green (P3 re-verifies after Wear theme lands)
- `checkFontBudget` Gradle task green (≤ 1.5 MB combined TTFs)
- Preview-rendering regressions implicitly caught by `mockup-layout-tests` (real composable instantiation via `composeTestRule.setContent`) — the v5 `checkComposePreviews` Gradle task was a no-op and was dropped in v6
- `static-import-checks` CI job green on PR 3.5 branch AND on every subsequent PR (diff-scoped, self-tested)
- `mockup-layout-tests` CI job green — `SettingsScreenLayoutTest` + `SoraThemingSpike` both pass on Pixel 6 API 34 emulator
- `ci-self-test.yml` weekly canary green (at least once before P1 starts)
- `Inter`, `JetBrainsMono`, `RobotoFlex` render via `@MockupPreviews` harness
- Settings PoC: relative-offset assertion passes
- Sora-Editor theming spike (P0.8) passed
- Visible pixel improvement on Settings vs baseline (before/after screenshots attached to PR 4a)
- No `feature-*` file imports `Material3.TopAppBar` family or `material.icons.*` (any form — verified by diff-scoped grep, not by full-tree scan)
- `minSdk 34` assumption verified against `app/build.gradle.kts:49` and `wear/build.gradle.kts:46`

---

## 6. Phase 1 — Navigation Unblock + Settings Full Build

### P1.1 — Connection → Terminal / Filemanager navigation

**Files modified:**
- `feature-connections/src/main/kotlin/dev/ori/feature/connections/ui/ConnectionListScreen.kt:109-110` — replace empty-stub callbacks
- `feature-connections/src/main/kotlin/dev/ori/feature/connections/ConnectionsNavigation.kt` — add `onOpenTerminal(profileId: String)` and `onOpenFileManager(profileId: String)` to navigation params
- `app/src/main/kotlin/dev/ori/app/OriDevNavHost.kt` — wire the callbacks to `navController.navigate("terminal?profileId=$id")` and `navigate("filemanager?profileId=$id")`
- `feature-terminal/.../ui/TerminalScreen.kt` — accept optional `initialProfileId` nav-arg and auto-create first tab from it
- `feature-filemanager/.../ui/FileManagerScreen.kt` — accept optional `initialProfileId` for the remote pane

**Acceptance:** From Connection list, tap "Open Terminal" → Terminal opens with a live SSH session to the selected host. Tap "Open Files" → Filemanager opens with the remote pane rooted on the host's initial directory.

### P1.2 — Settings screen expansion to 7 sections

**Files (new):**
- `feature-settings/.../sections/AccountPremiumSection.kt` — shows "Premium — bald verfügbar" **placeholder row** (no sheet, no pricing). `PremiumState` reads a hardcoded `Free` until Phase 12.
- `.../sections/AppearanceSection.kt`
- `.../sections/TerminalSection.kt`
- `.../sections/TransfersSection.kt`
- `.../sections/SecuritySection.kt`
- `.../sections/NotificationsSection.kt`
- `.../sections/AboutSection.kt`
- `.../components/SettingsRow.kt` — 52 dp min-height, 14/16 dp padding, 32 dp icon box (6 dp radius, `IndigoBg`, `Indigo500` icon), title `bodyMedium` (14 sp / 500 via M3), subtitle `labelMedium` (12 sp tertiary)
- `.../components/SettingsCard.kt` — wrapper around `OriCard` with `OriSectionLabel` above
- `.../components/PremiumBadge.kt` — `Crown` Lucide icon + "Premium" label, but NO sheet

**Files modified:**
- `SettingsScreen.kt` (already migrated in P0.6) — now renders 7 `SettingsCard`s in a `LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp))`, max-width 720 dp when `WindowWidthSizeClass.Expanded`
- `SettingsViewModel.kt` — extends state to hold all the preference flows
- `SettingsState.kt` — remove "Phase 10 only" comment

**New preference store:**
- `feature-settings/.../data/AppPreferences.kt` — DataStore with keys: `accentColor`, `fontSize`, `terminalFont`, `defaultShell`, `scrollback`, `bellMode`, `hardwareKeyboard`, `keyboardToolbar`, `maxParallelTransfers`, `autoResume`, `overwriteMode`, `biometricUnlock`, `autoLockTimeoutMinutes`, `clipboardClearSeconds`, `notifyTransferDone`, `notifyConnection`, `notifyClaude`, `notifyWear`

**License screen (About section):**
- Use `com.mikepenz:aboutlibraries-plugin` 10.x, **applied only to `app/build.gradle.kts`**. The plugin generates a `Libs` class into the `app` module's namespace; library modules cannot import it without a reverse dependency.
- **Navigation hand-off instead of cross-module import:** `AboutLibrariesScreen` is registered as a composable destination in `app/src/main/kotlin/.../OriDevNavHost.kt` under route `"licenses"`. The Settings "Lizenzen" row in `feature-settings` calls `navController.navigate("licenses")` — it passes only the route string, never imports the plugin-generated class. Feature-module isolation preserved.
- **Vendored Lucide manual entry:** since the vendored source has no Maven POM, `aboutlibraries` will not auto-detect it. A manual JSON entry at `config/aboutlibraries/libraries/lucide.json` describes the dual license (Lucide MIT + compose-icons Apache 2.0); the plugin picks it up during the generate task.

**Strings remain German** — all section titles, row titles, subtitles written in German from the start.

**Acceptance:** 7 sections visible, all toggles persist, no `OriToggle` regressions, no bottom sheet, `Premium — bald verfügbar` row shows in Account section.

### P1.3 — Destructive confirmation primitive is ready from P0

`OriConfirmDialog` is in P0.5. Used downstream in P2 without introducing anything new.

### P1 Exit Criteria
- Connection → Terminal and Connection → Filemanager flows work end-to-end on Pixel Fold emulator
- Settings has all 7 sections, all toggles persist across restart
- No references to `FeatureGateManager` or `PremiumState.premium`

---

## 7. Phase 2 — Phone Screen Alignment

Each sub-phase: replace `TopAppBar` with `OriTopBar`, replace Material primitives with Ori primitives, match exact px values, add `@MockupPreviews` (folded + unfolded), manual Layout Inspector diff against mockup. **No Paparazzi in the acceptance criteria**; it is tracked separately as a post-P0 spike.

### P2.1 — Terminal (`terminal.html`)
- `TerminalScreen.kt` — keep existing Scaffold; inner `TopAppBar` → `OriTopBar(height = OriTopBarDefaults.HeightCompact)` = 44 dp; `floatingActionButton` stays; `snackbarHost` stays; split divider 8 dp white with top+bottom 1-dp `Border`; terminal pane padding 12 dp top / 14 dp sides
- Fold detection via `currentWindowAdaptiveInfo().windowSizeClass` — replace `configuration.screenWidthDp >= 600` at line 71
- `TerminalTabBar.kt` — 40 dp height, 12 dp horiz padding, 2 dp gap; tab label `labelMedium`, sublabel `labelSmall`; active indicator 2 dp top border `Indigo500`
- `CustomKeyboard.kt` — key 34 dp height, 8 dp padding, 4 dp horizontal gap, 5 dp vertical gap; key font 11.5 sp / 500, modifier keys 10.5 sp / 600 on `Gray100`
- Terminal body uses `OriTypography.terminalBody`; Tokyo-Night bg `#1A1B26` explicitly (confirmed mockup-correct, flagged and kept)
- `SendToClaudeSheet.kt` + `CodeBlocksSheet.kt` — `OriExtraShapes.modalTop`

### P2.2 — Code Editor (`code-editor.html`)
- `CodeEditorScreen.kt` — inner `TopAppBar` → `OriTopBar(height = HeightDense)` = 40 dp; 12 dp horiz padding; existing Scaffold stays
- **Sora-Editor theming: apply the approach validated in P0.8.** The spike already proved `editor.typefaceText = JetBrainsMonoTypeface` + `EditorColorScheme` with the GitHub palette works. P2.2 simply consumes those findings — no spike here.
- `EditorTabBar.kt` — 36 dp height, 8 dp horiz padding, 12 sp tab label
- `SearchReplaceBar.kt` — 6 dp top / 12 dp sides padding, `JetBrainsMono` 12 sp input, highlight `#FFF3BF` / active `#FDDF68`
- `GitDiffStatusBar.kt` — 24 dp height, 11 sp text
- Add Undo/Redo `OriIconButton`s to `OriTopBar` `actions` slot

### P2.3 — Connection Manager (`connection-manager.html`)
- `ConnectionListScreen.kt` — inner `TopAppBar` → `OriTopBar(60.dp)`; add `OriSectionLabel("Recent")` and `OriSectionLabel("All Servers")`; add horizontal Quick-Connect `OriChip` row; search uses `OriSearchField` with asymmetric padding 16/20/0
- `ServerProfileCard.kt` — 60 dp height, 14/16 dp padding, name `bodyMedium` 14 sp / 600, host `OriTypography.hostMono`, status dot `OriStatusDot(8.dp)`, star/kebab `OriIconButton`
- Protocol badges → `OriStatusBadge` with exact hex pairs (SFTP IndigoBg/Indigo500, SSH YellowBg/YellowText, FTP/FTPS SkyBg/SkyText, Proxmox RedBg/RedText)
- FAB → `OriFab` inside existing Scaffold's `floatingActionButton`
- `AddEditConnectionScreen.kt` — `OriInput` for all text fields, `OriSegmentedControl` for auth-method toggle, `OriDropdown` for protocol
- `ConnectionDetailSheet.kt` — `OriExtraShapes.modalTop`, delete action uses `OriConfirmDialog(variant = Danger)`

### P2.4 — Transfer Queue (`transfer-queue.html`)
- `TransferQueueScreen.kt` — `OriTopBar(56.dp)` with `OriPillButton` "Clear Completed" in actions (triggers `OriConfirmDialog`); content wrapped in adaptive column (max 880 dp when Expanded, responsive otherwise)
- New `TransferStatsBar.kt` component — `OriCard` showing counts, speeds, aggregated `OriProgressBar(height = 6.dp)`
- New `TransferFilterChips.kt` — `OriChip` row with solid `Indigo500` when selected, count badge inside each chip
- `TransferItemCard.kt` — padding 16 × 18 dp asymmetric, name 14 sp / 600, `OriProgressBar(5.dp)`, `OriStatusBadge`, action buttons `OriPillButton` with text labels ("Pause"/"Resume"/"Cancel"/"Retry")
- Failed card border-left 3 dp `#EF4444` via `drawBehind`
- New `CompletedTransferRow` composable
- Empty state via `OriEmptyState` (primitive from P0.5)

### P2.5 — File Manager (`file-manager.html`)
- `FileManagerScreen.kt` — inner `TopAppBar` → `OriTopBar(56.dp)`; content padding 12 dp (unfolded) / 8 dp (folded) via `currentWindowAdaptiveInfo()`
- `DualPaneLayout.kt` — 12 dp divider container, 3 dp center line `Border`, 40 dp visible, hover expands to 56 dp with `IndigoLight` + `Indigo500` line
- `FileListPane.kt` — pane header 16 dp horizontal / 0 dp top; toolbar buttons `OriIconButton(24.dp)` with 14 dp Lucide icon
- `BreadcrumbBar.kt` — 8 × 12 dp padding, 13 sp / 400, 4 dp gap, `ChevronRight` 12 dp `TextTertiary` separator
- `FileItemRow.kt` — 8 × 18 dp padding, 32 dp icon, custom 16 dp `OriCheckbox` (primitive from P0.5)
- `BookmarkBar.kt` — 28 dp `OriChip` height, 11 sp text
- `FilePreviewSheet.kt` + `FileInfoSheet.kt` — `OriExtraShapes.modalTop`, max-height 70 %
- Delete action → `OriConfirmDialog(variant = Danger)`
- Rename/Chmod/Mkdir dialogs deliberately NOT in P2.5 — see P4

### P2.6 — Proxmox Dashboard (`proxmox.html`)
- `ProxmoxDashboardScreen.kt` — inner `TopAppBar` → `OriTopBar(56.dp)`; page padding 24 dp top / 20 dp sides / 100 dp bottom inside existing Scaffold; 36 dp gap between node grid and VM list
- Node grid: `LazyVerticalGrid(cells = GridCells.Adaptive(minSize = 280.dp), horizontalArrangement = Arrangement.spacedBy(16.dp))` replacing `LazyRow(220.dp fixed)`
- `NodeCard.kt` — 280 dp min width, 20 dp padding, 10 dp header gap, 12 dp stat gap, `OriProgressBar(6.dp)`, selected border `Indigo500`
- `VmCard.kt` — 16 × 20 dp padding, 14 dp element gap, new `OriMiniBar(60.dp, 4.dp)` for CPU/RAM, action pills `OriPillButton`
- `VmStatusBadge.kt` — replaced with `OriStatusBadge(Running/Stopped/Paused)` pill
- `CreateVmWizard.kt` — step indicator 28 dp circles, 2 dp connector line, 12 sp labels, active `Indigo500`, done `GreenText`
- `AddNodeSheet.kt` — `OriInput` primitives, unsaved-changes confirm via `OriConfirmDialog`
- FAB → `OriFab` icon-only (52 × 52)
- VM delete → `OriConfirmDialog(variant = Danger)`

### P2 Exit Criteria
- Every screen has `@MockupPreviews` rendering folded + unfolded
- Manual Layout Inspector diff against mockup passes
- CI grep checks green on every PR
- Sora-Editor theming spike documented (pass or fail + next step)

---

## 8. Phase 3 — Wear OS 1:1 (`watch.html`)

### P3.1 — OriDevWearTheme
**File:** `wear/src/main/kotlin/dev/ori/wear/theme/OriDevWearTheme.kt`
- Dark `MaterialTheme` (Wear variant) with `background = OledBlack (#0F0F0F)`, `surface = OledSurface`, `onBackground = White`, `primary = Indigo500`
- Typography uses `OriTypography.wearLogo / wearCompact / wearLabel` (Roboto Flex) and `OriTypography.wearTerminalTiny` (JetBrains Mono, terminal output only)
- `wear/build.gradle.kts` adds `implementation(project(":core:core-fonts"))` (no core-ui dep)
- **Dependency leak verification:** `./gradlew :wear:dependencies | grep 'androidx.compose.material3'` must be empty (no phone `material3` pulled through `core-fonts`). Re-run of the P0.1 transitive-leak check, now gated inside P3 exit.

### P3.2 — Wear primitives (in wear/ module, NOT core-ui)
**Files (new):** `wear/src/main/kotlin/dev/ori/wear/components/`
- `WearProgressRing.kt` — Canvas 100 × 100 dp ring, 8 dp stroke
- `WearGauge.kt` — 72 × 72 dp, 7 dp stroke
- `WearStatusDot.kt` — 8 dp circle with pulse glow via `infiniteTransition`
- `WearPanicButton.kt` — 120 × 120 dp radial gradient red + outer glow
- `WearTile.kt` — card analog with 1 dp border, 12 dp padding
- `WearSubtleButton.kt` — 2-column grid button with `rgba(255,255,255,0.06)` bg

### P3.3 — Wear screens redesign
- `MainTileScreen.kt` — styled logo with `BrushText` (Indigo 'D'), pulsing green status; 2 × 2 quick-action grid
- `ConnectionListScreen.kt` — `WearStatusDot` (colored + glow) rows, ping in `OriTypography.wearLabel` (Roboto Flex)
- `TransferMonitorScreen.kt` — `WearProgressRing(120 dp)`, 3 gauges for active files
- `ServerHealthScreen.kt` — 3 × `WearGauge` (CPU/RAM/Disk)
- `QuickCommandsScreen.kt` — 2 × 2 `WearSubtleButton` grid
- `PanicButtonScreen.kt` — `WearPanicButton` centered, confirm tap
- `CommandOutputScreen.kt` — `OriTypography.wearTerminalTiny` (JetBrains Mono) terminal-style output
- `TwoFactorDialogScreen.kt` — large code digits 20 sp, countdown ring

### P3 Exit Criteria
- Round emulator render matches `watch.html` screen-by-screen
- All text uses `OriTypography.wear*` variants
- OLED `#0F0F0F` background verified on physical device
- `wear` module builds without transitive `material3` (phone) dependencies — verified via `./gradlew :wear:dependencies`

---

## 9. Phase 4 — Functional Gap Closure

### P4.1 — Rename / Chmod / Mkdir dialogs (feature-filemanager)
New `RenameDialog.kt`, `ChmodDialog.kt`, `CreateDirectoryDialog.kt` using `OriInput` + `OriConfirmDialog` scaffolding. Wired to existing use cases in `FileManagerViewModel`.

### P4.2 — TextMate grammars (feature-editor)
- Grammars go to `feature-editor/src/main/assets/textmate/` (not `core-ui/assets/`)
- Add PHP, Python, JavaScript, TypeScript, XML, HTML, CSS, Rust, Go, Java
- Update `TextMateLoader.kt` `GRAMMAR_INFO` map
- Remove TODO comments

### P4.3 — Editor remote file picker
New nav flow Filemanager → Editor with remote path argument.

### P4.4 — Editor undo/redo UI
Add `OriIconButton`s to `OriTopBar.actions` wrapping Sora's native `editor.undo()` / `editor.redo()`.

### P4.5 — Editor large-file handling
5 MB threshold; above, show warning sheet + chunked 256 KB SFTP read.

### P4.6 — Transfer retry policy
`RetryPolicy` in `AppPreferences`, exponential backoff, max attempts.

### P4.7 — Foreground service indicator in `OriTopBar`
New `actions` slot icon with active-session count badge.

### P4.8 — Onboarding permissions expansion
`PermissionsScreen.kt` — full permission set with per-permission rationale card.

### P4 Exit Criteria
- File Manager has rename/chmod/mkdir dialogs wired
- Editor opens 20 MB log files without OOM
- Transfer retry policy configurable via Settings → Transfers
- Onboarding handles 5+ permissions with rationale

---

## 10. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Sora-Editor theming spike fails | Editor font/syntax wrong | Spike is inside P0 (P0.8), blocks PR 4 merge. If it fails, the entire Phase 11 pauses until a separate investigation resolves it — no downstream work with unproven primitives. |
| `core-fonts` module adds boilerplate for ~0 gain if Wear theming turns out minimal | Wasted module | Module is tiny (~8 source files, mostly resources). Acceptable overhead for the transitive-dep clean-up. |
| Vendored Lucide drifts from upstream | Missed icons/updates | README pins the upstream commit. Monthly re-vendor job is trivial (copy new files). |
| Paparazzi spike reveals incompatibility with AGP 8.9.1 | No screenshot tests | Acceptable — Paparazzi was opt-in from v2 onwards. Manual diffing is the fallback. |
| Surface=White swap breaks existing screens visually | Regressions | PR 2 Commit 0 audits every call-site and attaches before/after screenshots. |
| `core-fonts` depends on `androidx.compose.ui.text` which pulls Compose runtime | Wear APK grows | `compose.ui.text` is ~200 KB and already transitively present via Wear's own Compose. Acceptable. Verified via dependency graph before P3. |
| Settings PoC in P0.6 reveals primitive design flaws | PR 4 revised | That's the whole point of the bail-out gate. No downstream work has started yet. |
| Vendoring Lucide violates MIT attribution | Legal | Attribution file `lucide-mit.txt` bundled + README comment. Matches MIT requirements. |
| Fold detection via `currentWindowAdaptiveInfo()` not supported on older devices | Layout bugs | Dep is `androidx.compose.material3.adaptive:adaptive`; project has `minSdk = 34` (verified at `app/build.gradle.kts:49` and `wear/build.gradle.kts:46`), so fully covered. |
| Terminal dark mockup inside light app confuses users | UX confusion | Mockup is authoritative. Document in release notes that terminal is dark by design. |

---

## 11. Rollout Strategy

- **PR 1:** `core-fonts` module + font assets + `OriFonts` (+ size measurement + subsetting if needed)
- **PR 2:** Commit 0 = Surface/Background swap with screen audit; Commits 1..N = Color/Shape/Spacing/Typography rewrite + `OriDevTheme` rewire
- **PR 3:** Lucide vendored (hash-pinned) + CI grep checks (diff-scoped) + self-test fixture
- **PR 3.5:** CI pipeline self-tests & reliability (P0.10) — **non-instrumented only**: `fetch-depth: 0`, `static-import-checks`, `checkCoreFontsLeakage`, `checkWearLeakage`, `checkFontBudget`, `ci-self-test.yml` weekly canary, `.github/ci/*.sh` extraction, `act` playbook. **No emulator jobs in this PR** (no androidTest source sets exist yet).
- **PR 4a:** Primitives (all 19) + `feature-settings` androidTest scaffolding + `SettingsScreenLayoutTest` + `mockup-layout-tests` emulator job wired into `pr-check.yml` + Settings PoC migration (**primary bail-out gate**)
- **PR 4b (stacked on 4a):** Sora-Editor spike (P0.8) + `feature-editor` androidTest scaffolding + `SoraThemingSpike` appended to the same `mockup-layout-tests` job + `@MockupPreviews` + mockup-hash guard + `OriDevTopBar.kt` deprecation (**spike gate**)
- **P1 PRs:** P1.1 (nav unblock), P1.2 (settings expansion) — 2 PRs
- **P2 PRs:** one PR per screen (6 PRs) — Terminal, Editor, Connections, Transfers, Filemanager, Proxmox
- **P3 PRs:** 1 PR theme + primitives, 1 PR Wear screens
- **P4 PRs:** one PR per item (8 PRs)

**Total PR count:** PR 1, 2, 3, 3.5, 4a, 4b (6) + P1 (2) + P2 (6) + P3 (2) + P4 (7) = **23 PRs**. All must pass full CI per `feedback_cicd_must_pass` memory.

### Version catalog additions (for PR 1 — `gradle/libs.versions.toml`)

| Entry | Version | Status | Notes |
|---|---|---|---|
| `androidx.compose.ui.text` | follows existing compose-bom | pre-existing transitively | used in `core-fonts` |
| `androidx.compose.ui.unit` | follows existing compose-bom | pre-existing transitively | used in `core-fonts` |
| `androidx.compose.material3.adaptive` | `1.0.0` (or latest stable at execution time) | **net-new** | `currentWindowAdaptiveInfo()` — used in P2.1, P2.5 |
| `com.mikepenz.aboutlibraries-plugin` | `10.10.0` | **net-new** | applied in `app/build.gradle.kts` |
| `com.mikepenz.aboutlibraries-core` | `10.10.0` | **net-new** | runtime library for the screen |
| `androidx.compose.ui.test.junit4` | follows existing compose-bom | pre-existing | used by `SettingsScreenLayoutTest` |

Implementer should pin to the latest stable at the moment PR 1 opens, not copy the exact numbers above blindly — they are spec targets.

Branch strategy: `feature/phase-11-foundation-pr{0,1,2,3,4}`, `feature/phase-11-<screen>`, etc.

## 12. Estimation

Per `feedback_review_before_execution` and global no-dates guidance, no time estimates. Each phase ends at its exit criteria, not at a date. Task-count and PR-count above are for review sizing.

## 13. Review Workflow (user-mandated, actual agent names)

1. **Review Cycle 1 (parallel, DONE):** `general-purpose` acting as devil's advocate + `feature-dev:code-architect` — feedback applied in v2 (this document).
2. **Review Cycle 2 (next):** `superpowers:requesting-code-review` skill (which routes to `code-review:code-review` under the hood) on v2.
3. **Fix 2 → v3**
4. **Review Cycle 3 (final):** fresh `general-purpose` agent with devil's-advocate prompt, explicitly comparing v3 against v1 deltas.
5. **Fix 3 → v4** — the executable plan.
6. **Only then** start implementation in a separate session using `superpowers:executing-plans`.

Per `feedback_review_before_execution.md`: no implementation work until all three review+fix cycles are complete.

## 14. Open Questions (remaining for review cycle 3)

All open questions from v2 have been resolved in v3:
- ~~Sora-Editor spike timing~~ → P0.8, blocks PR 4.
- ~~`OriCheckbox` retroactive~~ → added definitively to P0.5.
- ~~Paparazzi spike owner~~ → Paparazzi is no longer planned; manual diff + instrumented gate test is the acceptance method.
- ~~Mockup hash initial value~~ → computed during PR 4, committed to `.github/mockup-hash.txt`.
- ~~`core-fonts` Compose dep~~ → limited to `ui-text` + `ui-unit`, `material3` excluded via **surgical per-dependency exclusion** (v4 change from v3's `configurations.all`); verified via `./gradlew :core:core-fonts:dependencies`.
- ~~Font subsetting~~ → threshold 1.5 MB enforced in PR 1.

**Still open for review cycle 3:**
1. **Lucide vendoring vs direct hand-port from lucide.dev.** Vendoring the compose-icons source avoids hand-port errors but pulls in Apache 2.0 license layer alongside the Lucide MIT/ISC upstream. Is dual-attribution preferable to a pure hand-port of ~68 SVGs? Current plan: dual-attribution.
2. **Settings PoC instrumented test infrastructure.** The plan assumes the project has an existing androidTest harness. If not, this adds a small infra task to PR 4. Review cycle 3 should verify.
3. **`aboutlibraries` manual JSON schema.** The plan describes a `config/aboutlibraries/libraries/lucide.json` entry — the exact schema expected by the plugin v10.x needs confirmation against the plugin's docs at implementation time.
4. ~~**Testing fixture for CI grep positive-control.**~~ **Resolved in v6**: fixture lives at `.github/fixtures/bad-imports.kt.txt` (non-`.kt` extension so IntelliJ doesn't index it; `.github/` is outside any Gradle source set so nothing compiles it).

---

## 15. Appendix A — Mockup → Screen → File Map

| Mockup | Screen | Source files |
|---|---|---|
| `index.html` | Design system reference | `core/core-ui/**`, `core/core-fonts/**` |
| `terminal.html` | feature-terminal | `TerminalScreen.kt`, `TerminalTabBar.kt`, `CustomKeyboard.kt`, `SendToClaudeSheet.kt`, `CodeBlocksSheet.kt` |
| `code-editor.html` | feature-editor | `CodeEditorScreen.kt`, `EditorTabBar.kt`, `SearchReplaceBar.kt`, `SoraEditorView.kt`, `GitDiffStatusBar.kt`, `DiffViewerScreen.kt`, `TextMateLoader.kt` |
| `connection-manager.html` | feature-connections | `ConnectionListScreen.kt`, `ServerProfileCard.kt`, `AddEditConnectionScreen.kt`, `ConnectionDetailSheet.kt` |
| `transfer-queue.html` | feature-transfers | `TransferQueueScreen.kt`, `TransferItemCard.kt`, new `TransferStatsBar.kt`, new `TransferFilterChips.kt`, new `CompletedTransferRow.kt` |
| `file-manager.html` | feature-filemanager | `FileManagerScreen.kt`, `DualPaneLayout.kt`, `FileListPane.kt`, `BreadcrumbBar.kt`, `FileItemRow.kt`, `BookmarkBar.kt`, `FilePreviewSheet.kt`, `FileInfoSheet.kt`, new dialogs (P4) |
| `proxmox.html` | feature-proxmox | `ProxmoxDashboardScreen.kt`, `NodeCard.kt`, `VmCard.kt`, `CreateVmWizard.kt`, `AddNodeSheet.kt`, `CertificateTrustDialog.kt`, `VmStatusBadge.kt`, new `OriMiniBar` usage |
| `settings.html` | feature-settings | Full rewrite — see P1.2 |
| `watch.html` | wear | Full rewrite — see P3 |

## 16. Appendix B — Files Created / Modified

**New modules:** 1 (`core/core-fonts`)
**New files (P0):** ~70 (primitives, icons, theme files, fonts, attribution files)
**New files (P1):** ~12 (settings sections + components)
**New files (P2):** ~8 (transfer stats bar, filter chips, completed row)
**New files (P3):** ~6 (wear primitives) + 8 rewritten wear screens
**New files (P4):** ~5 (dialogs, TextMate grammar configs)
**Modified files:** every existing `*Screen.kt` (TopAppBar → OriTopBar), every touched `*ViewModel.kt`, `OriDevTheme.kt`, `Shape.kt`, `Type.kt`, `Color.kt`, `OriDevNavHost.kt`, `settings.gradle.kts`, `core-ui/build.gradle.kts`, `wear/build.gradle.kts`, `detekt.yml` (minor), `.github/workflows/build.yml`

Total estimated diff: ~7,000 LOC added, ~1,500 LOC removed, ~2,500 LOC modified across ~22 PRs.

---

**End of Draft v6. Executable plan — ready for implementation in a separate session.**

**Review-cycle provenance:**
- v1 → cycle 1 (parallel): devils-advocate + code-architect → v2
- v2 → cycle 2: feature-dev code-reviewer → v3
- v3 → cycle 3 (final devils-advocate v3-vs-v1 deltas pass) → v4
- v4 → user-raised CI-testing gap → v5 (new §P0.10)
- v5 → cycle 4 (scoped on §P0.10) → **v6 (current)**

Per `feedback_review_before_execution.md`: four review cycles total completed (the last scoped to the substantial §P0.10 addition). All blockers resolved. Implementation may now start via `superpowers:executing-plans` in a fresh session.
