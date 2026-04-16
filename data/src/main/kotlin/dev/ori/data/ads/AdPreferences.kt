package dev.ori.data.ads

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.domain.model.AdSlot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("oridev_ads", Context.MODE_PRIVATE)

    fun lastShownAt(slot: AdSlot): Long =
        prefs.getLong("shown_${slot.name}", 0L)

    fun recordShown(slot: AdSlot) {
        prefs.edit().putLong("shown_${slot.name}", System.currentTimeMillis()).apply()
    }

    fun isDismissed(slot: AdSlot): Boolean =
        prefs.getBoolean("dismissed_${slot.name}", false)

    fun msSinceDismissal(slot: AdSlot): Long {
        val dismissedAt = prefs.getLong("dismissed_at_${slot.name}", 0L)
        return if (dismissedAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - dismissedAt
    }

    fun recordDismissed(slot: AdSlot) {
        prefs.edit()
            .putBoolean("dismissed_${slot.name}", true)
            .putLong("dismissed_at_${slot.name}", System.currentTimeMillis())
            .apply()
    }

    fun clearDismissal(slot: AdSlot) {
        prefs.edit()
            .putBoolean("dismissed_${slot.name}", false)
            .putLong("dismissed_at_${slot.name}", 0L)
            .apply()
    }
}
