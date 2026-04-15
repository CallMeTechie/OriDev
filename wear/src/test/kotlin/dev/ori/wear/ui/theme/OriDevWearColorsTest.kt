package dev.ori.wear.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 11 / P3.1 — verifies the Wear OS palette and Wear Material3 [androidx.wear.compose.material3.ColorScheme]
 * factory both honour the exact hex tokens documented in `Mockups/watch.html`.
 *
 * These tests are pure JVM (no Robolectric) — `androidx.compose.ui.graphics.Color`
 * is a value class wrapping a `Long`, and Wear M3 `ColorScheme` is a JVM data
 * holder, so equality + slot reads work without an Android runtime.
 */
class OriDevWearColorsTest {

    @Test
    fun background_matchesMockupHex() {
        assertThat(OriDevWearColors.Background).isEqualTo(Color(0xFF0F0F0F))
    }

    @Test
    fun primary_matchesOriDevIndigo500() {
        assertThat(OriDevWearColors.Primary).isEqualTo(Color(0xFF6366F1))
    }

    @Test
    fun success_matchesStatusConnected() {
        assertThat(OriDevWearColors.Success).isEqualTo(Color(0xFF10B981))
    }

    @Test
    fun error_matchesStatusDisconnected() {
        assertThat(OriDevWearColors.Error).isEqualTo(Color(0xFFEF4444))
    }

    @Test
    fun onSurfaceVariant_matchesMockupMutedGray() {
        assertThat(OriDevWearColors.OnSurfaceVariant).isEqualTo(Color(0xFF888888))
    }

    @Test
    fun colorScheme_populatesCoreSlotsFromWearColors() {
        val scheme = oriDevWearColorScheme()
        assertThat(scheme.background).isEqualTo(OriDevWearColors.Background)
        assertThat(scheme.onBackground).isEqualTo(OriDevWearColors.OnBackground)
        assertThat(scheme.primary).isEqualTo(OriDevWearColors.Primary)
        assertThat(scheme.onPrimary).isEqualTo(OriDevWearColors.OnPrimary)
        assertThat(scheme.error).isEqualTo(OriDevWearColors.Error)
        assertThat(scheme.onError).isEqualTo(OriDevWearColors.OnError)
        assertThat(scheme.outlineVariant).isEqualTo(OriDevWearColors.OutlineVariant)
        assertThat(scheme.onSurfaceVariant).isEqualTo(OriDevWearColors.OnSurfaceVariant)
    }
}
