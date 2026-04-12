package dev.ori.feature.settings.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.security.preferences.CrashReportingPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val crashReportingPreferences: CrashReportingPreferences,
) : ViewModel() {

    private val versionName: String = runCatching {
        @Suppress("DEPRECATION")
        application.packageManager
            .getPackageInfo(application.packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")

    val state: StateFlow<SettingsState> = crashReportingPreferences.enabled
        .map { enabled ->
            SettingsState(
                crashReportingEnabled = enabled,
                versionName = versionName,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
            initialValue = SettingsState(versionName = versionName),
        )

    fun setCrashReportingEnabled(value: Boolean) {
        viewModelScope.launch {
            crashReportingPreferences.setEnabled(value)
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}
