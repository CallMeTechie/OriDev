# Releasing Ori:Dev

Operator runbook for cutting a signed Ori:Dev release.

## Cutting a release

```bash
git tag v0.2.0
git push origin v0.2.0
```

Tag push triggers `.github/workflows/release.yml`, which:

1. Verifies all signing secrets are present (fails fast if any are missing).
2. Runs detekt, Android Lint, and the full unit test suite.
3. Decodes the keystore from `KEYSTORE_BASE64`.
4. Builds signed AAB + APK for both `:app` and `:wear` (R8 minified, baseline profile applied).
5. Verifies signing with `apksigner verify --min-sdk-version=34`.
6. Generates a Conventional-Commits-grouped changelog from commits since the previous tag.
7. Publishes a GitHub Release with all four artifacts (phone AAB+APK, wear AAB+APK).
8. Cleans up the keystore from the runner regardless of success or failure.

## Continuous Master Builds

Every successful push to `master` automatically produces a rolling prerelease tagged `continuous-master`. The flow:

1. The "Build & Test" workflow runs on master and (on success) emits a `workflow_run` event.
2. `release.yml` listens for that event, re-runs verification (detekt, lint, tests) on master HEAD, and rebuilds signed phone + wear AAB/APK.
3. Version metadata for continuous builds is injected via Gradle properties:
   - `versionCode = github.run_number`
   - `versionName = 0.2.0-master.<run_number>+<shortsha>`
4. The previous `continuous-master` GitHub release and tag are deleted, then recreated with the new artifacts. There is always exactly one "Latest Master Build (<n>)" prerelease.

To download the latest master build:

```bash
gh release download continuous-master --repo <org>/OriDev
```

### Graceful skip when secrets are missing

If `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, or `KEY_PASSWORD` are not configured, the continuous release job logs a warning and exits 0 instead of failing. The authoritative `Build & Test` workflow's status is unaffected, so master CI stays green.

Tag releases (`v*` push) still fail loudly if signing secrets are missing — proper releases must always be signed.

## Google Play Store upload (optional)

Play Store upload is gated on the `PLAY_SERVICE_ACCOUNT_JSON` GitHub secret. If absent, the upload step is skipped silently with a notice.

To enable:

1. Create a Google Cloud service account with the `Service Account User` role and grant it access to your Play Console project (Play Console -> Setup -> API access).
   See: https://developers.google.com/android-publisher/getting_started
2. Download the JSON key and store its full contents in the `PLAY_SERVICE_ACCOUNT_JSON` GitHub secret.
3. Add the publisher plugin to `app/build.gradle.kts`:
   ```kotlin
   plugins {
       // ...existing plugins...
       id("com.github.triplet.play") version "3.10.1"
   }

   play {
       serviceAccountCredentials.set(file(System.getenv("PLAY_SERVICE_ACCOUNT_JSON_PATH") ?: "/dev/null"))
       track.set("internal")
       defaultToAppBundles.set(true)
       releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
   }
   ```
   And in `settings.gradle.kts` `pluginManagement`, ensure `gradlePluginPortal()` is in the repository list.
4. The first version must be uploaded manually to the Play Console — the Publisher API cannot create a brand-new app listing. After the first manual upload, subsequent CI runs will publish to the `internal` track as `DRAFT` automatically.
5. An operator promotes from `internal/DRAFT` -> `production` in the Play Console.

The publisher plugin is intentionally NOT wired into `app/build.gradle.kts` by default — the release workflow detects whether the `publishReleaseBundle` task exists and only runs it when present.

## Required GitHub Secrets

Settings -> Secrets and variables -> Actions:

| Secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded upload keystore (`base64 -w0 release.keystore`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Key password |
| `ACRA_BACKEND_URL` | Crash reporter ingest URL |
| `ACRA_BASIC_AUTH_LOGIN` | ACRA HTTP basic auth login |
| `ACRA_BASIC_AUTH_PASSWORD` | ACRA HTTP basic auth password |
| `PLAY_SERVICE_ACCOUNT_JSON` | (optional) Google Play API service account JSON for automatic upload to the `internal` track |

## Generating an upload keystore

```bash
keytool -genkey -v -keystore release.keystore \
        -keyalg RSA -keysize 2048 -validity 10000 -alias ori
base64 -w0 release.keystore > keystore.base64
```

Paste the contents of `keystore.base64` into the `KEYSTORE_BASE64` secret. Never commit `release.keystore` or `keystore.base64` (both are covered by `.gitignore`).

## Play App Signing

Enroll the app in Play App Signing. The keystore generated above is the **upload key**, not the signing key. Google holds the signing key in their HSM. If the upload key is ever lost or compromised, contact Play support to rotate it; the signing key never changes.

## Baseline profile regeneration

The baseline profile lives at `app/src/main/baseline-prof.txt` and is shipped to all users via ProfileInstaller. To regenerate after significant startup-path changes:

1. Trigger the `baseline-profile.yml` workflow manually (Actions tab -> Run workflow).
2. Download the `baseline-profile` artifact from the run.
3. Replace `app/src/main/baseline-prof.txt` with the generated file.
4. Commit and push: `git commit -am "perf: regenerate baseline profile"`.

## Rollback

If a release is broken:

```bash
git tag -d v0.2.0
git push origin :refs/tags/v0.2.0
# fix the bug, commit, then re-tag and re-push
```

WARNING: re-pushing the same tag re-triggers the release workflow and republishes the GitHub Release. Prefer cutting a new patch version (`v0.2.1`) for users who already downloaded the broken build.

## Pre-release smoke test checklist

Run through this before pushing a tag:

- [ ] `./gradlew detekt lint test` is green locally
- [ ] `./gradlew :app:assembleRelease :wear:assembleRelease` succeeds locally
- [ ] Manual install on Pixel Fold: SSH connect, SFTP browse, terminal session
- [ ] Wear companion installs and pairs
- [ ] Onboarding flow displays once on fresh install
- [ ] No crashes in `adb logcat *:E` during a 5-minute walkthrough
- [ ] `versionCode` and `versionName` in `app/build.gradle.kts` and `wear/build.gradle.kts` are bumped
