package dev.ori.core.security.crash

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash sink that writes a plain-text report to the device
 * Downloads folder (via the Scoped-Storage `MediaStore.Downloads` API,
 * no `WRITE_EXTERNAL_STORAGE` permission required on minSdk 34).
 *
 * Designed for the homelab usage pattern where the user has no PC + adb
 * handy at the moment of crash. After a crash they can open the system
 * Files app, find `oridev-crash-{timestamp}.txt` in Downloads, and share
 * it from there.
 *
 * The handler **chains** to whatever `Thread.getDefaultUncaughtException
 * Handler()` returns at install time so the existing ACRA handler still
 * runs (and the process still terminates as Android expects). Install
 * order in [dev.ori.app.OriDevApplication.attachBaseContext]:
 *
 * 1. `super.attachBaseContext(base)`
 * 2. `AcraConfig.initIfEnabled(this)` — installs ACRA's handler
 * 3. `LocalCrashLogger.install(applicationContext)` — captures ACRA's
 *    handler as the chain target, installs ours on top. So our handler
 *    fires first (writes the file), then ACRA fires, then the OS kills
 *    the process.
 *
 * The handler itself is wrapped in try/catch so the logger can never
 * itself become the reason a crash report is lost.
 */
public object LocalCrashLogger {

    private const val TAG = "LocalCrashLogger"
    private const val FILE_PREFIX = "oridev-crash-"
    private const val FILE_EXTENSION = ".txt"
    private const val MIME_TEXT_PLAIN = "text/plain"
    private const val LOGCAT_TAIL_LINES = "200"

    /**
     * Installs the global uncaught-exception handler. Idempotent — calling
     * twice still chains the previous handler exactly once.
     */
    public fun install(context: Context) {
        // applicationContext may still be null if install() is called before
        // Application.attachBaseContext has finished (which is the whole
        // point — we want to catch crashes that happen IN that call).
        // Fall back to the raw context in that case; the ContentResolver
        // is available on a base ContextWrapper too.
        val appContext = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            @Suppress("TooGenericExceptionCaught")
            try {
                writeCrashLog(appContext, thread, throwable)
            } catch (loggerCrash: Throwable) {
                // Never let the logger itself swallow a real crash. Best-effort
                // log to logcat; the chain handler will still run.
                // Catching Throwable here is intentional — anything from
                // OutOfMemoryError to a corrupt MediaStore would otherwise
                // mask the original crash the user is trying to capture.
                Log.e(TAG, "LocalCrashLogger failed to write report", loggerCrash)
            }
            // Always chain — ACRA + the platform default handler still need
            // to run so the process terminates and a system crash dialog
            // appears.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val fileName = "$FILE_PREFIX$timestamp$FILE_EXTENSION"
        val report = buildReport(thread, throwable)

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TEXT_PLAIN)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore returned null URI for $fileName")
        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(report.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("Could not open output stream for $uri")
    }

    /**
     * Pure function: builds the human-readable crash report. Extracted so
     * tests can assert on its content without driving the MediaStore IO
     * path (which needs Robolectric).
     */
    internal fun buildReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("=== Ori:Dev Crash Report ===")
        pw.println("Timestamp:    ${Date()}")
        pw.println("Thread:       ${thread.name}")
        pw.println("Manufacturer: ${Build.MANUFACTURER}")
        pw.println("Model:        ${Build.MODEL}")
        pw.println("Android:      ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        pw.println("Fingerprint:  ${Build.FINGERPRINT}")
        pw.println()
        pw.println("=== Stack trace ===")
        throwable.printStackTrace(pw)
        pw.println()
        pw.println("=== Recent logcat (last $LOGCAT_TAIL_LINES lines) ===")
        appendLogcatTail(pw)
        return sw.toString()
    }

    /**
     * Captures the last [LOGCAT_TAIL_LINES] lines of logcat via
     * [ProcessBuilder] (args-array form, never a shell string — no
     * injection surface even though all args here are hardcoded).
     * Failures are recorded inline rather than thrown so the rest of
     * the report still ends up on disk.
     */
    private fun appendLogcatTail(pw: PrintWriter) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val process = ProcessBuilder("logcat", "-d", "-t", LOGCAT_TAIL_LINES)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.copyTo(pw)
            }
        } catch (logcatError: Throwable) {
            // Catching Throwable: ProcessBuilder can raise IOException,
            // SecurityException, or — on JVM unit-test runners — anything
            // up to NoClassDefFoundError. We must record the failure
            // inline rather than abort the whole report.
            pw.println("(could not capture logcat: ${logcatError.javaClass.simpleName}: ${logcatError.message})")
        }
    }
}
