package dev.ori.domain.preferences

import dev.ori.domain.model.TabMemo
import kotlinx.coroutines.flow.Flow

/**
 * Resume-state preferences. Implemented by :data via DataStore.
 * Kept in :domain so :feature-terminal and :feature-filemanager can
 * inject the interface without pulling in :data.
 */
interface SessionResumePreferences {
    val profileIds: Flow<Set<Long>>
    val tabMemos: Flow<List<TabMemo>>
    val focusedProfileId: Flow<Long?>
    val remotePaths: Flow<Map<Long, String>>
    val lastTopLevelRoute: Flow<String>

    suspend fun setProfileIds(ids: Set<Long>)
    suspend fun setTabMemos(memos: List<TabMemo>)
    suspend fun setFocusedProfileId(id: Long?)
    suspend fun setRemotePath(profileId: Long, path: String)
    suspend fun setLastTopLevelRoute(route: String)

    /** Wipes profileIds + tabMemos + focusedProfileId + remotePaths atomically. Leaves lastTopLevelRoute intact. */
    suspend fun clearResumeSubset()
}

/**
 * Separate interface — auto-resume toggle lives in AppPreferences
 * (:feature-settings) which :data cannot depend on. `:feature-settings`
 * implements this interface so ResumeCoordinator can consume the flag
 * without a cross-module import.
 */
interface AutoResumePreferences {
    val autoResumeSessions: Flow<Boolean>
    suspend fun setAutoResumeSessions(value: Boolean)
}
