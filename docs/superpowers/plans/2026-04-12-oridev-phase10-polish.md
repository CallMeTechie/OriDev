# Ori:Dev Phase 10: CI/CD & Polish

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** DRAFT v3 — both reviews applied (correctness + architecture/security). Ready for execution.

## v3 Changelog (review findings applied)

**Blockers fixed:**
1. **ACRA default = `false`** (opt-in) not `true` (opt-out) — GDPR correctness. Prose + code aligned.
2. **Stack-trace PII scrubber** ships in v0.2, not "future". Regex strips absolute filesystem paths and FQDN-looking tokens from every `STACK_TRACE` before send. Implemented via custom `ReportSenderFactory` wrapper.
3. **ACRA init in `attachBaseContext`** no longer uses Hilt. Instead: (a) `attachBaseContext` gates ONLY on `BuildConfig.ACRA_BACKEND_URL == "https://acra.invalid"` + `BuildConfig.DEBUG` (cheap static checks, no I/O); (b) the user toggle is consulted in `Application.onCreate()` via `ACRA.errorReporter.setEnabled(prefValue)` — DataStore read happens async on an IO dispatcher, no `runBlocking` in `attachBaseContext`. Toggle takes effect on next crash, not next launch.
4. **`feature-settings` is currently empty** (only `.gitkeep`). Task 10.7 now explicitly scaffolds `SettingsScreen`, `SettingsViewModel`, Hilt module, navigation wiring — with a **"Datenschutz"** section holding the ACRA toggle, and a note that Phase 9 (when revived) will add further sections alongside it, not rewrite.
5. **`baselineprofile` plugin applied to BOTH `:baselineprofile` (producer) and `:app` (consumer)** — Task 10.6 Step 3 now includes `plugins { alias(libs.plugins.baselineprofile) }` on `:app`.
6. **Wear release buildType** now created from scratch (it doesn't exist today) with `signingConfig`, `isMinifyEnabled`, `proguardFiles`. Plan no longer says "mirror".
7. **`lint.abortOnError = true`** gated on Task 10.5 landing first. Execution order explicitly makes 10.5 block 10.2.
8. **release.yml signing-presence check** runs BEFORE `Decode Keystore` step. Added keystore cleanup in an `if: always()` post-step.
9. **R8 ViewModel rule** tightened: `-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *` + `-keep class dev.ori.**.*ViewModel { <init>(...); }`. Removed the over-broad `@Composable` blanket keep.
10. **Additional R8 keep rules** for DataStore, WorkManager + `hilt-work` (`*_AssistedFactory`), `HiltWorkerFactory` entrypoint, Horologist, Play-Services-Wearable `WearableListenerService` subclasses, kotlinx-coroutines-play-services.
11. **Explicit deletion** of the current brittle `grep -q "Build failed"` detekt block in pr-check.yml (lines 60-66).
12. **`enableV1Signing = false`** — Janus CVE-2017-13156 on pre-N; minSdk 34 makes v1 superfluous.
13. **`acra-toast` dropped** (not referenced in config). `acra-limiter` retained for rate-limiting crash storms.
14. **LeakCanary `[libraries]` entry** added alongside the `[versions]` entry in `libs.versions.toml`.
15. **FLAG_SECURE** set on `MainActivity` during the onboarding flow to prevent over-the-shoulder / screen-record capture of permission grants.
16. **Dependabot SSHJ/BouncyCastle** in their own `security-crypto` group with `open-pull-requests-limit: 1` and major-version updates ignored — manual review gate for security-sensitive libs.
17. **Moshi codegen keep rules** made explicit: `-keep @com.squareup.moshi.JsonClass class *` added.
18. **`core-security` reference removed** — module does not exist on disk; `AcraConfig` stays in `app/crash/`.

**Architecture decisions reaffirmed (review raised, we declined):**
- Baseline profile scaffold stays in Phase 10 (reviewer suggested Phase 11). Cost is low, scaffolding is worth landing now.
- `AcraConfig` stays in `app/crash/` (reviewer suggested `core-common`). Wear doesn't need it; YAGNI.
- `store/*.md` stays in Phase 10 (reviewer suggested out-of-phase). Cost is ~30 minutes, zero maintenance.

**Acknowledged residual risks (not fixed, documented):**
- `ACRA_BASIC_AUTH_PASSWORD` embedded in release APK (anyone unpacking can spam the backend). Mitigation: per-version token in secret, operator rotates on abuse. Documented in threat model.
- Baseline profile is empty until first manual `workflow_dispatch` run — v0.2 ships without the perf benefit.
- Google Play App Signing enrollment is recommended (separates upload key from app signing key) — documented in RELEASE.md, outside CI scope.

## Locked Decisions (2026-04-12)

1. **Crash reporting:** ACRA with placeholder `https://acra.invalid` URL. No reports flow until operator hosts Acrarium and sets `ACRA_BACKEND_URL`. **Crashlytics explicitly rejected.**
2. **Detekt strictness:** Fail on any detekt error. No baseline file. Latent warnings from Phases 0-8 are fixed as they surface.
3. **Onboarding language:** German only, hardcoded strings (consistent with existing app). No i18n resource split.
4. **Debug build applicationId:** NO suffix. Single variant at a time. Wear pairing stays simple.
5. **Baseline profile workflow:** `workflow_dispatch` only (~15min emulator run accepted).

**Goal:** Ship a release-ready Ori:Dev: hardened CI/CD pipeline with tag-based signed releases, comprehensive R8 rules, an onboarding flow, accessibility audit, performance baseline, opt-in crash reporting (ACRA), and store-listing scaffolding.

**Depends on:** Phases 0-8 complete. **Phase 9 (Monetisierung) is intentionally SKIPPED** for v0.2 — there is no Premium gate, no AdMob, no Billing. Onboarding therefore omits any "Trial / Upgrade" screens. Feature-gating hooks may be added later without affecting Phase 10 work.

**Expected Outcome:** Tagging `vX.Y.Z` produces a signed AAB + APK in a GitHub Release. R8 minify is on for release and the app boots and runs end-to-end. New users see a 4-screen onboarding once. TalkBack reads every primary screen. LeakCanary catches leaks in debug. Crash reports flow to a self-hosted Acrarium endpoint (or null sink in CI). The `store/` directory holds the listing copy + screenshot specs.

---

## Design Decisions

1. **ACRA over Firebase Crashlytics.** Crashlytics requires a real GCP project, `google-services.json`, and the `google-services` Gradle plugin — none of which can be provisioned from CI without leaking project IDs. ACRA is Apache-2.0, has no SDK initialisation gate, and supports HTTP/email/Acrarium senders. Default sender is HTTP (Acrarium), URL provided via `gradle.properties` / GitHub secret `ACRA_BACKEND_URL`. Disabled in debug; enabled in release. Users opt out via Settings.

2. **Onboarding is a single-shot Compose flow stored in DataStore (`onboarding_completed: Boolean`).** The main UI launches behind it. No deep-link bypass. Battery optimisation request is best-effort (Pixel Fold can decline silently).

3. **Baseline Profile module is added but generation is a manual/CI-trigger step.** Generating a profile requires a real macrobenchmark run on an emulator. We add `:baselineprofile` module + the consumer wiring on `:app`, plus a separate workflow `baseline-profile.yml` that can be invoked via `workflow_dispatch`. The committed `baseline-prof.txt` starts empty; first real run replaces it.

4. **Release signing keys are GitHub Secrets only.** Plan configures workflows to consume `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. We will NOT generate or commit a keystore. The existing `release.yml` already references these secrets — Phase 10 verifies the wiring and adds a signing block to `app/build.gradle.kts` and `wear/build.gradle.kts`.

5. **Store assets cannot be automated.** Phase 10 creates `store/` with `listing.md` (title/short/long description in EN+DE), a `screenshots/README.md` checklist (Pixel 8, Pixel Fold inner+outer, Pixel Watch 2), and the feature-graphic spec (1024x500 PNG). Assets themselves remain TODO and are flagged in the completion checklist as "manual".

6. **Accessibility scope = the 7 primary screens + onboarding.** Connection list, Connection edit, File manager, Terminal, Transfers, Editor, Settings. Each gets: content descriptions on every interactive element, semantic role hints where the default is wrong, 48dp minimum touch targets, and `mergeDescendants = true` on row-style cards.

7. **LeakCanary is `debugImplementation` only.** Zero release impact.

---

## Open Questions

All resolved — see "Locked Decisions" above.

---

## Scope & Non-Goals

### IN scope (executable by Claude / CI)
- Refining `pr-check.yml` with deterministic gating
- Improving `release.yml` to verify signing config presence and produce phone+wear AAB/APK
- Adding library Proguard/R8 rules
- Enabling R8 on the wear module
- Onboarding flow module + DataStore flag + integration
- Accessibility audit + content descriptions
- LeakCanary `debugImplementation`
- Baseline Profile module scaffold
- ACRA dependency, init, settings toggle
- `store/` directory with listing.md + screenshot spec + feature graphic spec

### DEFERRED (manual / out-of-band)
- **Real release keystore creation** — maintainer must generate + upload secrets
- **Real Baseline Profile generation** — emulator run required
- **Real store screenshots / feature graphic** — device captures required
- **Real ACRA backend URL** — Acrarium instance must be hosted
- **Crashlytics path** — explicitly chosen against (Design Decision #1)
- **AdMob / Billing rules** — Phase 9 skipped, deferred

---

## File Structure

```
/.github/workflows/
├── pr-check.yml                  # MODIFIED — gating tightened
├── build.yml                     # unchanged
├── release.yml                   # MODIFIED — wear AAB + signing assert
├── security.yml                  # unchanged
├── baseline-profile.yml          # NEW — workflow_dispatch + emulator run
└── dependabot.yml                # NEW

/app/
├── build.gradle.kts              # MODIFIED — signingConfigs, R8, ACRA, LeakCanary
├── proguard-rules.pro            # MODIFIED — full library rule set
└── src/main/
    ├── AndroidManifest.xml       # MODIFIED — REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    ├── baseline-prof.txt         # NEW — empty placeholder
    └── kotlin/dev/ori/app/
        ├── OriDevApplication.kt  # MODIFIED — ACRA init via attachBaseContext
        ├── MainActivity.kt       # MODIFIED — gate behind onboarding
        └── crash/
            ├── AcraConfig.kt     # NEW
            └── CrashReportingPreferences.kt # NEW

/feature-onboarding/              # NEW MODULE
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/dev/ori/feature/onboarding/
        ├── OnboardingFlow.kt
        ├── OnboardingViewModel.kt
        ├── data/OnboardingPreferences.kt
        └── ui/{Welcome,Permissions,BatteryOptimization,Done}Screen.kt

/feature-settings/src/main/kotlin/dev/ori/feature/settings/
└── ui/
    ├── SettingsScreen.kt          # NEW / EXPANDED — a11y, crash opt-out, version
    └── SettingsViewModel.kt       # NEW / EXPANDED

/baselineprofile/                  # NEW MODULE (com.android.test)
├── build.gradle.kts
└── src/main/kotlin/dev/ori/baselineprofile/
    └── StartupBaselineProfileGenerator.kt

/wear/
├── build.gradle.kts               # MODIFIED — release buildType + minify
└── proguard-rules.pro             # NEW

/store/                            # NEW
├── listing.md
├── privacy-policy.md
├── screenshots/README.md
└── feature-graphic/README.md
```

---

## Task 10.1: CI PR-Check Refinement

**Files:**
- Modify: `/.github/workflows/pr-check.yml`
- Create: `/.github/dependabot.yml`

**Problems with current `pr-check.yml`:** detekt step has `continue-on-error` with brittle stdout-grep; lint has `continue-on-error`; UI tests run on every PR (wasteful).

- [ ] **Step 1: Tighten code-quality job** — **explicitly delete** the brittle `grep -q "Build failed"` step at pr-check.yml lines 60-66 and replace the detekt steps with:
  ```yaml
  - name: Run detekt
    run: ./gradlew detekt
  - name: Upload detekt report
    if: always()
    uses: actions/upload-artifact@v4
    with: { name: detekt-report, path: '**/build/reports/detekt/' }
  - name: Run Android Lint
    run: ./gradlew lint
  ```
- [ ] **Step 2: Make UI tests opt-in via PR label:**
  ```yaml
  if: github.event.pull_request.draft == false && contains(github.event.pull_request.labels.*.name, 'run-ui-tests')
  ```
- [ ] **Step 3: Create `dependabot.yml`** with grouped updates. Security-sensitive libs get their own group with `open-pull-requests-limit: 1` and major updates ignored:
  ```yaml
  groups:
    compose: { patterns: ["androidx.compose*"] }
    kotlin: { patterns: ["org.jetbrains.kotlin*", "com.google.devtools.ksp*"] }
    hilt: { patterns: ["com.google.dagger*", "androidx.hilt*"] }
    security-crypto:
      patterns: ["com.hierynomus:sshj*", "org.bouncycastle*", "ch.acra*"]
  ignore:
    - dependency-name: "com.hierynomus:sshj"
      update-types: ["version-update:semver-major"]
    - dependency-name: "org.bouncycastle:*"
      update-types: ["version-update:semver-major"]
  ```

**Acceptance:** Detekt violation fails CI; label gating works; Dependabot opens weekly grouped PRs. Testing: push a deliberate detekt violation, verify red, revert.

**Dependencies:** None.

---

## Task 10.2: Release Workflow Hardening

**Files:**
- Modify: `/.github/workflows/release.yml`
- Modify: `/app/build.gradle.kts` (signingConfigs, R8 on release)
- Modify: `/wear/build.gradle.kts` (signingConfigs + release buildType + minify)

- [ ] **Step 1: signingConfigs block in app/build.gradle.kts**
  ```kotlin
  android {
      signingConfigs {
          create("release") {
              val keystorePath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
              storeFile = file(keystorePath)
              storePassword = System.getenv("KEYSTORE_PASSWORD")
              keyAlias = System.getenv("KEY_ALIAS")
              keyPassword = System.getenv("KEY_PASSWORD")
              enableV1Signing = false  // minSdk 34, Janus CVE-2017-13156 avoidance
              enableV2Signing = true
              enableV3Signing = true
              enableV4Signing = true
          }
      }
      buildTypes {
          release {
              isMinifyEnabled = true
              isShrinkResources = true
              proguardFiles(
                  getDefaultProguardFile("proguard-android-optimize.txt"),
                  "proguard-rules.pro",
              )
              signingConfig = if (System.getenv("KEYSTORE_PASSWORD") != null)
                  signingConfigs.getByName("release")
              else signingConfigs.getByName("debug")
          }
      }
      lint { abortOnError = true; warningsAsErrors = false; checkReleaseBuilds = true }
  }
  ```
- [ ] **Step 2: Create wear release buildType from scratch** (wear currently has no `buildTypes { release { } }` at all). Add full `signingConfigs { create("release") { ... } }` block using the same env vars, and:
  ```kotlin
  buildTypes {
      release {
          isMinifyEnabled = true
          proguardFiles(
              getDefaultProguardFile("proguard-android-optimize.txt"),
              "proguard-rules.pro",
          )
          signingConfig = if (System.getenv("KEYSTORE_PASSWORD") != null)
              signingConfigs.getByName("release")
          else signingConfigs.getByName("debug")
      }
  }
  ```
  Create `wear/proguard-rules.pro` (Task 10.3 Step 2).
- [ ] **Step 3: release.yml signing-presence check** — fail fast if any of `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` missing. **Must run BEFORE "Decode Keystore"** so the empty file isn't written.
- [ ] **Step 4: `KEYSTORE_PATH` env** pointing to `${{ github.workspace }}/app/release.keystore`.
- [ ] **Step 5: Add `apksigner verify --verbose` smoke step** on the produced AAB. Fail the job if v2/v3 signatures are absent.
- [ ] **Step 6: Keystore cleanup post-step** with `if: always()`:
  ```yaml
  - name: Cleanup keystore
    if: always()
    run: |
      rm -f "${{ github.workspace }}/app/release.keystore"
      rm -f keystore.base64 || true
  ```

**Acceptance:** Tagging `v0.2.0-test` on a branch produces signed AAB + APK for phone + wear, attached to a draft GitHub Release. Missing secrets → workflow red in 5s.

**Dependencies:** Task 10.3 (R8 rules) must land first.

---

## Task 10.3: Proguard / R8 Rules

**Files:**
- Modify: `/app/proguard-rules.pro`
- Create: `/wear/proguard-rules.pro`

- [ ] **Step 1: Replace `/app/proguard-rules.pro` with full ruleset** covering:
  - **Kotlin/Coroutines** (`kotlinx.coroutines.**`, attributes: Signature, InnerClasses, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault, SourceFile, LineNumberTable)
  - **SSHJ** (`net.schmizz.**`, `com.hierynomus.**`, sshj.transport.kex/cipher/mac/compression/signature/userauth.method, `-dontwarn` for bouncycastle/slf4j/jgss/jzlib)
  - **BouncyCastle** (`org.bouncycastle.**`, `org.bouncycastle.jcajce.provider.**`)
  - **Apache Commons Net** (FTP)
  - **OkHttp** (Proxmox) + okio + conscrypt/openjsse dontwarn
  - **Moshi** (codegen adapters: `**JsonAdapter { <init>(...); <fields>; }`, `@JsonClass`, field annotations)
  - **Room** (RoomDatabase/Entity/Dao/Database)
  - **Hilt/Dagger** (`dagger.hilt.**`, `**_HiltModules`, `**_HiltComponents`, `**_Factory`, `hilt_aggregated_deps.**`)
  - **Sora-Editor** (`io.github.rosemoe.sora.**`, `sora.langs.textmate.**`, `org.eclipse.tm4e.**`, `org.snakeyaml.**`)
  - **ConnectBot termlib** (`org.connectbot.**`, `de.mud.terminal.**`)
  - **java-diff-utils** (`com.github.difflib.**`)
  - **Coil 3** (`coil3.**`)
  - **Play Services Wearable**
  - **ACRA** (core, config, collector, sender, annotation classes)
  - **Compose runtime** (keep runtime classes + members)
  - **DataStore** (`androidx.datastore.**` reflective serialization)
  - **WorkManager + hilt-work** (`**_AssistedFactory`, `androidx.hilt.work.HiltWorkerFactory` entrypoint, `@HiltWorker` annotated classes)
  - **Horologist** (`com.google.android.horologist.**`, compose-layout reflection)
  - **Play Services Wearable** (`WearableListenerService` subclasses: `-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }`)
  - **kotlinx-coroutines-play-services** (`kotlinx.coroutines.tasks.**`)
  - **Project** — tightened, no blanket `@Composable`:
    ```proguard
    -keep class dev.ori.**.*ViewModel { <init>(...); }
    -keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *
    -keep class dev.ori.**.UiState { *; }
    -keep class dev.ori.**.UiState$* { *; }
    ```
  - **Moshi codegen** explicit: `-keep @com.squareup.moshi.JsonClass class *` + the existing `**JsonAdapter` rule
- [ ] **Step 2: Create `/wear/proguard-rules.pro`** with Hilt + Compose + Wearable + Coroutines blocks (no SSHJ / sora / termlib / ACRA needed on wear).
- [ ] **Step 3: Verify minified release builds:**
  ```bash
  ./gradlew assembleRelease :wear:assembleRelease
  ```
- [ ] **Step 4: Manual smoke test** of all 5 features on minified APK installed on real device.

**Acceptance:** `./gradlew assembleRelease` succeeds; smoke test of connect/SFTP/editor/terminal/Proxmox passes; no `ClassNotFoundException` / `NoSuchMethodError` in logcat.

**Dependencies:** None. **Must land before 10.2 and 10.6.**

---

## Task 10.4: Onboarding Flow

**Files:**
- Create: `/feature-onboarding/build.gradle.kts`
- Create: `/feature-onboarding/src/main/AndroidManifest.xml`
- Create: `/feature-onboarding/src/main/kotlin/dev/ori/feature/onboarding/data/OnboardingPreferences.kt`
- Create: `/feature-onboarding/src/main/kotlin/dev/ori/feature/onboarding/OnboardingFlow.kt`
- Create: `/feature-onboarding/src/main/kotlin/dev/ori/feature/onboarding/ui/{Welcome,Permissions,BatteryOptimization,Done}Screen.kt`
- Modify: `/settings.gradle.kts` (include `:feature-onboarding`)
- Modify: `/app/build.gradle.kts` (+ dependency on `:feature-onboarding`)
- Modify: `/app/src/main/kotlin/dev/ori/app/MainActivity.kt` (gate behind onboarding)
- Modify: `/app/src/main/AndroidManifest.xml` (+ `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, + `POST_NOTIFICATIONS` if not yet there)

