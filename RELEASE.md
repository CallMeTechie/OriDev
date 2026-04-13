# Releasing Ori:Dev

Operator runbook for cutting a signed Ori:Dev release.

For one-time setup of GitHub secrets (keystore, Play Store service account,
ACRA backend) see **[docs/SECRETS_SETUP.md](docs/SECRETS_SETUP.md)**. That
guide is the single source of truth for everything CI needs.

## Release Flow (fully automatic)

The pipeline is driven by **Conventional Commits** — no manual version bumps.

```
feat/fix commit -> master
        |
        v
  Build & Test (CI)   --green-->  Auto Tag workflow
                                       |
                                       v
                                 version.properties bumped
                                 chore(release): vX.Y.Z commit
                                 annotated tag vX.Y.Z pushed
                                       |
                                       v
                                 Release workflow
                                       |
                                       v
                          signed AAB/APK (phone + wear)
                          GitHub Release created
                          Play Store Internal (DRAFT)
```

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
at configuration time. **Never edit by hand** — `.github/workflows/auto-tag.yml`
overwrites it.

## What the Release workflow does

Triggered only on `v*` tag push (which `auto-tag.yml` creates):

1. **Pre-Release Verification** — detekt, Android Lint, full unit test suite.
2. **Check signing secrets** — fails fast if any KEYSTORE_* secret missing.
3. **Decode keystore** from `KEYSTORE_BASE64`.
4. **Build** signed AAB + APK for `:app` and `:wear` (R8 minified, baseline profile).
5. **Verify** `apksigner verify --min-sdk-version=34` with v2/v3 scheme check.
6. **Changelog** grouped by Conventional Commits type, from previous tag.
7. **GitHub Release** with phone AAB/APK, wear AAB/APK, mapping.txt, seeds.txt, usage.txt.
8. **Play Store Internal** upload as DRAFT via gradle-play-publisher plugin.
9. **Cleanup** keystore and secrets from the runner regardless of outcome.

> The workflow has **no step-level skipping**: if any secret is missing, the
> job fails immediately with a pointer to `docs/SECRETS_SETUP.md`. This is
> intentional — proper releases must be complete.

## Manual trigger (if needed)

Only if the auto-tag workflow is somehow bypassed:

```bash
# Edit version.properties by hand
vim version.properties
git add version.properties
git commit -m "chore(release): v0.3.0"
git tag -a v0.3.0 -m "Release v0.3.0"
git push origin master
git push origin v0.3.0
```

## Rollback

If a release is broken, **prefer cutting a new patch version** rather than
re-pushing a tag. Re-pushing the same tag republishes the GitHub Release and
confuses Play Store consumers who already downloaded the broken build.

```bash
# Fix the bug, commit as fix: ..., push to master.
# Auto-tag will create v0.3.1 automatically.
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
