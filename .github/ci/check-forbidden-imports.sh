#!/usr/bin/env bash
# Phase 11 §P0.10.9 — forbidden-import grep guard.
#
# Blocks new code in feature-* / core/core-ui/src/main/kotlin/dev/ori/core/ui/components
# from importing:
#   - androidx.compose.material.icons.*  (use dev.ori.core.ui.icons.lucide.LucideIcons)
#   - androidx.compose.material3.{TopAppBar, MediumTopAppBar, LargeTopAppBar, CenterAlignedTopAppBar}
#     (use dev.ori.core.ui.components.OriTopBar — added in PR 4a)
#   - androidx.compose.material3.*       (wildcard hides forbidden TopAppBar usage)
#
# Aliased imports (e.g. `import ... as M3Bar`) are explicitly caught.
#
# Diff scope: only files CHANGED on the current PR branch are checked. The
# rationale (plan v6 §P0.10): un-migrated feature screens still legitimately
# import the forbidden symbols today, and a full-tree scan would block every
# unrelated PR until Phase 11 P2 finishes migrating each screen. Diff scope
# means only NEW violations fail; pre-existing imports are cleaned up
# naturally as each P2 sub-phase migrates its target screen.
#
# Usage:
#   bash .github/ci/check-forbidden-imports.sh             # real run on PR diff
#   bash .github/ci/check-forbidden-imports.sh --self-test # positive control

set -u
# NOTE: deliberately NOT `set -o pipefail`. grep returns exit 1 on no-match,
# which is the happy path here — pipefail would kill the script on every
# clean run.

fail=0
self_test=false

if [ "${1:-}" = "--self-test" ]; then
    self_test=true
fi

# ---- Resolve scope file list -------------------------------------------------

if [ "$self_test" = "true" ]; then
    # Self-test mode: scan the fixture file directly. Expect violations.
    scope_files=(.github/fixtures/bad-imports.kt.txt)
    if [ ! -f "${scope_files[0]}" ]; then
        echo "::error::Self-test fixture missing at ${scope_files[0]}"
        exit 2
    fi
else
    # Real run: diff against the merge base of origin/<base>.
    base_ref="${GITHUB_BASE_REF:-main}"

    # Use null-delimited output so paths with spaces survive (mapfile -d '').
    if ! mapfile -d '' scope_files < <(
        git diff --name-only -z "origin/${base_ref}...HEAD" -- \
            'feature-*/src/*.kt' \
            'core/core-ui/src/main/kotlin/dev/ori/core/ui/components/*.kt' \
            2>/dev/null
    ); then
        echo "::warning::git diff failed (shallow clone? use fetch-depth: 0); skipping check"
        exit 0
    fi

    if [ "${#scope_files[@]}" -eq 0 ] || { [ "${#scope_files[@]}" -eq 1 ] && [ -z "${scope_files[0]}" ]; }; then
        echo "No Kotlin files in scope on this diff — skip."
        exit 0
    fi
fi

# ---- Helper: run one regex against the scope, optionally restricted by glob -

run_grep() {
    local label="$1"
    local pattern="$2"
    local restrict_glob="${3:-}"
    local targets=("${scope_files[@]}")

    # Self-test mode bypasses the feature-* glob restriction so that the
    # fixture (which lives under .github/fixtures/) still triggers the
    # Material 3 TopAppBar checks. Real runs honor the restriction so that
    # core-ui's own OriTopBar.kt (which IS allowed to import M3 internals)
    # never trips the rule.
    if [ -n "$restrict_glob" ] && [ "$self_test" != "true" ]; then
        local filtered=()
        for f in "${targets[@]}"; do
            # shellcheck disable=SC2254
            case "$f" in
                $restrict_glob) filtered+=("$f") ;;
            esac
        done
        targets=("${filtered[@]}")
    fi

    if [ "${#targets[@]}" -eq 0 ]; then
        return 0
    fi

    # Each grep call wrapped with `|| true` so its exit-1-on-no-match
    # doesn't propagate via set -e (which we're not using anyway, but be safe).
    local output
    output=$(printf '%s\n' "${targets[@]}" | xargs -r -I{} grep -EnH "$pattern" "{}" 2>/dev/null || true)
    if [ -n "$output" ]; then
        echo "::error::$label"
        echo "$output"
        fail=1
    fi
}

# ---- Forbidden patterns ------------------------------------------------------

# 1) Material Icons — direct or aliased imports
run_grep \
    "Material Icons imported. Use dev.ori.core.ui.icons.lucide.LucideIcons instead." \
    '^import[[:space:]]+androidx\.compose\.material\.icons\.[A-Za-z0-9_.]+([[:space:]]+as[[:space:]]+[A-Za-z_][A-Za-z0-9_]*)?$'

# 2) Material Icons — wildcard import
run_grep \
    "Wildcard Material Icons import found. Use specific LucideIcons.* symbols instead." \
    '^import[[:space:]]+androidx\.compose\.material\.icons\..*\.\*$'

# 3) Material 3 TopAppBar family — direct or aliased (feature modules only)
run_grep \
    "Material3 TopAppBar imported in feature code. Use OriTopBar (added in PR 4a) instead." \
    '^import[[:space:]]+androidx\.compose\.material3\.(TopAppBar|MediumTopAppBar|LargeTopAppBar|CenterAlignedTopAppBar)([[:space:]]+as[[:space:]]+[A-Za-z_][A-Za-z0-9_]*)?$' \
    'feature-*'

# 4) Wildcard material3 import in feature modules (hides bare TopAppBar usage)
run_grep \
    "Wildcard material3 import in feature code is forbidden (hides TopAppBar usage). Import specific symbols." \
    '^import[[:space:]]+androidx\.compose\.material3\.\*$' \
    'feature-*'

# ---- Self-test verdict -------------------------------------------------------

if [ "$self_test" = "true" ]; then
    if [ "$fail" -eq 0 ]; then
        echo "::error::Self-test FAILED: grep did not detect forbidden patterns in fixture. Regexes have regressed."
        exit 2
    else
        echo "::notice::Self-test OK: fixture correctly triggered violations."
        exit 0
    fi
fi

exit "$fail"
