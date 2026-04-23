package dev.ori.domain.model

import kotlinx.serialization.Serializable

/**
 * Per-profile terminal-tab memo persisted by the resume subsystem so that
 * relaunching the app (or re-opening a profile from the Connections list)
 * can rehydrate the user's working set without forcing them to recreate
 * tabs manually. One [TabMemo] is stored per profile id.
 *
 * Serialised as JSON into the resume DataStore; the reader is configured
 * with `ignoreUnknownKeys = true` so future fields can be added without
 * breaking existing installs on downgrade.
 *
 * @property profileId identifier of the server profile these tabs belong to
 * @property tabCount number of terminal tabs the user had open for this
 *   profile at the point the memo was last written
 * @property focusedWithinProfile zero-based index of the focused tab
 *   within this profile's tab list
 */
@Serializable
data class TabMemo(
    val profileId: Long,
    val tabCount: Int,
    val focusedWithinProfile: Int,
)
