package dev.ori.app.crash

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.acra.ACRA
import org.junit.jupiter.api.Test

/**
 * Verifies AcraConfig's no-op guards. We cannot exercise the real
 * `initAcra` path from a JVM unit test (needs an Android Application
 * instance), so this test only confirms the guards short-circuit cleanly
 * when the debug or placeholder-URL conditions hold.
 */
class AcraConfigTest {

    @Test
    fun initIfEnabled_inDebugBuild_doesNotInitialiseAcra() {
        // Test variants are debug builds, so the BuildConfig.DEBUG guard fires
        // before any Application interaction. The mock should never be touched.
        val application = mockk<android.app.Application>(relaxed = true)

        AcraConfig.initIfEnabled(application)

        assertThat(ACRA.isInitialised).isFalse()
    }
}
