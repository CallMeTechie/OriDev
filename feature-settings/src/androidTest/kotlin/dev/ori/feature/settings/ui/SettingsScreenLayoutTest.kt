package dev.ori.feature.settings.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.ori.core.ui.theme.OriDevTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 11 PR 4a — bail-out gate for the foundation PR stack.
 *
 * Asserts the **topbar-to-content offset invariant**: there is no padding leak
 * between the bottom edge of `OriTopBar` and the first content row of
 * `SettingsScreen`. If the assertion fires, either the Scaffold is leaking
 * `innerPadding.top`, or `OriTopBar` is rendering at the wrong height, or
 * something else inserted a phantom margin between the two.
 *
 * **Why a relative-offset assertion (not an absolute `bounds.top == 56 dp`):**
 * the project uses `enableEdgeToEdge()` and `minSdk = 34`, so the Activity
 * draws under the status bar. An absolute assertion would measure
 * `statusBarInsetPx + 56 dp` (~80 dp) and always fail. The relative
 * assertion (`content.top - topBar.bottom == 0`) bypasses inset math entirely
 * and captures the real invariant. Cycle 4 finding #10 (review of plan v5)
 * called this out and v6 §P0.6 specifies exactly this assertion.
 *
 * The test is intentionally narrow in scope — fold-branch layout correctness
 * (different column counts, breakpoint-dependent visuals) is verified by
 * **manual mockup diff in P2's per-screen PRs**, not by this test.
 *
 * **Test tags:**
 * - `OriTopBar` carries `Modifier.testTag("ori_top_bar")` (set inside the
 *   primitive itself in `core/core-ui/components/OriTopBar.kt`).
 * - `SettingsScreen`'s root `Column` carries `Modifier.testTag("settings_content")`.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settings_content_starts_exactly_below_topbar() {
        composeTestRule.setContent {
            OriDevTheme {
                SettingsContent(
                    state = SettingsState(
                        crashReportingEnabled = false,
                        versionName = "0.6.0-test",
                    ),
                    onCrashReportingChanged = {},
                )
            }
        }

        val topBarBottom = composeTestRule
            .onNodeWithTag("ori_top_bar")
            .fetchSemanticsNode()
            .boundsInWindow
            .bottom

        val contentTop = composeTestRule
            .onNodeWithTag("settings_content")
            .fetchSemanticsNode()
            .boundsInWindow
            .top

        // Tolerance: ±1 px to absorb subpixel rounding. The assertion is
        // independent of status-bar inset, edge-to-edge, display cutout,
        // or screen size — it only measures the *relative* offset between
        // the OriTopBar's bottom edge and the first content row.
        assertThat(contentTop - topBarBottom).isWithin(1f).of(0f)
    }
}