- [ ] **Step 1: Module gradle** — copy pattern from `feature-settings/build.gradle.kts`. Dependencies: `core-ui`, `core-common`, `androidx.datastore.preferences`, Compose, Hilt.
- [ ] **Step 2: OnboardingPreferences (DataStore)** — boolean flag `onboarding_completed`, default false; `markCompleted()` suspend.
- [ ] **Step 3: 4 Screens** (Compose, Material3, light theme, 48dp min touch targets, all with content descriptions):
  - **Welcome:** logo + headline "Willkommen bei Ori:Dev" + subtitle + CTA "Loslegen"
  - **Permissions:** explains POST_NOTIFICATIONS; `rememberLauncherForActivityResult(RequestMultiplePermissions())`; "Weiter" + "Überspringen" buttons (non-blocking)
  - **BatteryOptimization:** checks `PowerManager.isIgnoringBatteryOptimizations(packageName)`; fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent. Explicit German justification required by Play policy: *"Damit SFTP-Übertragungen im Hintergrund nicht abgebrochen werden, sollte Ori:Dev von der Akku-Optimierung ausgenommen werden. Du kannst diese Einstellung jederzeit in den System-Einstellungen ändern."* Buttons "Weiter" + "Überspringen"
  - **Done:** single CTA "Fertig" → `viewModel.markCompleted()` → `onFinish()`
