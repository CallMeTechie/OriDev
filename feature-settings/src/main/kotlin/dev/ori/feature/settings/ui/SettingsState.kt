package dev.ori.feature.settings.ui

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
 */
public data class SettingsState(
    val crashReportingEnabled: Boolean = false,
    val versionName: String = "",
    val preferences: AppPreferencesSnapshot = DEFAULT_PREFERENCES,
    val premiumStatus: PremiumStatus = PremiumStatus.Free,
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
