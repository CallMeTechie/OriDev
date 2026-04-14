package dev.ori.feature.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore("ori_settings")

/**
 * App-wide preferences DataStore for the Phase 11 P1.2 settings expansion.
 *
 * Stores the user-configurable toggles, dropdowns, and slider values from the
 * 7 settings sections (Account/Premium, Appearance, Terminal, Transfers,
 * Security, Notifications, About). Crash-reporting opt-in remains in its own
 * `core/core-security/preferences/CrashReportingPreferences` because the
 * `OriDevApplication` reads it during early app startup before Hilt graph
 * resolution, and that singleton predates this aggregator.
 *
 * **Persistence model:** Android DataStore Preferences with a single backing
 * file `ori_settings.preferences_pb`. All flows are exposed as `Flow<T>` and
 * read with `default → user-set` fallback so first-launch users see sane
 * defaults even before they touch the screen.
 *
 * **Defaults** are German-app-friendly (system theme, Latin keyboard layout,
 * 14 sp font, "ask before overwrite" transfer mode).
 */
@Singleton
public open class AppPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    public constructor(@ApplicationContext context: Context) : this(context.appPrefsDataStore)

    // ---- Appearance --------------------------------------------------------
    private val themeKey = stringPreferencesKey("theme")
    private val accentKey = stringPreferencesKey("accent")
    private val fontSizeKey = intPreferencesKey("font_size")
    private val terminalFontKey = stringPreferencesKey("terminal_font")

    public val theme: Flow<String> = dataStore.data.map { it[themeKey] ?: "system" }
    public val accent: Flow<String> = dataStore.data.map { it[accentKey] ?: "indigo" }
    public val fontSize: Flow<Int> = dataStore.data.map { it[fontSizeKey] ?: DEFAULT_FONT_SIZE }
    public val terminalFont: Flow<String> = dataStore.data.map { it[terminalFontKey] ?: "JetBrains Mono" }

    public suspend fun setTheme(value: String) { dataStore.edit { it[themeKey] = value } }
    public suspend fun setAccent(value: String) { dataStore.edit { it[accentKey] = value } }
    public suspend fun setFontSize(value: Int) { dataStore.edit { it[fontSizeKey] = value } }
    public suspend fun setTerminalFont(value: String) { dataStore.edit { it[terminalFontKey] = value } }

    // ---- Terminal ----------------------------------------------------------
    private val defaultShellKey = stringPreferencesKey("default_shell")
    private val scrollbackKey = intPreferencesKey("scrollback")
    private val bellModeKey = stringPreferencesKey("bell_mode")
    private val hardwareKeyboardKey = booleanPreferencesKey("hardware_keyboard")
    private val keyboardToolbarKey = booleanPreferencesKey("keyboard_toolbar")

    public val defaultShell: Flow<String> = dataStore.data.map { it[defaultShellKey] ?: "/bin/bash" }
    public val scrollback: Flow<Int> = dataStore.data.map { it[scrollbackKey] ?: DEFAULT_SCROLLBACK }
    public val bellMode: Flow<String> = dataStore.data.map { it[bellModeKey] ?: "visible" }
    public val hardwareKeyboard: Flow<Boolean> = dataStore.data.map { it[hardwareKeyboardKey] ?: false }
    public val keyboardToolbar: Flow<Boolean> = dataStore.data.map { it[keyboardToolbarKey] ?: true }

    public suspend fun setDefaultShell(value: String) { dataStore.edit { it[defaultShellKey] = value } }
    public suspend fun setScrollback(value: Int) { dataStore.edit { it[scrollbackKey] = value } }
    public suspend fun setBellMode(value: String) { dataStore.edit { it[bellModeKey] = value } }
    public suspend fun setHardwareKeyboard(value: Boolean) { dataStore.edit { it[hardwareKeyboardKey] = value } }
    public suspend fun setKeyboardToolbar(value: Boolean) { dataStore.edit { it[keyboardToolbarKey] = value } }

    // ---- Transfers ---------------------------------------------------------
    private val maxParallelTransfersKey = intPreferencesKey("max_parallel_transfers")
    private val autoResumeKey = booleanPreferencesKey("auto_resume")
    private val overwriteModeKey = stringPreferencesKey("overwrite_mode")

    // Phase 11 P4.6 — retry policy knobs used by the future transfer engine.
    // maxRetryAttempts caps how many times a failed transfer auto-retries
    // before surfacing the failure to the user; retryBackoffSeconds is the
    // *initial* delay in exponential backoff (doubles per attempt).
    private val maxRetryAttemptsKey = intPreferencesKey("max_retry_attempts")
    private val retryBackoffSecondsKey = intPreferencesKey("retry_backoff_seconds")

    public open val maxParallelTransfers: Flow<Int> =
        dataStore.data.map { it[maxParallelTransfersKey] ?: DEFAULT_MAX_TRANSFERS }
    public open val autoResume: Flow<Boolean> =
        dataStore.data.map { it[autoResumeKey] ?: true }
    public open val overwriteMode: Flow<String> =
        dataStore.data.map { it[overwriteModeKey] ?: "ask" }
    public open val maxRetryAttempts: Flow<Int> =
        dataStore.data.map { it[maxRetryAttemptsKey] ?: DEFAULT_MAX_RETRY_ATTEMPTS }
    public open val retryBackoffSeconds: Flow<Int> =
        dataStore.data.map { it[retryBackoffSecondsKey] ?: DEFAULT_RETRY_BACKOFF_SECONDS }

    public suspend fun setMaxParallelTransfers(value: Int) {
        dataStore.edit { it[maxParallelTransfersKey] = value }
    }
    public suspend fun setAutoResume(value: Boolean) {
        dataStore.edit { it[autoResumeKey] = value }
    }
    public suspend fun setOverwriteMode(value: String) {
        dataStore.edit { it[overwriteModeKey] = value }
    }
    public suspend fun setMaxRetryAttempts(value: Int) {
        dataStore.edit { it[maxRetryAttemptsKey] = value }
    }
    public suspend fun setRetryBackoffSeconds(value: Int) {
        dataStore.edit { it[retryBackoffSecondsKey] = value }
    }

    // ---- Security ----------------------------------------------------------
    private val biometricUnlockKey = booleanPreferencesKey("biometric_unlock")
    private val autoLockTimeoutKey = intPreferencesKey("auto_lock_timeout_minutes")
    private val clipboardClearSecondsKey = intPreferencesKey("clipboard_clear_seconds")

    public val biometricUnlock: Flow<Boolean> =
        dataStore.data.map { it[biometricUnlockKey] ?: false }
    public val autoLockTimeoutMinutes: Flow<Int> =
        dataStore.data.map { it[autoLockTimeoutKey] ?: DEFAULT_AUTO_LOCK }
    public val clipboardClearSeconds: Flow<Int> =
        dataStore.data.map { it[clipboardClearSecondsKey] ?: DEFAULT_CLIPBOARD_CLEAR }

    public suspend fun setBiometricUnlock(value: Boolean) {
        dataStore.edit { it[biometricUnlockKey] = value }
    }
    public suspend fun setAutoLockTimeoutMinutes(value: Int) {
        dataStore.edit { it[autoLockTimeoutKey] = value }
    }
    public suspend fun setClipboardClearSeconds(value: Int) {
        dataStore.edit { it[clipboardClearSecondsKey] = value }
    }

    // ---- Notifications -----------------------------------------------------
    private val notifyTransferDoneKey = booleanPreferencesKey("notify_transfer_done")
    private val notifyConnectionKey = booleanPreferencesKey("notify_connection")
    private val notifyClaudeKey = booleanPreferencesKey("notify_claude")
    private val notifyWearKey = booleanPreferencesKey("notify_wear")

    public val notifyTransferDone: Flow<Boolean> = dataStore.data.map { it[notifyTransferDoneKey] ?: true }
    public val notifyConnection: Flow<Boolean> = dataStore.data.map { it[notifyConnectionKey] ?: true }
    public val notifyClaude: Flow<Boolean> = dataStore.data.map { it[notifyClaudeKey] ?: false }
    public val notifyWear: Flow<Boolean> = dataStore.data.map { it[notifyWearKey] ?: true }

    public suspend fun setNotifyTransferDone(value: Boolean) { dataStore.edit { it[notifyTransferDoneKey] = value } }
    public suspend fun setNotifyConnection(value: Boolean) { dataStore.edit { it[notifyConnectionKey] = value } }
    public suspend fun setNotifyClaude(value: Boolean) { dataStore.edit { it[notifyClaudeKey] = value } }
    public suspend fun setNotifyWear(value: Boolean) { dataStore.edit { it[notifyWearKey] = value } }

    /**
     * Aggregated snapshot for [SettingsViewModel]'s reactive flow. Reads all
     * 19 backing keys via [dataStore.data] in a SINGLE map operation rather
     * than nesting 19 `combine` calls. Kotlin's `combine(vararg flows)` only
     * supports up to 5 typed flows; for our 19-flow aggregation, reading the
     * whole `Preferences` map and projecting it is simpler and more efficient
     * (no per-key flow chain, no intersection-type warnings).
     */
    public val all: Flow<AppPreferencesSnapshot> = dataStore.data.map { prefs ->
        AppPreferencesSnapshot(
            theme = prefs[themeKey] ?: "system",
            accent = prefs[accentKey] ?: "indigo",
            fontSize = prefs[fontSizeKey] ?: DEFAULT_FONT_SIZE,
            terminalFont = prefs[terminalFontKey] ?: "JetBrains Mono",
            defaultShell = prefs[defaultShellKey] ?: "/bin/bash",
            scrollback = prefs[scrollbackKey] ?: DEFAULT_SCROLLBACK,
            bellMode = prefs[bellModeKey] ?: "visible",
            hardwareKeyboard = prefs[hardwareKeyboardKey] ?: false,
            keyboardToolbar = prefs[keyboardToolbarKey] ?: true,
            maxParallelTransfers = prefs[maxParallelTransfersKey] ?: DEFAULT_MAX_TRANSFERS,
            autoResume = prefs[autoResumeKey] ?: true,
            overwriteMode = prefs[overwriteModeKey] ?: "ask",
            maxRetryAttempts = prefs[maxRetryAttemptsKey] ?: DEFAULT_MAX_RETRY_ATTEMPTS,
            retryBackoffSeconds = prefs[retryBackoffSecondsKey] ?: DEFAULT_RETRY_BACKOFF_SECONDS,
            biometricUnlock = prefs[biometricUnlockKey] ?: false,
            autoLockTimeoutMinutes = prefs[autoLockTimeoutKey] ?: DEFAULT_AUTO_LOCK,
            clipboardClearSeconds = prefs[clipboardClearSecondsKey] ?: DEFAULT_CLIPBOARD_CLEAR,
            notifyTransferDone = prefs[notifyTransferDoneKey] ?: true,
            notifyConnection = prefs[notifyConnectionKey] ?: true,
            notifyClaude = prefs[notifyClaudeKey] ?: false,
            notifyWear = prefs[notifyWearKey] ?: true,
        )
    }

    private companion object {
        const val DEFAULT_FONT_SIZE = 14
        const val DEFAULT_SCROLLBACK = 10_000
        const val DEFAULT_MAX_TRANSFERS = 3
        const val DEFAULT_AUTO_LOCK = 5
        const val DEFAULT_CLIPBOARD_CLEAR = 30
        const val DEFAULT_MAX_RETRY_ATTEMPTS = 3
        const val DEFAULT_RETRY_BACKOFF_SECONDS = 10
    }
}

/** Immutable snapshot of [AppPreferences] for use in [SettingsState]. */
public data class AppPreferencesSnapshot(
    val theme: String,
    val accent: String,
    val fontSize: Int,
    val terminalFont: String,
    val defaultShell: String,
    val scrollback: Int,
    val bellMode: String,
    val hardwareKeyboard: Boolean,
    val keyboardToolbar: Boolean,
    val maxParallelTransfers: Int,
    val autoResume: Boolean,
    val overwriteMode: String,
    // Phase 11 P4.6 — retry policy knobs for the transfer engine.
    val maxRetryAttempts: Int,
    val retryBackoffSeconds: Int,
    val biometricUnlock: Boolean,
    val autoLockTimeoutMinutes: Int,
    val clipboardClearSeconds: Int,
    val notifyTransferDone: Boolean,
    val notifyConnection: Boolean,
    val notifyClaude: Boolean,
    val notifyWear: Boolean,
)
