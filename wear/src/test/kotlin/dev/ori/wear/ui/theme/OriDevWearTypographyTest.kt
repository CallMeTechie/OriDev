package dev.ori.wear.ui.theme

import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import dev.ori.core.fonts.OriFonts
import org.junit.jupiter.api.Test

/**
 * Phase 11 / P3.1 — verifies the Wear M3 [androidx.wear.compose.material3.Typography]
 * factory wires every relevant slot to [OriFonts.RobotoFlex] and that `labelSmall`
 * matches the 9 sp / 1 sp tracking / SemiBold spec from `Mockups/watch.html`.
 *
 * Pure JVM — `TextStyle` field reads do not require Android runtime.
 */
class OriDevWearTypographyTest {

    @Test
    fun oriDevWearTypography_usesRobotoFlex() {
        val typo = oriDevWearTypography()
        assertThat(typo.displayLarge.fontFamily).isEqualTo(OriFonts.RobotoFlex)
        assertThat(typo.titleMedium.fontFamily).isEqualTo(OriFonts.RobotoFlex)
        assertThat(typo.labelSmall.fontFamily).isEqualTo(OriFonts.RobotoFlex)
        assertThat(typo.bodyMedium.fontFamily).isEqualTo(OriFonts.RobotoFlex)
    }

    @Test
    fun labelSmall_isTightenedFor9spUppercase() {
        val typo = oriDevWearTypography()
        assertThat(typo.labelSmall.fontSize.value).isEqualTo(9f)
        assertThat(typo.labelSmall.letterSpacing.value).isEqualTo(1f)
        assertThat(typo.labelSmall.fontWeight).isEqualTo(FontWeight.SemiBold)
    }
}
