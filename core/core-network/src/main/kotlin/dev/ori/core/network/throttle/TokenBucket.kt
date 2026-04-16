package dev.ori.core.network.throttle

import kotlinx.coroutines.delay

class TokenBucket(
    private val capacityBytes: Long,
    private val refillRateBytesPerSecond: Long,
) {
    @Volatile
    private var availableTokens: Long = capacityBytes

    @Volatile
    private var lastRefillTimeMs: Long = System.currentTimeMillis()

    val isUnlimited: Boolean get() = refillRateBytesPerSecond <= 0

    suspend fun consume(bytes: Int): Int {
        if (isUnlimited || bytes <= 0) return bytes
        refill()
        return if (availableTokens >= bytes) {
            availableTokens -= bytes
            bytes
        } else {
            val granted = availableTokens.toInt().coerceAtLeast(0)
            availableTokens = 0
            if (granted == 0) {
                val waitMs = (bytes.toLong() * 1000) / refillRateBytesPerSecond
                delay(waitMs.coerceAtLeast(1))
                refill()
                val afterWait = availableTokens.coerceAtMost(bytes.toLong()).toInt()
                availableTokens -= afterWait
                afterWait
            } else {
                granted
            }
        }
    }

    private fun refill() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefillTimeMs
        if (elapsed <= 0) return
        val tokensToAdd = (elapsed * refillRateBytesPerSecond) / 1000
        availableTokens = (availableTokens + tokensToAdd).coerceAtMost(capacityBytes)
        lastRefillTimeMs = now
    }

    companion object {
        fun fromKbps(kbps: Int?): TokenBucket? {
            if (kbps == null || kbps <= 0) return null
            val bytesPerSecond = kbps.toLong() * 1024
            return TokenBucket(bytesPerSecond, bytesPerSecond)
        }
    }
}
