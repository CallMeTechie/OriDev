#!/usr/bin/env bash
# Phase 11 §P0.7 — mockup-hash drift guard.
#
# Hashes every Mockups/*.html file and compares to a baseline committed
# under .github/mockup-hash.txt. If they differ, the script emits a CI
# warning prompting the developer to update the hash and revisit the
# Phase 11 deltas — design changes in the mockups should ALWAYS trigger
# a conversation, not silently propagate.
#
# Usage:
#   bash .github/ci/check-mockup-hash.sh             # real run
#   bash .github/ci/check-mockup-hash.sh --self-test # positive control

set -u
# NOTE: deliberately NOT `set -o pipefail` — sha256sum on empty input is fine.

mode="${1:-check}"

if [ "$mode" = "--self-test" ]; then
    fixture_dir=".github/fixtures/mockups-fixture"
    baseline=".github/fixtures/mockups-fixture-hash.txt"

    if [ ! -d "$fixture_dir" ]; then
        echo "::error::Self-test fixture dir missing at $fixture_dir"
        exit 2
    fi
    if [ ! -f "$baseline" ]; then
        echo "::error::Self-test baseline missing at $baseline"
        exit 2
    fi

    current=$(find "$fixture_dir" -name '*.html' | sort | xargs sha256sum | sha256sum | awk '{print $1}')
    expected=$(cat "$baseline")

    # The fixture baseline is intentionally a known-WRONG hash. If the
    # script's hashing pipeline actually produces that wrong value, the
    # script has regressed and is silently accepting bad input — fail.
    if [ "$current" = "$expected" ]; then
        echo "::error::Self-test FAILED: hashes match when fixture baseline is supposed to be wrong. Hashing pipeline has regressed."
        exit 2
    else
        echo "::notice::Self-test OK: mismatch correctly detected (current=$current, baseline=$expected)."
        exit 0
    fi
fi

# Real run: compare actual mockups against the committed hash.
if [ ! -f .github/mockup-hash.txt ]; then
    echo "::warning::Hash file .github/mockup-hash.txt missing — skipping check (first-run after PR 4b merges or file was removed)."
    exit 0
fi

if [ ! -d Mockups ]; then
    echo "::warning::Mockups/ directory missing — skipping check."
    exit 0
fi

current=$(find Mockups -name '*.html' | sort | xargs sha256sum | sha256sum | awk '{print $1}')
expected=$(cat .github/mockup-hash.txt)

if [ "$current" != "$expected" ]; then
    echo "::warning::Mockups changed since the Phase 11 hash was recorded."
    echo "::warning::  expected: $expected"
    echo "::warning::  current:  $current"
    echo "::warning::Update .github/mockup-hash.txt and revisit Phase 11 deltas (any P2 screen affected by the mockup change must re-do its mockup diff)."
fi

# Always exit 0 — drift is a warning, not a hard failure (mockups can
# legitimately evolve after Phase 11 ships).
exit 0
