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

# ---- Resolve scope file list + diff content ---------------------------------
#
# Real-mode semantics (cycle 5 fix from PR #41 first-run failure):
#   - Only NEW lines added by the current PR are checked, not the whole file.
#     v1 of the script greppped the whole file and any PR that touched a
#     pre-existing un-migrated screen tripped on its existing material.icons
#     imports. Plan v6 §P0.10 explicitly said "only NEW violations fail",
#     and grepping whole files violated that.
#   - Per-file diff content is collected up-front into a Bash associative
#     array (`added_lines[$file]`) so the regex helper can iterate over it.
#
# Self-test semantics (unchanged): the script scans the entire fixture file
# at .github/fixtures/bad-imports.kt.txt and expects violations.

declare -A added_lines

if [ "$self_test" = "true" ]; then
    # Self-test mode: pretend the whole fixture file is "added" so all 4
    # forbidden patterns trigger.
    fixture=".github/fixtures/bad-imports.kt.txt"
    if [ ! -f "$fixture" ]; then
        echo "::error::Self-test fixture missing at $fixture"
        exit 2
    fi
    added_lines["$fixture"]=$(cat "$fixture")
    scope_files=("$fixture")
else
    base_ref="${GITHUB_BASE_REF:-main}"

    # First: list of changed files in scope.
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

    # For each changed file, get only the lines this PR ADDED (not the whole
    # file). Diff format with -U0 (no context lines) yields:
    #
    #   diff --git a/foo.kt b/foo.kt
    #   index abc..def 100644
    #   --- a/foo.kt
    #   +++ b/foo.kt
    #   @@ -10,0 +11,2 @@
    #   +import androidx.compose.foo
    #   +import androidx.compose.bar
    #
    # Filter: lines starting with `+` but NOT `+++` (which is the file header).
    # Strip the leading `+` so the regex sees just the source line content.
    for f in "${scope_files[@]}"; do
        [ -z "$f" ] && continue
        added_lines["$f"]=$(
            git diff -U0 "origin/${base_ref}...HEAD" -- "$f" 2>/dev/null \
                | grep -E '^\+[^+]' \
                | sed 's/^+//' \
                || true
        )
    done
fi

# ---- Helper: run one regex against ADDED lines for each file in scope -------

run_grep() {
    local label="$1"
    local pattern="$2"
    local restrict_glob="${3:-}"
    local file_output
    local found=0

    for f in "${scope_files[@]}"; do
        [ -z "$f" ] && continue

        # Skip files outside the restrict_glob in real mode. Self-test mode
        # bypasses the glob so the fixture (under .github/fixtures/) still
        # triggers the Material 3 TopAppBar checks.
        if [ -n "$restrict_glob" ] && [ "$self_test" != "true" ]; then
            # shellcheck disable=SC2254
            case "$f" in
                $restrict_glob) ;;
                *) continue ;;
            esac
        fi

        local content="${added_lines[$f]}"
        [ -z "$content" ] && continue

        file_output=$(printf '%s\n' "$content" | grep -E "$pattern" || true)
        if [ -n "$file_output" ]; then
            if [ "$found" -eq 0 ]; then
                echo "::error::$label"
                found=1
            fi
            # Print each matching added line, prefixed with the file path.
            printf '%s\n' "$file_output" | sed "s|^|$f: |"
            fail=1
        fi
    done
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

# 5) Cross-feature imports — feature modules must not import from other feature
#    modules EXCEPT the explicitly allowed symbols below:
#      - dev.ori.feature.premium.ui.AdSlotHost  (ad placement host composable)
#      - dev.ori.feature.premium.ui.PremiumGate  (premium gating composable)
#    Any other dev.ori.feature.* import inside a feature-* source file is
#    forbidden. We cannot use a negative lookahead in ERE, so we run a two-pass
#    check: first find ALL cross-feature imports, then filter out the allowlist.

CROSS_FEATURE_ALLOWLIST=(
    'dev.ori.feature.premium.ui.AdSlotHost'
    'dev.ori.feature.premium.ui.PremiumGate'
    'dev.ori.feature.premium.ui.BandwidthThrottleSlider'
)

for f in "${scope_files[@]}"; do
    [ -z "$f" ] && continue
    # Only check feature-* source files
    case "$f" in
        feature-*) ;;
        *) continue ;;
    esac
    # Self-test fixture bypass
    if [ "$self_test" = "true" ]; then
        case "$f" in
            feature-*) ;;
            *) continue ;;
        esac
    fi
    local_content="${added_lines[$f]}"
    [ -z "$local_content" ] && continue
    # Find cross-feature imports (import dev.ori.feature.XXX.*)
    cross_imports=$(printf '%s\n' "$local_content" \
        | grep -E '^import[[:space:]]+dev\.ori\.feature\.[a-z]+\.' || true)
    [ -z "$cross_imports" ] && continue
    # Filter out self-imports: extract module name from file path (e.g.
    # feature-transfers -> transfers) and skip imports from that module.
    module_name=$(echo "$f" | sed 's|^feature-||; s|/.*||')
    cross_imports=$(printf '%s\n' "$cross_imports" \
        | grep -vE "^import[[:space:]]+dev\.ori\.feature\.${module_name}\." || true)
    [ -z "$cross_imports" ] && continue
    # Filter out allowlisted symbols
    for allowed in "${CROSS_FEATURE_ALLOWLIST[@]}"; do
        escaped=$(printf '%s' "$allowed" | sed 's/\./\\./g')
        cross_imports=$(printf '%s\n' "$cross_imports" \
            | grep -vE "^import[[:space:]]+${escaped}$" || true)
    done
    [ -z "$cross_imports" ] && continue
    echo "::error::Cross-feature import detected. Feature modules may only import AdSlotHost and PremiumGate from feature-premium."
    printf '%s\n' "$cross_imports" | sed "s|^|$f: |"
    fail=1
done

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
