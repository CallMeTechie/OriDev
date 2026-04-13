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