- [ ] **Step 4: OnboardingFlow** as NavHost with routes welcome → permissions → battery → done.
- [ ] **Step 5: Gate MainActivity**
  ```kotlin
  val completed by prefs.completed.collectAsStateWithLifecycle(initialValue = null)
  when (completed) {
      null -> LoadingIndicator()
      true -> OriDevApp()
      false -> OnboardingFlow(onFinish = { scope.launch { prefs.markCompleted() } })
  }
  ```
- [ ] **Step 6: Manifest permission** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Also set `MainActivity` window flag `FLAG_SECURE` while onboarding is active to block screen-record / over-the-shoulder capture of permission grants: `window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)` in an `onResume` gate, cleared when `completed == true`.
- [ ] **Step 7: Tests** — `OnboardingPreferencesTest` (in-memory DataStore, markCompleted flips flow).

**Acceptance:** Fresh install → onboarding shown; Skip-through works; restart preserves flag.

**Dependencies:** None.

---

## Task 10.5: Accessibility Audit

**Files:**
- Modify (audit): the 7 primary screens in feature-* modules
- Create: `/docs/accessibility-checklist.md`

**Screens and required additions:**

| Screen | Additions |
|---|---|
| `feature-connections/.../ConnectionListScreen.kt` | `contentDescription` on FAB + swipe actions; `mergeDescendants=true` on cards; `Role.Button` on add |
| `feature-connections/.../ConnectionEditScreen.kt` | TextField labels; password visibility toggle `contentDescription` |
| `feature-filemanager/.../FileListScreen.kt` | Row: `"${name}, ${type}, ${size}"`; long-press menu items |
| `feature-terminal/.../TerminalScreen.kt` | `"Terminal output, ${lineCount} lines"` on canvas; soft-keyboard buttons (Esc, Tab, Ctrl) |
| `feature-transfers/.../TransferListScreen.kt` | `"${file}, ${progress}%, ${status}"`; pause/resume/cancel buttons |
| `feature-editor/.../EditorScreen.kt` | `"Code editor, ${language}"`; Claude action chips |
| `feature-settings/.../SettingsScreen.kt` | `Role.Switch` on toggles; `heading()` on section headers |

