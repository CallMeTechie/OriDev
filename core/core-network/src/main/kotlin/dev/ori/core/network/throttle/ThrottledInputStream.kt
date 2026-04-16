package dev.ori.core.network.throttle

import kotlinx.coroutines.runBlocking
import java.io.FilterInputStream
import java.io.InputStream

class ThrottledInputStream(
    delegate: InputStream,
    private val bucket: TokenBucket,
) : FilterInputStream(delegate) {

    override fun read(): Int {
        runBlocking { bucket.consume(1) }
        return super.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val allowed = runBlocking { bucket.consume(len) }
        if (allowed <= 0) return super.read(b, off, 1)
        return super.read(b, off, allowed)
    }
}
