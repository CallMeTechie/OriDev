# Releasing Ori:Dev

Operator runbook for cutting a signed Ori:Dev release.

For one-time setup of GitHub secrets (keystore, Play Store service account,
ACRA backend) see **[docs/SECRETS_SETUP.md](docs/SECRETS_SETUP.md)**. That
guide is the single source of truth for everything CI needs.

## Release Flow

Three entry points, all handled by `.github/workflows/release.yml`:

1. **Master push (automatic)** — bump inferred from the HEAD commit subject.
   `feat:` → minor, `fix:` → patch, `feat!:` / `BREAKING CHANGE:` → major.
   Any other prefix is silently skipped (no release).
2. **`workflow_dispatch`** — maintainer picks `patch`/`minor`/`major`
   explicitly via Actions → Release → Run workflow.
3. **`v*.*.*` tag push** — the tag IS the version, no bump applied.

```
feat/fix commit -> master
        |
        v
   Release workflow (push: branches:[master])
        |
        v
   verify (detekt, lint, test)
        |
        v
   build signed AAB/APK (phone + wear, R8 minified)
        |
        v
   push tag vX.Y.Z + open release/vX.Y.Z PR (auto-merged)
        |
        v
   GitHub Release with assets + Play Store Internal (if secret set)
```

The `chore(release): vX.Y.Z` bump for `version.properties` ships as a PR
because branch protection blocks direct pushes to master. The workflow
enables auto-merge on that PR, so it merges itself once CI goes green.
`version.properties` and `**.md` are in `paths-ignore`, preventing the
bump from re-triggering the workflow (infinite-loop guard).

## Versioning — Conventional Commits

| Commit prefix            | Bump    | Example           |
|--------------------------|---------|-------------------|
| `feat: ...`              | MINOR   | `0.2.0` → `0.3.0` |
| `fix: ...`               | PATCH   | `0.2.0` → `0.2.1` |
| `feat!: ...` / `BREAKING CHANGE:` | MAJOR | `0.2.0` → `1.0.0` |
| `chore/docs/ci/refactor/test/style/perf/build` | none | no release |

Scopes are allowed: `feat(editor): ...`, `fix(wear): ...`.
`VERSION_CODE` is incremented on every bump (Play Store requires monotonic).

## Single Source of Truth: version.properties

`/version.properties` holds the current version:

```properties
VERSION_MAJOR=0
VERSION_MINOR=2
VERSION_PATCH=0
VERSION_CODE=1
```

Both `app/build.gradle.kts` and `wear/build.gradle.kts` read from this file
at configuration time. **Never edit by hand** — `.github/workflows/release.yml`
overwrites it via the auto-merged `chore(release)` PR.

## What the Release workflow does

Same 9 steps regardless of entry point:

1. **Pre-Release Verification** — detekt, Android Lint, full unit test suite.
2. **Check signing secrets** — fails fast if any KEYSTORE_* secret missing.
3. **Decode keystore** from `KEYSTORE_BASE64`.
4. **Build** signed AAB + APK for `:app` and `:wear` (R8 minified, baseline profile).
5. **Verify** `apksigner verify --min-sdk-version=34` with v2/v3 scheme check.
6. **Changelog** grouped by Conventional Commits type, from previous tag.
7. **Tag + version bump PR** — push `vX.Y.Z` tag, open auto-merging
   `release/vX.Y.Z` PR with `version.properties` bumped.
8. **GitHub Release** with phone AAB/APK, wear AAB/APK, mapping.txt, seeds.txt, usage.txt.
9. **Play Store Internal** upload as DRAFT via gradle-play-publisher plugin.
10. **Cleanup** keystore and secrets from the runner regardless of outcome.

> The workflow has **no step-level skipping**: if any secret is missing, the
> job fails immediately with a pointer to `docs/SECRETS_SETUP.md`. This is
> intentional — proper releases must be complete.

## Required repo settings

The auto-release flow assumes the following are set on the repository
(admin-only, one-time):

- Actions → General → Workflow permissions → **Allow GitHub Actions to create and approve pull requests** (✓)
- General → Pull Requests → **Allow auto-merge** (✓)
- General → Pull Requests → **Automatically delete head branches** (✓)

Without these, the `release/vX.Y.Z` bump PR can't be opened or can't
auto-merge.

### Required secret: `RELEASE_PAT`

GitHub deliberately does **not** run workflows on pushes authenticated
with the repo's `GITHUB_TOKEN` (anti-loop safety). The `release/vX.Y.Z`
bump PR therefore comes into the world with *no* status checks attached
and its auto-merge stalls in `BLOCKED` forever. To fix this, the release
workflow pushes the bump branch with a maintainer-owned PAT when the
`RELEASE_PAT` secret is set.

One-time setup:

1. GitHub → Settings → Developer settings → Personal access tokens →
   Fine-grained tokens → **Generate new token**.
2. Repository access: **Only select repositories → CallMeTechie/OriDev**.
3. Permissions: **Contents: Read and write**, **Pull requests: Read and write**.
4. Expiration: 1 year (calendar reminder to rotate).
5. Copy the token. In the repo: Settings → Secrets and variables →
   Actions → **New repository secret** → name `RELEASE_PAT`, paste the
   token.

Without `RELEASE_PAT` the workflow falls back to `GITHUB_TOKEN` and
emits a warning; you'll then need a manual empty commit on the bump
branch to kick off CI before auto-merge can proceed.

## Manual trigger (if needed)

If a commit with a non-release prefix (e.g. `chore:`) needs to ship, or
you want to force a specific bump:

- Actions → Release → Run workflow → pick `patch`/`minor`/`major`.

Or push a tag directly:

```bash
git tag -a v0.3.0 -m "Release v0.3.0"
git push origin v0.3.0
```

## Rollback

If a release is broken, **prefer cutting a new patch version** rather than
re-pushing a tag. Re-pushing the same tag republishes the GitHub Release and
confuses Play Store consumers who already downloaded the broken build.

```bash
# Fix the bug, commit as fix: ..., push to master.
# The master-push trigger on release.yml will cut v0.3.1 automatically.
```

If you must delete a tag:

```bash
git tag -d v0.3.0
git push origin :refs/tags/v0.3.0
gh release delete v0.3.0 --yes --repo <org>/OriDev
```

## Baseline profile regeneration

The baseline profile lives at `app/src/main/baseline-prof.txt` and is shipped
to all users via ProfileInstaller. To regenerate after significant startup-path
changes:

1. Trigger the `baseline-profile.yml` workflow manually (Actions tab → Run workflow).
2. Download the `baseline-profile` artifact from the run.
3. Replace `app/src/main/baseline-prof.txt` with the generated file.
4. Commit and push: `git commit -am "perf: regenerate baseline profile"`.

## Pre-release smoke test checklist

Run through this before pushing a `feat:` / `fix:` commit that will trigger a
release:

- [ ] `./gradlew detekt lint test` is green locally
- [ ] `./gradlew :app:assembleRelease :wear:assembleRelease` succeeds locally
- [ ] Manual install on Pixel Fold: SSH connect, SFTP browse, terminal session
- [ ] Wear companion installs and pairs
- [ ] Onboarding flow displays once on fresh install
- [ ] No crashes in `adb logcat *:E` during a 5-minute walkthrough

## Play App Signing

The keystore generated in [docs/SECRETS_SETUP.md §1](docs/SECRETS_SETUP.md) is
the **upload key**, not the signing key. Google holds the signing key in their
HSM. If the upload key is ever lost or compromised, contact Play support to
rotate it; the signing key never changes.