- [ ] **Step 1: Grep for sub-48dp touch targets** — `grep -rn "size(.*\.dp)" feature-*/src/main --include="*.kt"` and audit hits.
- [ ] **Step 2: Apply content descriptions** mechanically to every Icon/IconButton/Image/swipe action.
- [ ] **Step 3: Add semantic heading roles** on section headers.
- [ ] **Step 4: Manual TalkBack pass** on Pixel Fold emulator; document gaps in `accessibility-checklist.md`.
- [ ] **Step 5: Compose UI test** asserting key contentDescriptions exist.

**Acceptance:** Every interactive element has non-null `contentDescription`; lint `ContentDescription`/`ClickableViewAccessibility` checks at error severity pass.

**Dependencies:** None.

---

## Task 10.6: Performance — Baseline Profile + LeakCanary + Startup Tracing

**Files:**
- Create: `/baselineprofile/build.gradle.kts`
- Create: `/baselineprofile/src/main/AndroidManifest.xml`
- Create: `/baselineprofile/src/main/kotlin/dev/ori/baselineprofile/StartupBaselineProfileGenerator.kt`
- Modify: `/settings.gradle.kts` (include `:baselineprofile`)
- Modify: `/app/build.gradle.kts` (consume baselineprofile, add LeakCanary, profileinstaller)
- Create: `/app/src/main/baseline-prof.txt` (empty placeholder)
- Create: `/.github/workflows/baseline-profile.yml`

