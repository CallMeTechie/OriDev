package dev.ori.app.service

import kotlin.random.Random

/**
 * Phase 12 P12.2 — exponential backoff with +/- 30 % jitter for the transfer
 * engine's auto-retry logic.
 *
 * Given a retry attempt counter, a base-seconds value from user preferences,
 * and a current timestamp, returns the epoch millis at which the next retry
 * should become eligible. Pure Kotlin — no Android dependencies — so the
 * dispatcher can drive its wakeup logic with a testable input.
 *
 * Formula:
 *   baseMs   = baseSeconds * 2^retryCount * 1000
 *   jitterMs = baseMs * 0.30
 *   jitter   = Random.nextLong(-jitterMs, jitterMs + 1)
 *   result   = nowMillis + baseMs + jitter
 *
 * With defaults baseSeconds=10, maxRetryAttempts=3:
 *   attempt 0 -> 10 s +/- 3 s   (7-13 s)
 *   attempt 1 -> 20 s +/- 6 s   (14-26 s)
 *   attempt 2 -> 40 s +/- 12 s  (28-52 s)
 *   attempt 3 -> no retry, caller marks FAILED
 */
object RetryScheduler {
    private const val JITTER_FRACTION = 0.30

    fun computeNextRetryAt(
        retryCount: Int,
        baseSeconds: Int,
        nowMillis: Long = System.currentTimeMillis(),
        random: Random = Random.Default,
    ): Long {
        require(retryCount >= 0) { "retryCount must be non-negative, was $retryCount" }
        require(baseSeconds > 0) { "baseSeconds must be positive, was $baseSeconds" }
        val baseMs = baseSeconds.toLong() * (1L shl retryCount) * 1000L
        val jitterRange = (baseMs * JITTER_FRACTION).toLong()
        val jitter = if (jitterRange > 0L) random.nextLong(-jitterRange, jitterRange + 1L) else 0L
        return nowMillis + baseMs + jitter
    }
}
