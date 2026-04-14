package dev.ori.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape system aligned with the Phase 11 mockups.
 *
 * Mockup tokens (from `Mockups/index.html`):
 * - `--radius`     `14px`  → cards (the default `OriCard` rounding)
 * - `--radius-sm`  `10px`  → smaller surfaces
 * - `--radius-pill` `9999px` → see [OriExtraShapes.pill]
 *
 * Notable change from v0.x: `large` was previously `16.dp`; the mockup
 * specifies `14.dp` for the primary card radius, so v6 §P0.4 corrects it.
 * `OriDevTheme.kt:38` already references `OriDevShapes` by name, so this
 * rewrite is picked up automatically without a call-site change.
 */
val OriDevShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), // icon containers (mockup --radius icon 6px)
    small = RoundedCornerShape(8.dp), // inputs, small buttons
    medium = RoundedCornerShape(10.dp), // mockup --radius-sm
    large = RoundedCornerShape(14.dp), // mockup --radius (cards) — was 16
    extraLarge = RoundedCornerShape(20.dp), // modal bottom-sheet top corners
)

/**
 * Phase 11 shapes that don't fit the M3 `Shapes` slots — pill primitives
 * (status badges, chips, FAB), modal-top-only rounding for bottom sheets,
 * and progress bar tracks. Feature code accesses these via
 * `OriExtraShapes.pill` etc., not via `MaterialTheme.shapes`.
 */
object OriExtraShapes {
    /** Full pill (50 % of the smaller dimension) — chips, status badges, FAB. */
    val pill = RoundedCornerShape(percent = 50)

    /** Bottom sheet top corners only, 20 dp; bottom corners are 0 dp (flush). */
    val modalTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** Same as [pill]; semantic alias for OriStatusBadge so intent is explicit. */
    val badge = pill

    /** Same as [pill]; semantic alias for OriProgressBar tracks. */
    val progress = pill
}
