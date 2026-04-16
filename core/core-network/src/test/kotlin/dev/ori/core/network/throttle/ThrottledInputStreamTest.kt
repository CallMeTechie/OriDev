package dev.ori.core.network.throttle

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ThrottledInputStreamTest {

    @Test
    fun read_withBucket_returnsData() {
        val data = ByteArray(256) { it.toByte() }
        val bucket = TokenBucket(capacityBytes = 256, refillRateBytesPerSecond = 256)
        val stream = ThrottledInputStream(ByteArrayInputStream(data), bucket)
        val buffer = ByteArray(256)
        val read = stream.read(buffer, 0, 256)
        assertThat(read).isGreaterThan(0)
    }

    @Test
    fun read_unlimitedBucket_passthrough() {
        val data = ByteArray(100) { 42 }
        val bucket = TokenBucket(capacityBytes = 0, refillRateBytesPerSecond = 0)
        val stream = ThrottledInputStream(ByteArrayInputStream(data), bucket)
        val buffer = ByteArray(100)
        val read = stream.read(buffer, 0, 100)
        assertThat(read).isEqualTo(100)
    }

    @Test
    fun close_propagatesToDelegate() {
        val data = ByteArray(10)
        val bucket = TokenBucket(capacityBytes = 100, refillRateBytesPerSecond = 100)
        val stream = ThrottledInputStream(ByteArrayInputStream(data), bucket)
        stream.close()
    }
}
