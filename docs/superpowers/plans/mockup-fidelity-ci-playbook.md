# Phase 11 — Mockup Fidelity CI Playbook

Companion document for `2026-04-13-oridev-phase11-mockup-fidelity.md`. Covers
how to run, debug, and extend the Phase 11 CI checks **locally** without the
push-wait-retry cycle that GitHub Actions normally requires.

## What's wired in

The Phase 11 PR stack adds the following checks beyond the project's
existing `pr-check.yml` / `build.yml` / `release.yml` / `security.yml`
/ `baseline-profile.yml` workflows:

| PR | Check | Where | Purpose |
|---|---|---|---|
| PR 1 | `:core:core-fonts:checkCoreFontsLeakage` | `core/core-fonts/build.gradle.kts` | no `androidx.compose.material3` in core-fonts deps |
| PR 1 | `:core:core-fonts:checkFontBudget` | `core/core-fonts/build.gradle.kts` | combined TTFs ≤ 1.5 MB |
| PR 3 | `bash .github/ci/check-forbidden-imports.sh` | shell + fixture | diff-scoped grep guard for `material.icons.*` and `material3.TopAppBar` family |
| PR 3.5 | `:wear:checkWearLeakage` | `wear/build.gradle.kts` | no `androidx.compose.material3` (phone) in wear deps |
| PR 3.5 | `static-import-checks` job | `pr-check.yml` + `build.yml` | wires the grep script into CI |
| PR 3.5 | `verify-isolation` step | `pr-check.yml` + `build.yml` | wires the three Gradle leak/budget tasks into CI |
| PR 3.5 | `ci-self-test.yml` | weekly cron | positive-control canaries against fixtures |
| PR 3.5 | `bash .github/ci/check-mockup-hash.sh` | shell + fixture | drift guard for `Mockups/*.html` (added in PR 4b alongside `.github/mockup-hash.txt`) |
| PR 4b | `mockup-hash-check` job | `pr-check.yml` | wires the hash script |

## Running checks locally

### Forbidden-imports grep guard

```bash
# Real run against the current branch's diff vs origin/master:
bash .github/ci/check-forbidden-imports.sh

# Positive control (asserts the script catches the fixture's bad imports):
bash .github/ci/check-forbidden-imports.sh --self-test
```

The script auto-detects `GITHUB_BASE_REF` for the diff target. When run
locally, set it manually if your local `master` differs:

```bash
GITHUB_BASE_REF=master bash .github/ci/check-forbidden-imports.sh
```

### Mockup-hash drift guard

```bash
# Real run (warns if Mockups/*.html drifted from the committed hash):
bash .github/ci/check-mockup-hash.sh

# Positive control (asserts the script catches an intentional mismatch):
bash .github/ci/check-mockup-hash.sh --self-test
```

### Gradle verification tasks

```bash
./gradlew :core:core-fonts:checkCoreFontsLeakage   # phone material3 must be absent
./gradlew :core:core-fonts:checkFontBudget         # TTFs must fit budget
./gradlew :wear:checkWearLeakage                   # phone material3 must be absent
```

Override the budget for testing:

```bash
./gradlew :core:core-fonts:checkFontBudget -Pfont.budget=2000000   # raise to 2 MB
./gradlew :core:core-fonts:checkFontBudget -Pfont.budget=0         # force failure
```

The CI Self-Test workflow uses `-Pfont.budget=0` as a positive control to
verify the task still rejects bad input. If you change the task and it
stops failing on `-Pfont.budget=0`, the weekly canary alerts.

## Iterating on workflow YAML with `act`

Editing GitHub Actions YAML normally means push → wait 5–10 min → see
that the regex was wrong → fix → push. The `nektos/act` tool runs jobs
locally inside a Docker container, eliminating the round trip.

### Install

```bash
# macOS
brew install act

# Linux
curl https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash
```

Requires Docker. On Apple Silicon Macs add `--container-architecture
linux/amd64` to every `act` invocation (the runner images are amd64-only).

### Run a single job

```bash
# Run only the static-import-checks job from pr-check.yml:
act pull_request -j static-import-checks --container-architecture linux/amd64

# Run the weekly canary on demand:
act schedule -W .github/workflows/ci-self-test.yml
```

### Common pitfalls

- **Out of memory** on macOS: `act` defaults to 2 GB; bump via
  `--memory 4g` or set Docker Desktop's memory limit higher.
- **Missing secrets**: the keystore-related steps in `build.yml` need
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, etc. Stub them via
  `act ... --secret-file .secrets.local` (gitignored).
- **Wrong runner image**: `act` defaults to `node:18-buster-slim` which
  doesn't have JDK 21. Override per workflow:
  `act ... -P ubuntu-latest=catthehacker/ubuntu:act-latest` (or pin a
  version).

## Updating `.github/mockup-hash.txt`

When the design team updates `Mockups/*.html` and the new hash is
intentional:

```bash
# Recompute and commit:
find Mockups -name '*.html' | sort | xargs sha256sum | sha256sum | awk '{print $1}' > .github/mockup-hash.txt
git add .github/mockup-hash.txt Mockups/
git commit -m "chore(mockups): update Phase 11 hash after design refresh"
```

The PR description should explain which mockup changed and which P2
sub-phase needs to redo its visual diff against the new mockup.

## Debugging a failing positive-control self-test

If `ci-self-test.yml` fires a failure overnight, follow this order:

1. **Forbidden-imports**: re-run `bash .github/ci/check-forbidden-imports.sh
   --self-test` locally. If the regex stopped matching, check for whitespace
   normalisation drift in the fixture file (`.github/fixtures/bad-imports.kt.txt`)
   or in the regex pattern itself.
2. **Mockup-hash**: re-run `bash .github/ci/check-mockup-hash.sh --self-test`.
   If the hashing pipeline broke, check for a `find`/`sha256sum`/`awk`
   version drift on the runner image (Ubuntu LTS bumps occasionally
   tweak coreutils behavior).
3. **Font budget**: re-run `./gradlew :core:core-fonts:checkFontBudget
   -Pfont.budget=0`. If the task silently passes, check whether the
   `font.budget` property is still being read or whether the
   `walkTopDown` is hitting an empty directory.
4. **Leak checks**: re-run `./gradlew :core:core-fonts:checkCoreFontsLeakage
   :wear:checkWearLeakage`. If they fire on real material3 entries, a
   transitive dep was added — `gradle :module:dependencies` to find
   the offending chain, then fix the dep declaration.
