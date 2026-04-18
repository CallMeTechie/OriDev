package dev.ori.feature.terminal.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Phase 14 Task 14.5 — pure-flow tests for the resize-debounce /
 * row-floor pipeline used by [TerminalViewModel] to throttle
 * `window-change` SSH packets during emoji-sheet wobble, IME layout
 * switches, and voice-input pop-ins.
 *
 * These tests deliberately exercise the [debouncedResizes] extension
 * directly (rather than spinning up a [TerminalViewModel] with 12
 * mocked collaborators). The three contract invariants checked:
 *
 * 1. Five rapid emissions inside 100 ms collapse to a single final
 *    emission after the 200 ms debounce window.
 * 2. A resize with `rows < 5` is dropped outright — no emission
 *    reaches the collector even after the window closes.
 * 3. The filter runs BEFORE debounce, so a trailing small resize
 *    does not "win" the debounce race and cancel a healthy resize.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResizeDebounceTest {

    @Test
    fun debouncedResizes_fiveRapidEmissionsWithin100ms_emitsOnceAfterWindow() = runTest {
        val input = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 8)
        val collected = mutableListOf<Pair<Int, Int>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            debouncedResizes(input).collect { collected.add(it) }
        }

        // Emit 5 changes, all valid (rows >= 5), each 20 ms apart —
        // total 80 ms ≪ the 200 ms debounce window.
        for (i in 1..FIVE_EMISSIONS) {
            input.emit(RESIZE_COLS to (RESIZE_BASE_ROWS + i))
            advanceTimeBy(EMIT_GAP_MILLIS)
        }

        // Before debounce closes: nothing emitted yet.
        assertThat(collected).isEmpty()

        // After the window: only the last emission wins.
        advanceTimeBy(DEBOUNCE_MILLIS + MARGIN_MILLIS)
        assertThat(collected).hasSize(1)
        assertThat(collected.first()).isEqualTo(RESIZE_COLS to (RESIZE_BASE_ROWS + FIVE_EMISSIONS))

        job.cancel()
    }

    @Test
    fun debouncedResizes_rowCountBelowFloor_dropsEmission() = runTest {
        val input = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 8)
        val collected = mutableListOf<Pair<Int, Int>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            debouncedResizes(input).collect { collected.add(it) }
        }

        // A 2-row resize would clobber any running TUI. The filter
        // must drop it before debounce.
        input.emit(RESIZE_COLS to 2)
        advanceTimeBy(DEBOUNCE_MILLIS * 2)

        assertThat(collected).isEmpty()

        job.cancel()
    }

    @Test
    fun debouncedResizes_rowCountAtFloor_passesThrough() = runTest {
        val input = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 8)
        val collected = mutableListOf<Pair<Int, Int>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            debouncedResizes(input).collect { collected.add(it) }
        }

        // rows == 5 is exactly at the floor and MUST pass.
        input.emit(RESIZE_COLS to MIN_ROWS_FLOOR)
        advanceTimeBy(DEBOUNCE_MILLIS + MARGIN_MILLIS)

        assertThat(collected).hasSize(1)
        assertThat(collected.first()).isEqualTo(RESIZE_COLS to MIN_ROWS_FLOOR)

        job.cancel()
    }

    @Test
    fun debouncedResizes_tinyTailingResize_filteredBeforeDebounceSoHealthyEmissionSurvives() = runTest {
        val input = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 8)
        val collected = mutableListOf<Pair<Int, Int>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            debouncedResizes(input).collect { collected.add(it) }
        }

        // Healthy resize arrives first.
        input.emit(RESIZE_COLS to RESIZE_BASE_ROWS)
        advanceTimeBy(EMIT_GAP_MILLIS)

        // A tiny trailing resize (2 rows) must NOT be allowed to win
        // the debounce race by resetting its timer. The filter runs
        // before debounce so the tiny value is dropped entirely.
        input.emit(RESIZE_COLS to 2)
        advanceTimeBy(DEBOUNCE_MILLIS + MARGIN_MILLIS)

        // Healthy resize should emit exactly once.
        assertThat(collected).hasSize(1)
        assertThat(collected.first()).isEqualTo(RESIZE_COLS to RESIZE_BASE_ROWS)

        job.cancel()
    }

    @Test
    fun debouncedResizes_twoBurstsWithGap_emitsTwice() = runTest {
        val input = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 8)
        val collected = mutableListOf<Pair<Int, Int>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            debouncedResizes(input).collect { collected.add(it) }
        }

        // Burst 1.
        input.emit(RESIZE_COLS to RESIZE_BASE_ROWS)
        advanceTimeBy(DEBOUNCE_MILLIS + MARGIN_MILLIS)

        // Burst 2.
        input.emit(RESIZE_COLS to (RESIZE_BASE_ROWS + 1))
        advanceTimeBy(DEBOUNCE_MILLIS + MARGIN_MILLIS)

        assertThat(collected).containsExactly(
            RESIZE_COLS to RESIZE_BASE_ROWS,
            RESIZE_COLS to (RESIZE_BASE_ROWS + 1),
        ).inOrder()

        job.cancel()
    }

    private companion object {
        const val RESIZE_COLS = 80
        const val RESIZE_BASE_ROWS = 24
        const val FIVE_EMISSIONS = 5
        const val EMIT_GAP_MILLIS = 20L
        const val MARGIN_MILLIS = 10L
    }
}
