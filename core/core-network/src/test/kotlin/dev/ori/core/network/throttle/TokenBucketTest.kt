package dev.ori.core.network.throttle

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TokenBucketTest {

    @Test
    fun consume_exactCapacity_drains() = runTest {
        val bucket = TokenBucket(capacityBytes = 1024, refillRateBytesPerSecond = 1024)
        val consumed = bucket.consume(1024)
        assertThat(consumed).isEqualTo(1024)
    }

    @Test
    fun consume_overCapacity_returnsPartial() = runTest {
        val bucket = TokenBucket(capacityBytes = 512, refillRateBytesPerSecond = 512)
        val consumed = bucket.consume(1024)
        assertThat(consumed).isAtMost(512)
    }

    @Test
    fun consume_zeroBandwidth_isUnlimited() = runTest {
        val bucket = TokenBucket(capacityBytes = 0, refillRateBytesPerSecond = 0)
        assertThat(bucket.isUnlimited).isTrue()
        val consumed = bucket.consume(4096)
        assertThat(consumed).isEqualTo(4096)
    }

    @Test
    fun fromKbps_null_returnsNull() {
        assertThat(TokenBucket.fromKbps(null)).isNull()
        assertThat(TokenBucket.fromKbps(0)).isNull()
    }
}
