package dev.ori.core.security.crash

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NonFatalErrorLoggerTest {

    @Test
    fun buildReport_includesCategoryAndTimestamp() {
        val report = NonFatalErrorLogger.buildReport(
            category = "connect-ssh",
            throwable = IllegalStateException("boom"),
            contextNote = null,
        )
        assertThat(report).contains("=== Ori:Dev Non-Fatal Error Report ===")
        assertThat(report).contains("Category:     connect-ssh")
        assertThat(report).contains("Timestamp:")
    }

    @Test
    fun buildReport_includesStackTraceAndMessage() {
        val report = NonFatalErrorLogger.buildReport(
            category = "connect-ssh",
            throwable = RuntimeException("auth failed"),
            contextNote = null,
        )
        assertThat(report).contains("=== Stack trace ===")
        assertThat(report).contains("RuntimeException")
        assertThat(report).contains("auth failed")
    }

    @Test
    fun buildReport_includesContextNoteWhenProvided() {
        val report = NonFatalErrorLogger.buildReport(
            category = "connect-ssh",
            throwable = Exception("boom"),
            contextNote = "host=192.168.1.10, port=22",
        )
        assertThat(report).contains("Context:      host=192.168.1.10, port=22")
    }

    @Test
    fun buildReport_omitsContextSectionWhenNoteIsNullOrBlank() {
        val report = NonFatalErrorLogger.buildReport(
            category = "connect-ssh",
            throwable = Exception("boom"),
            contextNote = "   ",
        )
        assertThat(report).doesNotContain("Context:")
    }

    @Test
    fun buildReport_includesLogcatSectionHeaderEvenOnFailure() {
        // On a JVM unit-test runner the ProcessBuilder will fail — section
        // header must still appear so the file is not silently truncated.
        val report = NonFatalErrorLogger.buildReport("connect-ssh", Exception("boom"), null)
        assertThat(report).contains("=== Recent logcat")
    }
}
