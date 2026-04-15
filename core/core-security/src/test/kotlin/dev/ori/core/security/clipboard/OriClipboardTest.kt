package dev.ori.core.security.clipboard

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for [OriClipboard]. We drive the helper through its
 * internal [ClipSink] seam so we never touch the Android framework
 * static `ClipData.newPlainText`, which cannot be called from plain JVM
 * tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OriClipboardTest {

    private class FakeClipSink : ClipSink {
        data class Write(val label: String, val text: String, val sensitive: Boolean)

        val writes = mutableListOf<Write>()
        var clearCount = 0
        var currentValue: String? = null

        override fun write(label: String, text: String, sensitive: Boolean) {
            writes += Write(label, text, sensitive)
            currentValue = text
        }

        override fun current(): String? = currentValue

        override fun clear() {
            clearCount++
            currentValue = null
        }
    }

    @Test
    fun copy_writesLabelAndTextWithSensitiveFlag() = runTest {
        val sink = FakeClipSink()
        val helper = OriClipboard(sink, backgroundScope)

        helper.copy("Password", "hunter2", holdForSeconds = 0)

        assertThat(sink.writes).hasSize(1)
        assertThat(sink.writes.single().label).isEqualTo("Password")
        assertThat(sink.writes.single().text).isEqualTo("hunter2")
        assertThat(sink.writes.single().sensitive).isTrue()
    }

    @Test
    fun copy_withZeroHold_doesNotScheduleClear() = runTest {
        val sink = FakeClipSink()
        val helper = OriClipboard(sink, backgroundScope)

        helper.copy("Path", "/etc/passwd", holdForSeconds = 0)
        advanceTimeBy(60_000)
        advanceUntilIdle()

        assertThat(sink.clearCount).isEqualTo(0)
        assertThat(sink.currentValue).isEqualTo("/etc/passwd")
    }

    @Test
    fun copy_afterHoldElapses_clearsWhenStillOurText() = runTest {
        val sink = FakeClipSink()
        val helper = OriClipboard(sink, backgroundScope)

        helper.copy("Password", "hunter2", holdForSeconds = 30)

        advanceTimeBy(29_000)
        advanceUntilIdle()
        assertThat(sink.clearCount).isEqualTo(0)

        advanceTimeBy(2_000)
        advanceUntilIdle()
        assertThat(sink.clearCount).isEqualTo(1)
        assertThat(sink.currentValue).isNull()
    }

    @Test
    fun copy_afterHold_doesNotClearWhenUserOverwroteClipboard() = runTest {
        val sink = FakeClipSink()
        val helper = OriClipboard(sink, backgroundScope)

        helper.copy("Password", "hunter2", holdForSeconds = 30)
        // Simulate user (or another app) copying something else.
        sink.currentValue = "something else the user copied"

        advanceTimeBy(31_000)
        advanceUntilIdle()

        assertThat(sink.clearCount).isEqualTo(0)
        assertThat(sink.currentValue).isEqualTo("something else the user copied")
    }

    @Test
    fun copy_secondCall_cancelsPreviousClearTimer() = runTest {
        val sink = FakeClipSink()
        val helper = OriClipboard(sink, backgroundScope)

        helper.copy("Password", "first", holdForSeconds = 30)
        advanceTimeBy(20_000)
        helper.copy("Password", "second", holdForSeconds = 30)

        // 20s elapsed since second copy (would have fired the first timer).
        advanceTimeBy(20_000)
        advanceUntilIdle()
        assertThat(sink.clearCount).isEqualTo(0)
        assertThat(sink.currentValue).isEqualTo("second")

        // 30s total since the second copy -> its timer fires.
        advanceTimeBy(11_000)
        advanceUntilIdle()
        assertThat(sink.clearCount).isEqualTo(1)
    }

    @Test
    fun defaultHoldSeconds_is30_matchingAppPreferences() {
        assertThat(OriClipboard.DEFAULT_HOLD_SECONDS).isEqualTo(30)
    }
}
