package dev.ori.core.ui.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * Phase 11 §P0.9 — multi-preview annotation that renders any composable in
 * **both** Pixel Fold form factors so reviewers can visually diff against the
 * mockups under `/Mockups/` for both folded (cover) and unfolded (inner)
 * displays in a single Android Studio preview pane.
 *
 * **Usage:**
 *
 * ```kotlin
 * @MockupPreviews
 * @Composable
 * private fun SettingsScreenMockupPreview() {
 *     OriDevTheme {
 *         SettingsContent(state = ..., onCrashReportingChanged = {})
 *     }
 * }
 * ```
 *
 * **Device specs (cycle 4 finding #6 corrected):** the Compose Preview DSL
 * accepts **dp** in the `spec:` string, **not px**. Pixel Fold dimensions:
 *
 * | Display | dp | dpi |
 * |---|---|---|
 * | Unfolded (inner) | 2208 × 1840 | 408 |
 * | Folded (cover)   | 1080 × 2092 | 420 |
 *
 * **Caveat:** previews are visual-only — fold-state-dependent logic that uses
 * `currentWindowAdaptiveInfo()` cannot be exercised in a preview because the
 * adaptive info reads from the host Activity's `WindowMetricsCalculator`, not
 * from the Compose Preview tooling's mock environment. Reviewers should
 * acknowledge this when diffing screens that branch on fold state — those
 * branches require a real emulator run (see `mockup-layout-tests` CI job
 * added in PR 4a).
 *
 * **`@Retention(BINARY)` + `@Target` rationale (cycle 4 finding #17):** without
 * these, some toolchain versions silently ignore the multi-preview annotation
 * and only one of the two `@Preview` instances renders.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Preview(
    name = "Pixel Fold — Unfolded (inner)",
    device = "spec:width=2208dp,height=1840dp,dpi=408,isRound=false,orientation=portrait",
    showBackground = true,
    backgroundColor = 0xFFFAFAFA,
)
@Preview(
    name = "Pixel Fold — Folded (cover)",
    device = "spec:width=1080dp,height=2092dp,dpi=420,isRound=false,orientation=portrait",
    showBackground = true,
    backgroundColor = 0xFFFAFAFA,
)
public annotation class MockupPreviews