- [ ] **Step 1: Add libs to `libs.versions.toml`:**
  ```toml
  benchmark-macro = "1.3.4"
  profileinstaller = "1.4.1"
  leakcanary = "2.14"
  baselineprofile-plugin = "1.3.4"
  ```
  plus plugins section `baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineprofile-plugin" }`.
- [ ] **Step 2: Create `:baselineprofile` module** (`com.android.test` + baselineprofile plugin), `targetProjectPath = ":app"`, `StartupBaselineProfileGenerator` using `BaselineProfileRule` that presses "Loslegen" → "Weiter" → "Weiter" → "Fertig" to skip onboarding, then waits for idle.
- [ ] **Step 3: App consumer wiring** — MUST apply the `androidx.baselineprofile` plugin on `:app` too (not just `:baselineprofile`) to register the `baselineProfile` configuration:
  ```kotlin
  plugins {
      alias(libs.plugins.baselineprofile)
  }
  dependencies {
      implementation(libs.androidx.profileinstaller)
      "baselineProfile"(project(":baselineprofile"))
      debugImplementation(libs.leakcanary.android)
  }
  ```
- [ ] **Step 4: Empty `app/src/main/baseline-prof.txt`** placeholder.
- [ ] **Step 5: `baseline-profile.yml` workflow** (manual `workflow_dispatch`) using `reactivecircus/android-emulator-runner@v2` (api-level 34, x86_64, google_apis, pixel_6) running `./gradlew :app:generateReleaseBaselineProfile` and uploading the result as an artifact.
- [ ] **Step 6: Startup Trace markers** in `OriDevApplication.onCreate()` via `Trace.beginSection/endSection`.
- [ ] **Step 7: LeakCanary smoke** — debug build, navigate terminal round-trip, force GC, expect no leaks.

