package dev.ori.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a baseline profile for Ori:Dev startup + onboarding flow.
 *
 * Run via the `baseline-profile` GitHub Actions workflow (workflow_dispatch).
 * The generated profile is uploaded as an artifact and committed to
 * `app/src/main/baseline-prof.txt` manually.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.ori.dev") {
        pressHome()
        startActivityAndWait()
        // Walk through onboarding (German strings).
        device.wait(Until.findObject(By.text("Loslegen")), TIMEOUT_MS)?.click()
        device.wait(Until.findObject(By.text("Überspringen")), TIMEOUT_MS)?.click()
        device.wait(Until.findObject(By.text("Überspringen")), TIMEOUT_MS)?.click()
        device.wait(Until.findObject(By.text("Starten")), TIMEOUT_MS)?.click()
        device.waitForIdle()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
