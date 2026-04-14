package dev.ori.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ori.core.ui.theme.Gray200
import dev.ori.core.ui.theme.Gray900
import dev.ori.core.ui.theme.TopBarBackground

/**
 * Mockup-aligned top bar primitive. Replaces every `androidx.compose.material3.TopAppBar`
 * usage in feature modules.
 *
 * Per Phase 11 plan v6 §P0.5 + cycle 4 finding #9, the background is the named
 * [TopBarBackground] token (defined in `core-ui/theme/Color.kt`, value
 * `Color.White.copy(alpha = 0.92f)`), NOT a hex literal — Compose has no real
 * backdrop-blur primitive, so the mockup's `backdrop-filter: blur(12px)` effect
 * collapses to a solid 92 %-opaque white surface.
 *
 * **Heights** (one per screen category, matching the mockup spec):
 * - [OriTopBarDefaults.Height]        — 56 dp, default for most screens
 * - [OriTopBarDefaults.HeightCompact] — 44 dp, terminal screen
 * - [OriTopBarDefaults.HeightDense]   — 40 dp, code editor screen
 *
 * **Why a custom primitive instead of `TopAppBar`:** Material 3's `TopAppBar`
 * defaults to 64 dp / `MediumTopAppBar` 112 dp / `LargeTopAppBar` 152 dp, all
 * significantly taller than the 40-56 dp the mockups specify. The "too much
 * space at the top" complaint that motivated Phase 11 in the first place was
 * caused by Material defaults, not by anything Ori:Dev was doing wrong.
 *
 * **Test tag:** the bar carries `Modifier.testTag("ori_top_bar")` so the
 * `SettingsScreenLayoutTest` instrumented test (PR 4a bail-out gate) can locate
 * it and assert that `contentTop - topBarBottom == 0` (no padding leak between
 * the bottom of the bar and the first content row).
 *
 * **Scaffold elevation:** the bar is rendered inside a [Surface] with
 * `tonalElevation = 0.dp` and `shadowElevation = 0.dp` so that when placed
 * inside `Scaffold { topBar = { OriTopBar(...) } }` no implicit shadow is
 * introduced under it — current Compose does not add one, but we assert this
 * explicitly so future Compose version bumps don't silently regress.
 */
@Composable
public fun OriTopBar(
    title: String,
    modifier: Modifier = Modifier,
    height: Dp = OriTopBarDefaults.Height,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    hairline: Boolean = true,
) {
    val borderColor = Gray200
    Surface(
        modifier = modifier
            .testTag("ori_top_bar")
            .fillMaxWidth()
            .height(height),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = TopBarBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (hairline) {
                        val strokePx = 1.dp.toPx()
                        drawLine(
                            color = borderColor,
                            start = Offset(0f, size.height - strokePx / 2f),
                            end = Offset(size.width, size.height - strokePx / 2f),
                            strokeWidth = strokePx,
                        )
                    }
                }
                .padding(horizontal = OriTopBarDefaults.HorizontalPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.titleSmall.copy(color = Gray900),
                    ) {
                        Text(text = title)
                    }
                }
                actions()
            }
        }
    }
}

public object OriTopBarDefaults {
    /** Default top-bar height — 56 dp per `index.html` / `settings.html` / etc. */
    public val Height: Dp = 56.dp

    /** Compact height for the terminal screen — 44 dp per `terminal.html`. */
    public val HeightCompact: Dp = 44.dp

    /** Dense height for the code editor — 40 dp per `code-editor.html`. */
    public val HeightDense: Dp = 40.dp

    /** Horizontal content padding inside the top bar. */
    public val HorizontalPadding: Dp = 16.dp
}
