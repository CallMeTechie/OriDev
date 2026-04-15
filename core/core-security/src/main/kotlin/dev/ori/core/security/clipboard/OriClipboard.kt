package dev.ori.core.security.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Thin seam around [ClipboardManager] so unit tests can verify
 * [OriClipboard]'s auto-clear logic without touching the real Android
 * framework static [ClipData.newPlainText].
 */
internal interface ClipSink {
    fun write(label: String, text: String, sensitive: Boolean)
    fun current(): String?
    fun clear()
}

internal class SystemClipSink(
    private val context: Context,
    private val clipboardManager: ClipboardManager,
) : ClipSink {
    override fun write(label: String, text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clip.description.extras = extras
        }
        clipboardManager.setPrimaryClip(clip)
    }

    override fun current(): String? = runCatching {
        clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    override fun clear() {
        clipboardManager.clearPrimaryClip()
    }
}

/**
 * Centralized clipboard writer. Every call from Ori:Dev code that writes
 * to the system clipboard MUST go through [copy] so the sensitive-content
 * flag and the user-configured auto-clear timer are applied uniformly.
 *
 * Two safety features:
 *  1. Sensitive flag. [ClipDescription.EXTRA_IS_SENSITIVE] is set on the
 *     clip description so Android suppresses the system-level clipboard
 *     preview overlay for passwords and SSH keys. The app's `minSdk` is
 *     already 34 (Android 14), so the extra is always honoured.
 *  2. Auto-clear. After `holdForSeconds` seconds (pulled from the user's
 *     `AppPreferences.clipboardClearSeconds`, default 30) the helper
 *     clears the primary clip — but only if the current primary clip is
 *     still the exact string we wrote. If the user or another app
 *     overwrote the clipboard in the meantime, we leave it alone so we
 *     do not clobber unrelated content.
 *
 * This class is `@Singleton` so the pending auto-clear job is shared
 * process-wide; calling [copy] a second time cancels the previous
 * timer and starts a fresh one.
 */
@Singleton
class OriClipboard internal constructor(
    private val sink: ClipSink,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        sink = SystemClipSink(context, context.getSystemService()!!),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    @Volatile
    private var pendingClear: Job? = null

    /**
     * Copy [text] to the clipboard with the sensitive flag set and schedule
     * an auto-clear after [holdForSeconds]. Pass `holdForSeconds = 0` to
     * disable auto-clear (for non-sensitive data such as file paths, if the
     * caller explicitly opts out).
     *
     * [label] is shown in the clipboard UI (e.g. "Password", "SSH key").
     * Prefer a generic label for sensitive content.
     */
    fun copy(label: String, text: String, holdForSeconds: Int = DEFAULT_HOLD_SECONDS) {
        sink.write(label, text, sensitive = true)

        pendingClear?.cancel()
        pendingClear = if (holdForSeconds > 0) {
            scope.launch {
                delay(holdForSeconds.seconds)
                if (sink.current() == text) {
                    sink.clear()
                }
            }
        } else {
            null
        }
    }

    companion object {
        const val DEFAULT_HOLD_SECONDS: Int = 30
    }
}
