package dev.ori.domain.model

/**
 * Selects which keyboard surface the terminal pane renders under the shell
 * view. Persisted by `KeyboardPreferences` in `core-common`; consumed by
 * `KeyboardHost` in `feature-terminal` (Phase 14).
 *
 * Values:
 *  - [CUSTOM] — the built-in `CustomKeyboard` composable (today's behaviour,
 *    default; preferred for password entry since nothing is routed through
 *    the system IME and there is no dictionary-learning risk).
 *  - [HYBRID] — the Android system IME (Gboard, SwiftKey, …) plus a sticky
 *    extra-keys row pinned above it (Esc/Tab/Ctrl/Alt/arrows/Fn).
 *  - [SYSTEM_ONLY] — the system IME alone, no extra-keys row. Power-user
 *    escape hatch.
 *
 * Pure Kotlin on purpose: lives in `domain/` so both Android and plain-JVM
 * modules (preferences, use-cases, tests) can reference it without pulling
 * in an Android classpath.
 */
enum class KeyboardMode {
    CUSTOM,
    HYBRID,
    SYSTEM_ONLY,
}
