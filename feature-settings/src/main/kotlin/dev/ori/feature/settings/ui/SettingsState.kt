package dev.ori.feature.settings.ui

/**
 * UI state for [SettingsScreen]. Phase 10 only exposes the privacy section
 * (crash-reporting opt-in) and an info section (version name). Future phases
 * will extend this state with additional preferences.
 */
data class SettingsState(
    val crashReportingEnabled: Boolean = false,
    val versionName: String = "",
)
