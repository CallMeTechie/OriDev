package dev.ori.feature.terminal.ui

/**
 * Snapshot of pane state. Output of [resolvePaneAssignments].
 */
data class PaneAssignment(
    val leftPaneTabId: String?,
    val rightPaneTabId: String?,
    val activePaneIndex: Int,
)

/**
 * Pure reducer that collapses any pane-state input into a consistent
 * output via two-stage rules.
 *
 * Phase A: Rules 1-4 (orphan-cleanup -> duplicate-cleanup -> left-fill
 * -> right-fill). Rule 1 is suspended while [isRestoringPanes] is true
 * so cold-start pane IDs are not nulled before the matching tabs
 * arrive.
 *
 * Phase B: Sanitization-Clamp runs only AFTER Phase A. It never flips
 * the active pane while Rule 3/4 could still fill the matching slot,
 * preventing the silent-keystroke-misroute regression from DA round 3.
 */
@Suppress("LongParameterList")
fun resolvePaneAssignments(
    tabs: List<TerminalTabState>,
    leftPaneTabId: String?,
    rightPaneTabId: String?,
    activePaneIndex: Int,
    activeTabIndex: Int,
    isRestoringPanes: Boolean,
): PaneAssignment {
    // Rule 1: orphan cleanup (skipped during restore)
    var left = if (isRestoringPanes) {
        leftPaneTabId
    } else {
        leftPaneTabId?.takeIf { id -> tabs.any { it.id == id } }
    }
    var right = if (isRestoringPanes) {
        rightPaneTabId
    } else {
        rightPaneTabId?.takeIf { id -> tabs.any { it.id == id } }
    }

    // Rule 2: duplicate cleanup -- preserves active
    if (left != null && left == right) {
        if (activePaneIndex == 0) right = null else left = null
    }

    // Rule 3: left-slot fill. Prefer the activeTabIndex'd tab. If that
    // would duplicate right AND another tab exists, pick the other so
    // Rule 4 isn't forced to leave the other pane empty. If only one
    // tab exists and it already sits in right, accept the transient
    // duplicate -- the re-run of Rule 2 below collapses it correctly.
    if (left == null && tabs.isNotEmpty()) {
        val preferredId = tabs.getOrNull(activeTabIndex)?.id ?: tabs.first().id
        left = if (preferredId != right) {
            preferredId
        } else {
            tabs.firstOrNull { it.id != right }?.id ?: preferredId
        }
    }

    // Rule 4: right-slot fill
    if (right == null && tabs.size >= 2) {
        right = tabs.firstOrNull { it.id != left }?.id
    }

    // Re-run Rule 2 after fills -- Rule 3 may have collided with right
    if (left != null && left == right) {
        if (activePaneIndex == 0) right = null else left = null
    }

    // Phase B: sanitization clamp (last-resort)
    var active = activePaneIndex.coerceIn(0, 1)
    if (active == 1 && right == null) active = 0
    if (active == 0 && left == null && right != null) active = 1

    return PaneAssignment(left, right, active)
}
