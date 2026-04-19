package dev.ori.feature.settings.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.security.preferences.CrashReportingPreferences
import dev.ori.domain.model.KeyboardMode
import dev.ori.domain.preferences.KeyboardPreferences
import dev.ori.domain.usecase.CheckPremiumUseCase
import dev.ori.feature.settings.data.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
public class SettingsViewModel @Inject constructor(
    application: Application,
    private val crashReportingPreferences: CrashReportingPreferences,
    private val appPreferences: AppPreferences,
    private val keyboardPreferences: KeyboardPreferences,
    checkPremiumUseCase: CheckPremiumUseCase,
) : ViewModel() {

    private val versionName: String = runCatching {
        @Suppress("DEPRECATION")
        application.packageManager
            .getPackageInfo(application.packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")

    public val state: StateFlow<SettingsState> = combine(
        crashReportingPreferences.enabled,
        appPreferences.all,
        checkPremiumUseCase(),
        keyboardPreferences.keyboardModeFlow,
    ) { crashReporting, prefs, isPremium, keyboardMode ->
        SettingsState(
            crashReportingEnabled = crashReporting,
            versionName = versionName,
            preferences = prefs,
            premiumStatus = if (isPremium) PremiumStatus.Premium else PremiumStatus.Free,
            keyboardMode = keyboardMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = SettingsState(versionName = versionName),
    )

    public fun setCrashReportingEnabled(value: Boolean) {
        viewModelScope.launch { crashReportingPreferences.setEnabled(value) }
    }

    // ---- Appearance --------------------------------------------------------
    public fun setTheme(value: String) {
        viewModelScope.launch { appPreferences.setTheme(value) }
    }
    public fun setAccent(value: String) {
        viewModelScope.launch { appPreferences.setAccent(value) }
    }
    public fun setFontSize(value: Int) {
        viewModelScope.launch { appPreferences.setFontSize(value) }
    }
    public fun setTerminalFont(value: String) {
        viewModelScope.launch { appPreferences.setTerminalFont(value) }
    }

    // ---- Terminal ----------------------------------------------------------
    public fun setDefaultShell(value: String) {
        viewModelScope.launch { appPreferences.setDefaultShell(value) }
    }
    public fun setScrollback(value: Int) {
        viewModelScope.launch { appPreferences.setScrollback(value) }
    }
    public fun setBellMode(value: String) {
        viewModelScope.launch { appPreferences.setBellMode(value) }
    }
    public fun setHardwareKeyboard(value: Boolean) {
        viewModelScope.launch { appPreferences.setHardwareKeyboard(value) }
    }
    public fun setKeyboardToolbar(value: Boolean) {
        viewModelScope.launch { appPreferences.setKeyboardToolbar(value) }
    }

    /**
     * Phase 14 Task 14.6 — persists the user's selected keyboard-surface
     * mode. Task 14.5's `KeyboardHost` reactively picks up the change on
     * its next Flow emission without requiring a restart.
     *
     * The section UI is responsible for showing the system-IME
     * dictionary-learning warning before HYBRID / SYSTEM_ONLY reaches
     * this method; by the time we're called the user has already
     * confirmed.
     */
    public fun setKeyboardMode(value: KeyboardMode) {
        viewModelScope.launch { keyboardPreferences.setKeyboardMode(value) }
    }

    // ---- Transfers ---------------------------------------------------------
    public fun setMaxParallelTransfers(value: Int) {
        viewModelScope.launch { appPreferences.setMaxParallelTransfers(value) }
    }
    public fun setAutoResume(value: Boolean) {
        viewModelScope.launch { appPreferences.setAutoResume(value) }
    }
    public fun setOverwriteMode(value: String) {
        viewModelScope.launch { appPreferences.setOverwriteMode(value) }
    }
    public fun setMaxRetryAttempts(value: Int) {
        viewModelScope.launch { appPreferences.setMaxRetryAttempts(value) }
    }
    public fun setRetryBackoffSeconds(value: Int) {
        viewModelScope.launch { appPreferences.setRetryBackoffSeconds(value) }
    }

    // ---- Security ----------------------------------------------------------
    public fun setBiometricUnlock(value: Boolean) {
        viewModelScope.launch { appPreferences.setBiometricUnlock(value) }
    }
    public fun setAutoLockTimeoutMinutes(value: Int) {
        viewModelScope.launch { appPreferences.setAutoLockTimeoutMinutes(value) }
    }
    public fun setClipboardClearSeconds(value: Int) {
        viewModelScope.launch { appPreferences.setClipboardClearSeconds(value) }
    }

    // ---- Notifications -----------------------------------------------------
    public fun setNotifyTransferDone(value: Boolean) {
        viewModelScope.launch { appPreferences.setNotifyTransferDone(value) }
    }
    public fun setNotifyConnection(value: Boolean) {
        viewModelScope.launch { appPreferences.setNotifyConnection(value) }
    }
    public fun setNotifyClaude(value: Boolean) {
        viewModelScope.launch { appPreferences.setNotifyClaude(value) }
    }
    public fun setNotifyWear(value: Boolean) {
        viewModelScope.launch { appPreferences.setNotifyWear(value) }
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}
