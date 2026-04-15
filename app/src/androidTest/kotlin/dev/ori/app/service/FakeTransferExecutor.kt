package dev.ori.app.service

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 3 T3a — controllable [TransferExecutor] used by
 * [TransferEngineServiceTest]. Emits [progressTicks] progress callbacks
 * spaced [tickDelayMs] apart, then either returns normally or throws per
 * [failWith] / [cancelOnTick].
 *
 * Every knob is `@Volatile` so tests can flip behaviour between phases
 * without recreating the Hilt singleton. The fake also remembers how many
 * times each entry point was called so tests can assert on invocation
 * counts when helpful.
 */
@Singleton
internal class FakeTransferExecutor @Inject constructor() : TransferExecutor {

    @Volatile var progressTicks: Int = DEFAULT_TICKS

    @Volatile var tickDelayMs: Long = DEFAULT_TICK_DELAY_MS

    @Volatile var totalBytes: Long = DEFAULT_TOTAL_BYTES

    /** When non-null, [upload]/[download] throw this instead of completing. */
    @Volatile var failWith: Throwable? = null

    /**
     * When `remoteFileSize` is called by the worker's overwrite-policy
     * check, return this value. `null` (the default) means "destination
     * does not exist" so the worker skips the conflict branch entirely,
     * which is what we want for the happy-path lifecycle test.
     */
    @Volatile var remoteSize: Long? = null

    @Volatile var uploadCalls: Int = 0
        private set

    @Volatile var downloadCalls: Int = 0
        private set

    @Volatile var remoteFileSizeCalls: Int = 0
        private set

    override suspend fun upload(
        sessionId: String,
        localPath: String,
        remotePath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        uploadCalls += 1
        emitProgress(onProgress)
    }

    override suspend fun download(
        sessionId: String,
        remotePath: String,
        localPath: String,
        offsetBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        downloadCalls += 1
        emitProgress(onProgress)
    }

    override suspend fun remoteFileSize(sessionId: String, remotePath: String): Long? {
        remoteFileSizeCalls += 1
        return remoteSize
    }

    private suspend fun emitProgress(onProgress: suspend (Long, Long) -> Unit) {
        val ticks = progressTicks.coerceAtLeast(1)
        val total = totalBytes.coerceAtLeast(1L)
        for (i in 1..ticks) {
            // Bail out early if a test flipped failWith mid-flight.
            failWith?.let { throw it }
            val transferred = (total * i) / ticks
            onProgress(transferred, total)
            if (tickDelayMs > 0L) delay(tickDelayMs)
        }
        failWith?.let { throw it }
    }

    companion object {
        const val DEFAULT_TICKS: Int = 5
        const val DEFAULT_TICK_DELAY_MS: Long = 50L
        const val DEFAULT_TOTAL_BYTES: Long = 1_024L
    }
}
