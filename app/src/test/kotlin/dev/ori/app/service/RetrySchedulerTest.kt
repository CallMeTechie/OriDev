package dev.ori.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Phase 12 P12.2 — unit tests for [RetryScheduler]. Exercises the exponential
 * backoff math with a fixed `nowMillis` baseline and a seeded [Random] where
 * deterministic assertions are required; the jitter-bounds test loops 1000
 * iterations with [Random.Default] to verify the hard +/- 30 % envelope.
 */
class RetrySchedulerTest {

    private val baseline = 1_000_000L
    private val baseSeconds = 10
    private val seeded: Random get() = Random(42)

    @Test
    fun computeNextRetryAt_attempt0_returnsApproxBaseSeconds() {
        val result = RetryScheduler.computeNextRetryAt(
            retryCount = 0,
            baseSeconds = baseSeconds,
            nowMillis = baseline,
            random = seeded,
        )
        val delta = result - baseline
        // attempt 0: base = 10_000 ms, jitter range +/- 3_000 ms
        assertThat(delta).isAtLeast(7_000L)
        assertThat(delta).isAtMost(13_000L)
    }

    @Test
    fun computeNextRetryAt_attempt1_returnsApproxDoubleBase() {
        val result = RetryScheduler.computeNextRetryAt(
            retryCount = 1,
            baseSeconds = baseSeconds,
            nowMillis = baseline,
            random = seeded,
        )
        val delta = result - baseline
        // attempt 1: base = 20_000 ms, jitter range +/- 6_000 ms
        assertThat(delta).isAtLeast(14_000L)
        assertThat(delta).isAtMost(26_000L)
    }

    @Test
    fun computeNextRetryAt_attempt2_returnsApproxFourTimesBase() {
        val result = RetryScheduler.computeNextRetryAt(
            retryCount = 2,
            baseSeconds = baseSeconds,
            nowMillis = baseline,
            random = seeded,
        )
        val delta = result - baseline
        // attempt 2: base = 40_000 ms, jitter range +/- 12_000 ms
        assertThat(delta).isAtLeast(28_000L)
        assertThat(delta).isAtMost(52_000L)
    }

    @Test
    fun computeNextRetryAt_jitter_neverExceedsThirtyPercent() {
        // 1000 iterations over the default Random to make sure the clamp holds.
        val attempt = 2
        val baseMs = baseSeconds.toLong() * (1L shl attempt) * 1000L // 40_000
        val maxJitter = (baseMs * 0.30).toLong() // 12_000
        val lower = baseMs - maxJitter
        val upper = baseMs + maxJitter

        repeat(1_000) {
            val result = RetryScheduler.computeNextRetryAt(
                retryCount = attempt,
                baseSeconds = baseSeconds,
                nowMillis = baseline,
                random = Random.Default,
            )
            val delta = result - baseline
            assertThat(delta).isAtLeast(lower)
            assertThat(delta).isAtMost(upper)
        }
    }

    @Test
    fun computeNextRetryAt_customBaseSeconds_scalesCorrectly() {
        // baseSeconds=5, attempt=2 -> base = 5 * 4 * 1000 = 20_000 ms, jitter +/- 6_000
        val result = RetryScheduler.computeNextRetryAt(
            retryCount = 2,
            baseSeconds = 5,
            nowMillis = baseline,
            random = seeded,
        )
        val delta = result - baseline
        assertThat(delta).isAtLeast(14_000L)
        assertThat(delta).isAtMost(26_000L)
    }
}
