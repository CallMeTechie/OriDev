package dev.ori.feature.terminal.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PaneAssignmentTest {

    // The reducer only reads `tab.id`; the other fields are required by
    // the existing TerminalTabState constructor and are filled with
    // cheap defaults derived from the id / profileId.
    private fun tab(id: String, profileId: Long = 1L) = TerminalTabState(
        id = id,
        sessionId = "sess-$id",
        profileId = profileId,
        serverName = "P$profileId",
    )

    @Test
    fun `empty tabs yields empty slots and active 0`() {
        val r = resolvePaneAssignments(
            tabs = emptyList(),
            leftPaneTabId = null,
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isNull()
        assertThat(r.rightPaneTabId).isNull()
        assertThat(r.activePaneIndex).isEqualTo(0)
    }

    @Test
    fun `orphan left slot is nulled outside restore`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T2")),
            leftPaneTabId = "T-ghost",
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T2")
    }

    @Test
    fun `orphan NOT nulled during restore (pending tab)`() {
        val r = resolvePaneAssignments(
            tabs = emptyList(),
            leftPaneTabId = "T-pending",
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = true,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T-pending")
    }

    @Test
    fun `duplicate cleanup preserves active-pane slot (active=0)`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = "T1",
            rightPaneTabId = "T1",
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T1")
        assertThat(r.rightPaneTabId).isEqualTo("T2")
    }

    @Test
    fun `duplicate cleanup preserves active-pane slot (active=1)`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = "T1",
            rightPaneTabId = "T1",
            activePaneIndex = 1,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T2")
        assertThat(r.rightPaneTabId).isEqualTo("T1")
    }

    @Test
    fun `left-slot fill uses activeTabIndex`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = null,
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 1,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T2")
        assertThat(r.rightPaneTabId).isEqualTo("T1")
    }

    @Test
    fun `right-slot not filled with only one tab`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1")),
            leftPaneTabId = null,
            rightPaneTabId = null,
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T1")
        assertThat(r.rightPaneTabId).isNull()
    }

    @Test
    fun `single tab in rightSlot + active=0 keeps active=0 (regression DA-3)`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1")),
            leftPaneTabId = null,
            rightPaneTabId = "T1",
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T1")
        assertThat(r.rightPaneTabId).isNull()
        assertThat(r.activePaneIndex).isEqualTo(0)
    }

    @Test
    fun `activePaneIndex clamped when rightSlot genuinely empty`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1")),
            leftPaneTabId = "T1",
            rightPaneTabId = null,
            activePaneIndex = 1,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.activePaneIndex).isEqualTo(0)
    }

    @Test
    fun `activePaneIndex out of range coerced`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = "T1",
            rightPaneTabId = "T2",
            activePaneIndex = 7,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.activePaneIndex).isEqualTo(1)
    }

    @Test
    fun `orphan cleanup of rightSlot with refill`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2"), tab("T3")),
            leftPaneTabId = "T1",
            rightPaneTabId = "T-ghost",
            activePaneIndex = 0,
            activeTabIndex = 0,
            isRestoringPanes = false,
        )
        assertThat(r.rightPaneTabId).isEqualTo("T2")
    }

    @Test
    fun `corrupt activeTabIndex falls back to first tab`() {
        val r = resolvePaneAssignments(
            tabs = listOf(tab("T1"), tab("T2")),
            leftPaneTabId = null,
            rightPaneTabId = "T2",
            activePaneIndex = 0,
            activeTabIndex = -1,
            isRestoringPanes = false,
        )
        assertThat(r.leftPaneTabId).isEqualTo("T1")
        assertThat(r.rightPaneTabId).isEqualTo("T2")
        assertThat(r.activePaneIndex).isEqualTo(0)
    }
}
