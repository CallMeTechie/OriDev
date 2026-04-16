package dev.ori.core.network.throttle

import kotlinx.coroutines.runBlocking
import java.io.FilterOutputStream
import java.io.OutputStream

class ThrottledOutputStream(
    delegate: OutputStream,
    private val bucket: TokenBucket,
) : FilterOutputStream(delegate) {

    override fun write(b: Int) {
        runBlocking { bucket.consume(1) }
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var written = 0
        while (written < len) {
            val chunk = runBlocking { bucket.consume(len - written) }
            if (chunk > 0) {
                out.write(b, off + written, chunk)
                written += chunk
            }
        }
    }
}
