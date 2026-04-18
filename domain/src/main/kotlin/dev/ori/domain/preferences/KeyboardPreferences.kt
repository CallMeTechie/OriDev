package dev.ori.domain.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ori.domain.model.KeyboardMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's preferred [KeyboardMode] for the terminal pane.
 *
 * Phase 14 Task 14.1 — single source of truth for `KeyboardHost`
 * (`feature-terminal`). Standalone DataStore (backing file
 * [DATASTORE_NAME]) rather than a field on `feature-settings`'
 * `AppPreferences`, because `feature-terminal` must not depend on
 * `feature-settings` (feature-isolation rule, CLAUDE.md).
 *
 * The default is [KeyboardMode.CUSTOM]. Existing users keep their built-in
 * keyboard on first launch after upgrading — no surprise IME swap, no
 * accidental dictionary-learning event. HYBRID is opt-in via the Settings
 * screen (wired up in Task 14.6).
 *
 * ## Module placement
 *
 * Lives in `:domain` rather than `:core-common` (which the Phase 14 plan
 * proposes) because `KeyboardMode` itself is in `:domain` per Task 14.1
 * Step 1, and `:core-common` sits below `:domain` in the dependency
 * graph — `core-common` cannot reference `domain` types. Placing the
 * preferences class next to its model is the cheapest fix that keeps
 * both modules pure Kotlin (no Android classpath pollution) and keeps
 * the CLAUDE.md rule "`domain` has no Android dependencies" intact:
 * [androidx.datastore:datastore-preferences-core] is the pure-JVM
 * (okio-backed) DataStore variant.
 *
 * ## Production wiring
 *
 * The [DataStore] is supplied via the primary constructor so tests can
 * inject an in-memory fake. The production Android binding materialises
 * a DataStore from `PreferenceDataStoreFactory.create { context
 * .preferencesDataStoreFile([DATASTORE_NAME]) }` and lives in a
 * downstream Android Hilt `@Module` (introduced in Task 14.6 alongside
 * the Settings UI).
 *
 * @see KeyboardMode
 */
@Singleton
class KeyboardPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val modeKey = stringPreferencesKey(KEY_MODE)

    /**
     * Emits the currently-persisted [KeyboardMode]. Unknown values
     * (forward-compat rollback, store corruption) fall back to
     * [DEFAULT_MODE] so the terminal never crashes on read.
     */
    val keyboardModeFlow: Flow<KeyboardMode> = dataStore.data
        .map { prefs -> prefs[modeKey].toKeyboardModeOrDefault() }

    /** Persists [mode]. Suspending — call from a coroutine scope. */
    suspend fun setKeyboardMode(mode: KeyboardMode) {
        dataStore.edit { it[modeKey] = mode.name }
    }

    private fun String?.toKeyboardModeOrDefault(): KeyboardMode =
        if (this == null) {
            DEFAULT_MODE
        } else {
            runCatching { KeyboardMode.valueOf(this) }.getOrDefault(DEFAULT_MODE)
        }

    companion object {
        /** DataStore preference key for the serialised [KeyboardMode] name. */
        const val KEY_MODE: String = "keyboard_mode"

        /**
         * Backing-file name for production builds. The downstream Hilt
         * provider SHOULD pass this constant to
         * `context.preferencesDataStoreFile(DATASTORE_NAME)` so the
         * filename stays consistent and upgrades preserve user state.
         */
        const val DATASTORE_NAME: String = "ori_keyboard"

        /**
         * Default on fresh installs and upgrades. **Do not change without
         * updating the Phase 14 plan and release notes** — existing
         * users' muscle memory and the security posture (no system-IME
         * dictionary learning by default) depend on this staying
         * [KeyboardMode.CUSTOM]. HYBRID is opt-in via Settings.
         */
        val DEFAULT_MODE: KeyboardMode = KeyboardMode.CUSTOM
    }
}
