package dev.ori.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Tier 3 T3a — custom instrumentation runner that substitutes
 * [HiltTestApplication] for the production `OriDevApplication` so Hilt's
 * `@HiltAndroidRule` can stand up the :app graph during instrumented tests.
 *
 * Wired in `app/build.gradle.kts` via
 * `android.defaultConfig.testInstrumentationRunner = "dev.ori.app.HiltTestRunner"`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
