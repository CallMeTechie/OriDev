package dev.ori.core.common.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over [ProcessBuilder] to allow injecting fake processes in tests.
 *
 * Implementations must be safe to call from any thread; callers are not required
 * to switch dispatchers.
 */
interface ProcessRunner {
    /**
     * Runs a process and returns its combined stdout/stderr output.
     *
     * @return the process output on success, or `null` on timeout, non-zero
     *         exit code, or any exception (e.g. missing binary).
     */
    suspend fun run(
        command: List<String>,
        workingDir: String? = null,
        timeoutSeconds: Long = 5,
    ): String?
}

@Singleton
class DefaultProcessRunner @Inject constructor() : ProcessRunner {
    override suspend fun run(
        command: List<String>,
        workingDir: String?,
        timeoutSeconds: Long,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val builder = ProcessBuilder(command)
            workingDir?.let { builder.directory(File(it)) }
            builder.redirectErrorStream(true)
            val process = builder.start()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                process.inputStream.bufferedReader().readText()
            }
        } catch (_: Exception) {
            null
        }
    }
}
