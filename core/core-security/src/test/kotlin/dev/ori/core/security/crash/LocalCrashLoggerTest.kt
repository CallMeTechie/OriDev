package dev.ori.core.security.crash

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LocalCrashLoggerTest {

    @Test
    fun buildReport_includesHeaderTimestampAndThreadName() {
        val thread = Thread.currentThread()
        val report = LocalCrashLogger.buildReport(thread, IllegalStateException("boom"))

        assertThat(report).contains("=== Ori:Dev Crash Report ===")
        assertThat(report).contains("Timestamp:")
        assertThat(report).contains("Thread:       ${thread.name}")
    }

    @Test
    fun buildReport_includesStackTraceWithCauseMessage() {
        val report = LocalCrashLogger.buildReport(Thread.currentThread(), IllegalStateException("boom-42"))

        assertThat(report).contains("=== Stack trace ===")
        assertThat(report).contains("IllegalStateException")
        assertThat(report).contains("boom-42")
    }

    @Test
    fun buildReport_includesNestedCauseChain() {
        val cause = IllegalArgumentException("inner-cause")
        val outer = RuntimeException("outer-wrapper", cause)

        val report = LocalCrashLogger.buildReport(Thread.currentThread(), outer)

        assertThat(report).contains("outer-wrapper")
        assertThat(report).contains("Caused by:")
        assertThat(report).contains("inner-cause")
    }

    @Test
    fun buildReport_includesLogcatSectionEvenWhenLogcatBinaryUnavailable() {
        // On a JVM unit-test runner (no Android logcat binary on PATH) the
        // ProcessBuilder will throw IOException; the logger must record this
        // inline rather than crashing or omitting the section.
        val report = LocalCrashLogger.buildReport(Thread.currentThread(), Exception("boom"))

        assertThat(report).contains("=== Recent logcat")
        // Either real logcat output landed (CI on real Android device — unlikely)
        // OR the inline error message did. One of them must be present.
        val sectionFromHeader = report.substringAfter("=== Recent logcat (last 200 lines) ===")
        assertThat(sectionFromHeader).isNotEmpty()
    }
}
