package dev.ori.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Semantic spacing scale used as gaps, margins, and content padding throughout
 * Ori:Dev. Component-intrinsic dimensions (e.g. the 44 dp height of an
 * `OriTopBarDefaults.HeightCompact`, the 60 dp `ServerProfileCard` height,
 * the 28 dp `OriChip` height) live inside the component itself and are NOT
 * part of this scale — the scale is for **gaps between things**, not for
 * sizing the things.
 *
 * Phase 11 reviewers (cycle 1 finding F2) explicitly rejected the v1 idea of
 * forcing every `.dp` literal in feature modules through a token; that path
 * collides with mockup-derived intrinsic heights like 44 dp / 52 dp / 60 dp.
 * This `OriSpacing` is therefore advisory: feature code is encouraged to use
 * `OriSpacing.m` for "12 dp gap between cards" but is free to spell `60.dp`
 * inline when a card height is the spec.
 */
object OriSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val ml = 16.dp
    val l = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val xxxxl = 64.dp
}