**Acceptance:** Release assembles with empty `baseline-prof.txt`; LeakCanary is not in release APK (`dependencyInsight`); workflow_dispatch runs green.

**Dependencies:** Task 10.3 (R8 rules).

---

## Task 10.7: Crash Reporting (ACRA)

**Files:**
- Modify: `/gradle/libs.versions.toml` (+ACRA)
- Modify: `/app/build.gradle.kts` (+ACRA deps + `buildConfigField` for backend URL/auth)
- Create: `/app/src/main/kotlin/dev/ori/app/crash/AcraConfig.kt`
- Create: `/app/src/main/kotlin/dev/ori/app/crash/CrashReportingPreferences.kt`
- Modify: `/app/src/main/kotlin/dev/ori/app/OriDevApplication.kt` (override `attachBaseContext`)
- Modify: `/feature-settings/.../SettingsScreen.kt` (+ toggle)

- [ ] **Step 1: libs.versions.toml**
  ```toml
  acra = "5.11.4"
  acra-http = { module = "ch.acra:acra-http", version.ref = "acra" }
  acra-toast = { module = "ch.acra:acra-toast", version.ref = "acra" }
  acra-limiter = { module = "ch.acra:acra-limiter", version.ref = "acra" }
  ```
- [ ] **Step 2: buildConfigField** for `ACRA_BACKEND_URL` (default `"https://acra.invalid"`), `ACRA_BASIC_AUTH_LOGIN`, `ACRA_BASIC_AUTH_PASSWORD` — all pulled from `project.findProperty()` so CI overrides via `-P`.
- [ ] **Step 3: AcraConfig object** with PII-minimal `ReportField` allowlist (APP_VERSION_CODE, APP_VERSION_NAME, PHONE_MODEL, ANDROID_VERSION, STACK_TRACE, STACK_TRACE_HASH, CRASH_DATE). **No logcat, no installation id, no shared prefs.** Guards: `if (!enabled || BuildConfig.DEBUG || BuildConfig.ACRA_BACKEND_URL == "https://acra.invalid") return`.
- [ ] **Step 4: CrashReportingPreferences** (DataStore) — `crashReportingEnabled: Boolean`, default **`false`** (opt-in per GDPR), `setEnabled()` suspend.
- [ ] **Step 4b: Scaffold `feature-settings`** — the module is currently empty (`.gitkeep` only). Create `SettingsScreen`, `SettingsViewModel`, `SettingsState`, Hilt `SettingsModule` providing `CrashReportingPreferences`, navigation entry. Layout reserves a "Datenschutz" section for the crash toggle plus a version-info row. Phase 9 (when revived) will append further sections — Phase 10 must not claim ownership of non-privacy settings.
- [ ] **Step 4c: PII Scrubber** — implement `ScrubbingReportSenderFactory : ReportSenderFactory` wrapping the default `HttpSender`. In `send(Context, CrashReportData)` pre-process `ReportField.STACK_TRACE`:
  ```kotlin
  private val ABSOLUTE_PATH = Regex("""(/(?:sdcard|storage|data|system|vendor)/[^\s:()]*)""")
  private val FQDN = Regex("""\b([a-z0-9-]+\.)+(com|net|org|io|dev|app|internal|local|lan)\b""", RegexOption.IGNORE_CASE)
  fun scrub(trace: String): String =
      trace.replace(ABSOLUTE_PATH, "<path>").replace(FQDN, "<host>")
  ```
  Register via `@AutoService(ReportSenderFactory::class)` or manual entry in `META-INF/services`.
