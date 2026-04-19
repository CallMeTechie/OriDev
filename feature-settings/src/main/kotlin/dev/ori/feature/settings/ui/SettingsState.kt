package dev.ori.feature.settings.ui

import dev.ori.domain.model.GrantedTree
import dev.ori.domain.model.KeyboardMode
import dev.ori.domain.preferences.KeyboardPreferences
import dev.ori.feature.settings.data.AppPreferencesSnapshot

/**
 * UI state for [SettingsScreen] — Phase 11 P1.2 expansion from the original
 * 2-section state (privacy + info) to the full 7-section settings model.
 *
 * - [crashReportingEnabled] continues to read from the legacy
 *   `core/core-security/preferences/CrashReportingPreferences` because the
 *   `OriDevApplication` reads it during early startup, before Hilt has
 *   resolved this ViewModel.
 * - [preferences] is the new aggregated snapshot from
 *   [dev.ori.feature.settings.data.AppPreferences], read reactively.
 * - [versionName] is read once from the PackageManager.
 * - Premium state is `Free` for now; full Premium UI ships in a future
 *   Phase 12 (Monetarisierung) per plan v6 §3 item 3.
 * - [keyboardMode] is the Phase 14 Task 14.6 keyboard-surface choice
 *   (CUSTOM / HYBRID / SYSTEM_ONLY). Lives on its own preference file
 *   because `feature-terminal` must not depend on `feature-settings`.
 */
public data class SettingsState(
    val crashReportingEnabled: Boolean = false,
    val versionName: String = "",
    val preferences: AppPreferencesSnapshot = DEFAULT_PREFERENCES,
    val premiumStatus: PremiumStatus = PremiumStatus.Free,
    val keyboardMode: KeyboardMode = KeyboardPreferences.DEFAULT_MODE,
    // Phase 15 Task 15.6 — SAF-granted trees surfaced in the Storage
    // Access section. Empty list → only the "Add folder" button renders.
    val grantedTrees: List<GrantedTree> = emptyList(),
)

public enum class PremiumStatus { Free, Premium }

private val DEFAULT_PREFERENCES = AppPreferencesSnapshot(
    theme = "system",
    accent = "indigo",
    fontSize = 14,
    terminalFont = "JetBrains Mono",
    defaultShell = "/bin/bash",
    scrollback = 10_000,
    bellMode = "visible",
    hardwareKeyboard = false,
    keyboardToolbar = true,
    maxParallelTransfers = 3,
    autoResume = true,
    overwriteMode = "ask",
    maxRetryAttempts = 3,
    retryBackoffSeconds = 10,
    biometricUnlock = false,
    autoLockTimeoutMinutes = 5,
    clipboardClearSeconds = 30,
    notifyTransferDone = true,
    notifyConnection = true,
    notifyClaude = false,
    notifyWear = true,
)