- [ ] **Step 5: OriDevApplication.attachBaseContext** — do NOT read DataStore (ANR risk + Hilt injection not yet ready). Only call `AcraConfig.initIfEnabled(this)` which gates on static `BuildConfig` checks only:
  ```kotlin
  override fun attachBaseContext(base: Context) {
      super.attachBaseContext(base)
      AcraConfig.initIfEnabled(this)
  }
  ```
  Inside `AcraConfig.initIfEnabled`:
  ```kotlin
  if (BuildConfig.DEBUG) return
  if (BuildConfig.ACRA_BACKEND_URL == "https://acra.invalid") return
  application.initAcra { /* config using scrubbing sender */ }
  ```
- [ ] **Step 5b: Consult user preference in `onCreate()`** — after Hilt injection is ready:
  ```kotlin
  override fun onCreate() {
      super.onCreate()
      wearDataSyncPublisher.start()
      applicationScope.launch(Dispatchers.IO) {
          val enabled = crashReportingPreferences.enabled.first()
          if (ACRA.isInitialised) ACRA.errorReporter.setEnabled(enabled)
      }
  }
  ```
  Toggle takes effect on the next crash, not the next launch.
- [ ] **Step 6: SettingsScreen toggle** "Anonyme Absturzberichte senden" with snackbar hint "Änderung wirkt nach Neustart".
- [ ] **Step 7: Tests** — `CrashReportingPreferencesTest` (opt-out persists), `AcraConfigTest` (init is no-op when disabled or debug).

**Acceptance:** Debug build: ACRA never initialised. Release with placeholder URL: short-circuits. Release with real URL: a test `throw` yields an HTTP POST. Opt-out via toggle disables on next launch.

**Dependencies:** None.

---

## Task 10.8: Store Listing Assets

**Files:**
- Create: `/store/listing.md`
- Create: `/store/screenshots/README.md`
- Create: `/store/feature-graphic/README.md`
- Create: `/store/privacy-policy.md`

- [ ] **Step 1: listing.md** with name, short (80 char) + long (4000 char) descriptions in EN + DE, tags, category, content rating, privacy policy URL.
- [ ] **Step 2: screenshots/README.md** enumerating the 8 required captures (Pixel 8 x3, Pixel Fold inner x2, Pixel Fold outer x1, Pixel Watch 2 x2) with purpose per shot and `adb shell screencap` instructions.
- [ ] **Step 3: feature-graphic/README.md** specifying 1024x500 PNG, Indigo #6366F1, wordmark + tagline.
- [ ] **Step 4: privacy-policy.md** minimal: no personal data collected, crash reports opt-in and anonymised, credentials in Android Keystore.

**Acceptance:** `store/` directory committed, `listing.md` is review-ready final draft, screenshot requirements enumerated.

**Dependencies:** None.

---

## Verification

After all 8 tasks land:

```bash
./gradlew clean
./gradlew detekt
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew :wear:assembleDebug
./gradlew :wear:assembleRelease
./gradlew bundleRelease
```

Then push, monitor CI until both Build & Test and Security Scan are green. Finally tag `v0.2.0-rc1` on a branch and verify release workflow.

**Smoke test on sideloaded release APK:**
1. Onboarding shows on first launch
2. SSH connect → terminal works
3. SFTP browse → editor opens file
4. Proxmox dashboard lists VMs
5. Wear OS pairing → connection visible on watch
6. Crash reporting toggle → restart → still works

---

## Threat Model & Risk Notes

| Risk | Mitigation |
|---|---|
| Release signing key leak | Keystore only in GitHub Secrets; `.gitignore` excludes `*.keystore`/`*.jks`; workflow verifies all 4 secrets; maintainer keeps offline backup |
| R8 strips reflectively-used class | Comprehensive keep rules (Task 10.3); manual smoke test of all 5 major features on minified release before tagging; recommend adding `release-smoke.yml` later |
| ACRA leaks PII | Explicit `ReportField` allowlist (no logcat, no installation id, no shared prefs); operator-controlled backend; opt-out one tap; future: strip hostnames from stack traces via BuilderPlugin |
| Onboarding battery-opt request feels manipulative | Explicit "Überspringen" button; user can change later in system settings |
| Permissions fire before user understands | All permissions user-initiated via "Grant" on explanatory screen; only POST_NOTIFICATIONS (no location/camera/contacts) |
| Baseline profile stale / overfit | Regenerate before each release; document in RELEASE.md |
| LeakCanary in release | `debugImplementation` only; verified via `dependencyInsight` |
| Wear APK signing mismatch | Phone + wear signed with same key via same env vars in release.yml |
| ACRA backend password leaked via APK unpack | `ACRA_BASIC_AUTH_PASSWORD` is embedded in every release APK. Use a per-version write-only token rotated on abuse. Operator must monitor Acrarium for spam. Future: replace with HMAC-signed requests. |
| Tapjacking on permission/battery screens | `FLAG_SECURE` on onboarding activity; 48dp minimum touch targets prevent accidental taps under overlays |
| Keystore loss or compromise | Google Play App Signing enrollment recommended (upload key ≠ signing key). Maintainer keeps offline backup. Documented in RELEASE.md. |
| R8 silent regression across library bumps | Add `:app:assembleRelease` smoke job to pr-check.yml (future), plus manual smoke test before every release tag |
| ACRA_BACKEND_URL MITM | Operator must use Let's Encrypt + HSTS; future work: certificate pinning in ACRA's OkHttp client |

---

## Execution Order

**Wave A (parallelizable):** 10.1 (PR check), 10.5 (a11y), 10.7 (ACRA), 10.8 (store scaffold)
**Wave B (parallelizable, after A):** 10.3 (R8 rules), 10.4 (onboarding)
**Wave C (sequential):** 10.6 (baseline + LeakCanary) → 10.2 (release workflow) — both depend on 10.3

**Final:** Full verification + tag `v0.2.0-rc1`.

---

## Completion Checklist

- [ ] CI: `pr-check.yml` tightened, no `continue-on-error` on quality
- [ ] CI: `dependabot.yml` grouped weekly PRs
- [ ] CI: `release.yml` verifies 4 signing secrets, builds signed AAB+APK for phone+wear
- [ ] R8: `app/proguard-rules.pro` covers all libraries
- [ ] R8: `wear/proguard-rules.pro` covers Hilt/Compose/Wearable
- [ ] R8: minified release smoke-tested on real device
- [ ] Onboarding: 4-screen flow, DataStore-gated, skippable permissions
- [ ] Onboarding: MainActivity gated
- [ ] A11y: 7 primary screens audited
- [ ] A11y: 48dp touch targets verified
- [ ] A11y: TalkBack manual pass
- [ ] Performance: `:baselineprofile` module created, `:app` consumer wired
- [ ] Performance: `baseline-profile.yml` workflow runs
- [ ] Performance: LeakCanary in debug only
- [ ] Crash: ACRA wired, opt-in toggle, debug no-op
- [ ] Crash: PII-minimal report fields
- [ ] Store: `store/` scaffold with listing, screenshots, feature graphic, privacy policy
- [ ] All: detekt clean, lint clean, tests passing, CI GREEN
- [ ] **Manual:** real keystore generated, secrets uploaded
- [ ] **Manual:** baseline profile generated and committed
- [ ] **Manual:** real screenshots + feature graphic
- [ ] **Manual:** Acrarium backend deployed, `ACRA_BACKEND_URL` set

## Known Limitations

1. Baseline profile empty until first manual run
2. Store assets are placeholders
3. Crash reporting backend operator-provided
4. Crashlytics intentionally not used
5. Phase 9 skipped — no billing/premium R8 rules
